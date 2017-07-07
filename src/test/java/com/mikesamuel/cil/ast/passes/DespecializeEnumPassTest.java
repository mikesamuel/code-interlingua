package com.mikesamuel.cil.ast.passes;

import java.util.logging.Logger;

import org.junit.Test;

import com.google.common.collect.ImmutableList;
import com.mikesamuel.cil.ast.NodeI;
import com.mikesamuel.cil.ast.Trees.Decorator;
import com.mikesamuel.cil.ast.j8.J8FileNode;
import com.mikesamuel.cil.ast.j8.J8MethodDescriptorReference;
import com.mikesamuel.cil.ast.j8.Java8Comments;
import com.mikesamuel.cil.ast.meta.MethodDescriptor;
import com.mikesamuel.cil.ast.meta.TypeSpecification;
import com.mikesamuel.cil.ast.passes.PassTestHelpers.PassRunner;
import com.mikesamuel.cil.parser.Unparse.UnparseVerificationException;

import junit.framework.TestCase;

@SuppressWarnings("javadoc")
public final class DespecializeEnumPassTest extends TestCase {

  private static void assertPassOutput(
      String[][] expected,
      String[][] inputs,
      Decorator decorator,
      String... expectedErrors)
  throws UnparseVerificationException {
    PassTestHelpers.assertAnnotatedOutput(
        new PassRunner() {

          @Override
          public ImmutableList<J8FileNode> runPasses(
              Logger logger, ImmutableList<J8FileNode> files) {

            CommonPassRunner firstPass = new CommonPassRunner(logger);
            ImmutableList<J8FileNode> firstPassResult = firstPass.run(files);

            DespecializeEnumPass dsp = new DespecializeEnumPass(
                logger, firstPass.getMemberInfoPool(),
                firstPass.getMethodVariantPool());
            ImmutableList<J8FileNode> rewritten = dsp.run(firstPassResult);

            CommonPassRunner secondPass = new CommonPassRunner(logger);
            return secondPass.run(rewritten);
          }

        },
        expected,
        inputs,
        decorator,
        expectedErrors);
  }

  @Test
  public static void testSimpleEnum() throws Exception {
    assertPassOutput(
        new String[][] {
          {
            "enum E { A(), B(), C(),; }",
          },
        },
        new String[][] {
          {
            "enum E {",
            "  A, B, C;",
            "}",
          },
        },
        null);
  }

  @Test
  public static void testTrivialSpecialization() throws Exception {
    assertPassOutput(
        new String[][] {
          {
            "enum E { A(), B(), C(),; }",
          },
        },
        new String[][] {
          {
            "enum E {",
            "  A {}, B, C;",
            "}",
          },
        },
        null);
  }

  @Test
  public static void testOverriddenMethod() throws Exception {
    assertPassOutput(
        new String[][] {
          {
            "enum E {",
            "  A(), B(), C(),;",

            "  static private String B__toString() { return\"B!\"; }",
            "  @java.lang.Override public java.lang.String toString() {",
            "    switch (this) {",
            "      case B :"
            +      " return",
                  "      /* /E.()Ljava/lang/String;*/",
                  "      B__toString();",
            "    }",
            "    return super.",
            "    /* /E.()Ljava/lang/String;*/",
            "    toString();",
            "  }",

            "}",
          },
          {
            "class C {",
            "  static{"
            +  " E e = E.A;"
            // Not /java/lang/Enum</E>.  It should be /E since it is now
            // overridden.
            +  " e./* /E.()Ljava/lang/String;*/toString(); "
            + "}",
            "  public C() {}",
            "}",
          },
        },
        new String[][] {
          {
            "enum E {",
            "  A,",
            "  B {",
            "    @Override public String toString() { return \"B!\"; }",
            "  },",
            "  C,",
            "}",
          },
          {
            "class C {",
            "  static {",
            "    E e = E.A;",
            "    e.toString();",
            "  }",
            "}",
          },
        },
        METHOD_DESCRIPTOR_DECORATOR
        );
  }

  private static final Decorator METHOD_DESCRIPTOR_DECORATOR =
      new Decorator() {

        @Override
        public String decorate(NodeI<?, ?, ?> node) {
          if (node instanceof J8MethodDescriptorReference) {
            J8MethodDescriptorReference ref =
                (J8MethodDescriptorReference) node;
            TypeSpecification callee = ref.getMethodDeclaringType();
            MethodDescriptor md = ref.getMethodDescriptor();
            if (callee != null || md != null) {
              return Java8Comments.blockCommentMinimalSpace(
                  String.valueOf(callee) + "." + String.valueOf(md));
            }
          }
          return null;
        }

      };


}
