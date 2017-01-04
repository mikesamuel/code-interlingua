package com.mikesamuel.cil.ast.passes;

import java.util.logging.Logger;

import org.junit.Test;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.mikesamuel.cil.ast.BaseNode;
import com.mikesamuel.cil.ast.CompilationUnitNode;
import com.mikesamuel.cil.ast.ExpressionNode;
import com.mikesamuel.cil.ast.meta.StaticType.TypePool;
import com.mikesamuel.cil.ast.meta.TypeInfoResolver;
import com.mikesamuel.cil.ast.passes.PassTestHelpers.LoggableOperation;
import com.mikesamuel.cil.ast.traits.Typed;
import com.mikesamuel.cil.parser.Unparse.UnparseVerificationException;

import junit.framework.TestCase;

@SuppressWarnings("javadoc")
public final class TypingPassTest extends TestCase {

  private static void assertTyped(
      String[][] inputs,
      Class<? extends Typed> target,
      String[] expectedTypes,
      String... expectedErrors)
  throws UnparseVerificationException {
    ImmutableList<CompilationUnitNode> processedCus =
        PassTestHelpers.expectErrors(
            new LoggableOperation<ImmutableList<CompilationUnitNode>>() {

              @Override
              public ImmutableList<CompilationUnitNode> run(Logger logger) {
                ImmutableList<CompilationUnitNode> cus =
                    PassTestHelpers.parseCompilationUnits(inputs);
                DeclarationPass dp = new DeclarationPass(logger);
                TypeInfoResolver typeInfoResolver = dp.run(cus);
                DisambiguationPass disambigPass = new DisambiguationPass(
                    typeInfoResolver, logger, false);
                cus = disambigPass.run(cus);
                TypePool typePool = new TypePool(typeInfoResolver);
                ClassMemberPass classMemberPass = new ClassMemberPass(
                    logger, typePool);
                classMemberPass.run(cus);
                TypingPass tp = new TypingPass(logger, typePool, true);
                tp.run(cus);
                return cus;
              }

            },
            expectedErrors);

    ImmutableList.Builder<String> got = ImmutableList.builder();
    for (CompilationUnitNode cu : processedCus) {
      for (Typed t : cu.finder(target).find()) {
        got.add(
            PassTestHelpers.serializeNodes(ImmutableList.of((BaseNode) t), null)
            + " : " + t.getStaticType());
      }
    }

    assertEquals(
        Joiner.on("\n\n").join(expectedTypes),
        Joiner.on("\n\n").join(got.build()));
  }


  @Test
  public static final void testLiterals() throws Exception {
    assertTyped(
        new String[][] {
          {
            "//C",
            "class C {",
            "  int x = 3;",
            "  long l = -42L;",
            "  float f = 3.f;",
            "  double d = 3.;",
            "  char c = 'c';",
            "  String s = \"\";",
            "  Object o = null;",
            "}",
          }
        },
        ExpressionNode.class,
        new String[] {
            "3 : int",
            "-42L : long",
            "3.f : float",
            "3. : double",
            "'c' : char",
            "\"\" : /java/lang/String",
            "null : <null>",
        });

  }

  @Test
  public static final void testSimpleUnboxing() throws Exception {
    assertTyped(
        new String[][] {
          {
            "//C",
            "class C {",
            "  long i = +Integer.valueOf(\"4\");",
            "}",
          }
        },
        ExpressionNode.class,
        new String[] {
            // TODO: need to cast to long to satisfy initialized type
            "+Integer.valueOf(\"4\") : int",
            "\"4\" : /java/lang/String",
        });
  }
}
