package com.mikesamuel.cil.ast.passes;

import java.util.logging.Logger;

import javax.annotation.Nullable;

import org.junit.Test;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.mikesamuel.cil.ast.BaseNode;
import com.mikesamuel.cil.ast.CompilationUnitNode;
import com.mikesamuel.cil.ast.ExpressionNode;
import com.mikesamuel.cil.ast.Java8Comments;
import com.mikesamuel.cil.ast.MethodNameNode;
import com.mikesamuel.cil.ast.Trees.Decorator;
import com.mikesamuel.cil.ast.meta.StaticType.TypePool;
import com.mikesamuel.cil.ast.meta.TypeInfoResolver;
import com.mikesamuel.cil.ast.passes.PassTestHelpers.LoggableOperation;
import com.mikesamuel.cil.ast.traits.MethodDescriptorReference;
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
    assertTyped(null, inputs, target, expectedTypes, null, expectedErrors);
  }

  private static void assertTyped(
      @Nullable String[][] expectedWithCasts,
      String[][] inputs,
      Class<? extends Typed> target,
      String[] expectedTypes,
      @Nullable Decorator decorator,
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
                return tp.run(cus);
              }

            },
            expectedErrors);


    if (expectedWithCasts != null) {
      String got = PassTestHelpers.serializeNodes(processedCus, decorator);
      String want = decorator == null
          ? PassTestHelpers.normalizeCompilationUnitSource(expectedWithCasts)
          : PassTestHelpers.joinExpectedLines(expectedWithCasts);
      assertEquals(want, got);
    }

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
            "class C {",
            "  long i = (long) (+(int) Integer.valueOf(\"4\"));",
            "}",
          },
        },
        new String[][] {
          {
            "//C",
            "class C {",
            "  long i = +Integer.valueOf(\"4\");",
            "}",
          },
        },
        ExpressionNode.class,
        new String[] {
            // TODO: need to cast to long to satisfy initialized type
            "(long) (+(int) Integer.valueOf(\"4\")) : long",
            "+(int) Integer.valueOf(\"4\") : int",
            "\"4\" : /java/lang/String",
        },
        null);
  }

  @Test
  public static final void testMethodDispatchDoesntBoxOrUnboxUnnecessarily()
  throws Exception {
    assertTyped(
        new String[][] {
          {
            "class C {",
            "  void f(int i) {}",
            "  String f(Integer i) { return null; }",
            "  {",
            "    this./*(I)V*/f(42);",
            "    this./*(Ljava/lang/Integer;)Ljava/lang/String;*/f(null);",
            "    String s = this./*(Ljava/lang/Integer;)Ljava/lang/String;*/f(Integer",
            "        ./*(I)Ljava/lang/Integer;*/valueOf(42));",
            "  }",
            "}",
          },
        },
        new String[][] {
          {
            "//C",
            "class C {",
            "  void f(int i) {}",
            "  String f(Integer i) { return null; }",
            "  {",
            "    this.f(42);",
            "    this.f(null);",
            "    String s = this.f(Integer.valueOf(42));",
            "  }",
            "}",
          },
        },
        ExpressionNode.class,
        new String[] {
            "null : <null>",  // returned from f(Integer).
            "42 : int",  // actual to first call.
            "null : <null>",  // actual to second call.
            // third call is used as an expression not an expression statement.
            "this.f(Integer.valueOf(42)) : /java/lang/String",
            // actual to third call.
            "Integer.valueOf(42) : /java/lang/Integer",
            // actual to embedded call.
            "42 : int",
        },
        DECORATE_METHOD_NAMES);

    if (false)
    assertTyped(
        new String[][] {
          {
            "class C {",
            "  void f(int i) {}",
            "  String f(Integer i) { return null; }",
            "  {",
            "    /*(I)V*/f(42);",
            "    /*(Ljava/lang/Integer;)Ljava/lang/String;*/f(null);",
            "    String s = /*(Ljava/lang/Integer;)Ljava/lang/String;*/f(Integer",
            "        ./*(I)Ljava/lang/Integer;*/valueOf(42));",
            "  }",
            "}",
          },
        },
        new String[][] {
          {
            "//C",
            "class C {",
            "  void f(int i) {}",
            "  String f(Integer i) { return null; }",
            "  {",
            "    f(42);",
            "    f(null);",
            "    String s = f(Integer.valueOf(42));",
            "  }",
            "}",
          },
        },
        ExpressionNode.class,
        new String[] {
            "null : <null>",  // returned from f(Integer).
            "42 : int",  // actual to first call.
            "null : <null>",  // actual to second call.
            // third call is used as an expression not an expression statement.
            "this.f(Integer.valueOf(42)) : /java/lang/String",
            // actual to third call.
            "Integer.valueOf(42) : /java/lang/Integer",
            // actual to embedded call.
            "42 : int",
        },
        DECORATE_METHOD_NAMES);
  }

  private static final Decorator DECORATE_METHOD_NAMES = new Decorator() {

    @Override
    public String decorate(BaseNode node) {
      if (node instanceof MethodDescriptorReference) {
        String descriptor = ((MethodDescriptorReference) node)
            .getMethodDescriptor();
        if (descriptor != null) {
          return Java8Comments.blockCommentMinimalSpace(descriptor);
        }
      }
      return null;
    }

  };
}
