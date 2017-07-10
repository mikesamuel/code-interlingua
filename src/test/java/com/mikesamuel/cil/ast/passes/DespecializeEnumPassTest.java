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
            +      " return E.",
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

  @Test
  public static void testFieldAccessesRewritten() throws Exception {
    // Fields declared within a specialized enum are only accessible
    // (modulo abuses of reflection) to code within that specialized enum's
    // class body.  Test that we rewrite field accesses in that scope.

    String[] waysToReferenceEdotBdotx = new String[] {
      "x",
      "this.x",
      // "B.x",        // Not allowed since the static type of B is E, not E$1
      // "E.B.x",      // ditto
      // "foo.E.B.x",  // ditto
    };

    for (String refToX : waysToReferenceEdotBdotx) {
      String[] input = {
        "//E:%(ref_to_x)s",
        "package foo;",
        "enum E {",
        "  A,",
        "  B() {",
        "    int x;",
        "    @Override public String toString() {",
        "      return \"B\" + %(ref_to_x)s;",
        "    }",
        "  },",
        "  C,",
        "}",
      };

      for (int i = 0, n = input.length; i < n; ++i) {
        input[i] = input[i].replace("%(ref_to_x)s", refToX);
      }

      assertPassOutput(
          new String[][] {
            {
              "package foo;",
              "enum E {",
              "  A,",
              "  B,",
              "  C,",
              "  ;",
              "  private static int B__x;",
              "  static private String B__toString() { return \"B\" + B__x; }",
              "  @java.lang.Override public java.lang.String toString() {",
              "    switch (this) {",
              "      case B:",
              "        return foo.E.B__toString();",
              "    }",
              "    return super.toString();",
              "  }",
              "}",
            },
          },
          new String[][] { input },
          null
          );
    }
  }

  @Test
  public static void testMethodCallsRewritten() throws Exception {
    // Fields declared within a specialized enum are only accessible
    // (modulo abuses of reflection) to code within that specialized enum's
    // class body.  Test that we rewrite field accesses in that scope.

    String[] waysToReferenceEdotBdotx = new String[] {
      "f()", "B__f()",
      "this.f()", "foo.E.B__f()",
      // "B.f()",      // Not allowed since the static type of B is E, not E$1
      // "E.B.f()",    // ditto
      // "foo.E.B.f()",// ditto
    };

    for (int j = 0, m = waysToReferenceEdotBdotx.length; j < m; j += 2) {
      String refToF = waysToReferenceEdotBdotx[j];
      String refToBF = waysToReferenceEdotBdotx[j + 1];
      String[] input = {
        "//E:%(ref_to_f)s",
        "package foo;",
        "enum E {",
        "  A,",
        "  B() {",
        "    String f() { return \".f\"; }",
        "    @Override public String toString() {",
        "      return \"B\" + %(ref_to_f)s;",
        "    }",
        "  },",
        "  C,",
        "}",
      };

      String[] output = {
        "package foo;",
        "enum E {",
        "  A,",
        "  B,",
        "  C,",
        "  ;",
        "  private static String B__f() {",
        "    return \".f\";",
        "  }",
        "  static private String B__toString()"
        + " { return \"B\" + %(ref_to_b__f)s; }",
        "  @java.lang.Override public java.lang.String toString() {",
        "    switch (this) {",
        "      case B:",
        "        return foo.E.B__toString();",
        "    }",
        "    return super.toString();",
        "  }",
        "}",
      };

      for (int i = 0, n = input.length; i < n; ++i) {
        input[i] = input[i].replace("%(ref_to_f)s", refToF);
      }
      for (int i = 0, n = output.length; i < n; ++i) {
        output[i] = output[i].replace("%(ref_to_b__f)s", refToBF);
      }

      assertPassOutput(
          new String[][] { output },
          new String[][] { input },
          null);
    }
  }

  @Test
  public static void testThisRewrittenInMigratedStatements() throws Exception {
    assertPassOutput(
        new String[][] {
          {
            "enum E {",
            "  A(), B(), C(),;",
            "  private static int A__x = (E.A).ordinal();",
            "  static {",
            "    A__x += E.A__f();",
            "  }",
            "  private static int A__f() { return A__x; }",
            "}",
          },
        },
        new String[][] {
          {
            "enum E {",
            "  A() {",
            "    int x = this.ordinal();",
            "    { this.x += this.f(); }",
            "    int f() { return this.x; }",
            "  },",
            "  B,",
            "  C () {};",
            "}",
          },
        },
        null);
  }

  public static void testAbstractMethodsMadeConcrete() throws Exception {
    assertPassOutput(
        new String[][] {
          {
            "package p;",
            "",
            "enum E {",
            "  X(), Y(),;",
            "",
            "  <T> int f(T x) {",
            "    switch (this) {",
            "      case X:",
            "        return p.E.<T>X__f(x);",
            "      case Y:",
            "        return p.E.<T>Y__f(x);",
            "    }",
            "    throw new java.lang.AssertionError(this);",
            "  }",
            "  private static <T> int X__f(T x) { return 1; }",
            "  private static <T> int Y__f(T x) { return 2; }",
            "}",
          },
        },
        new String[][] {
          {
            "//E",
            "package p;",
            "",
            "enum E {",
            "  X() {",
            "    <T> int f(T x) { return 1; }",
            "  },",
            "  Y () {",
            "    <T> int f(T x) { return 2; }",
            "  };",
            "",
            "  abstract <T> int f(T x);",
            "}",
          },
        },
        null);
  }

  public static void testSuperCallsFromSpecializedEnumMethods()
  throws Exception {
    if (true) { return; }  // TODO: fix super calls
    assertPassOutput(
        new String[][] {
          {
            "package p;",
            "",
            "enum E {",
            "  X(),",
            "  Y(),",
            "  ;",
            "",
            "  <T> private static int X__f(T x) { return base__f(1); }",
            "  private static int Y__g() { return base_f(f(base__g())); }",
            "  private static String Y__toString() {",
            "    return super.toString();",
            "  }",
            "  private static <T> int base_f(T x) { return 0; }",
            "  private static int base__g() { return 1337; }",
            "  <T> int f(T x) {",
            "    switch (this) {",
            "      case X:",
            "        return p.E.<T>X__f(x);",
            "    }",
            "    return p.E.<T>base__f(x);",
            "  }",
            "  int g() {",
            "    switch (this) {",
            "      case Y:",
            "        return p.E.Y__g();",
            "    }",
            "    return p.E.base__g();",
            "  }",
            "  public String toString() {",
            "    switch (this) {",
            "      case Y:",
            "        return p.E.Y__toString();",
            "    }",
            "    return super.toString();",
            "  }",
            "}",
          },
        },
        new String[][] {
          {
            "//E",
            "package p;",
            "",
            "enum E {",
            "  X() {",
            "    <T> int f(T x) { return super.f(1); }",
            "  },",
            "  Y () {",
            "    int g() { return super.f(f(super.g())); }",
            "    public String toString() {",
            "      return super.toString();",
            "    }",
            "  };",
            "",
            "  <T> int f(T x) { return 0; }",
            "  int g() { return 1337; }",
            "}",
          },
        },
        null);
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
