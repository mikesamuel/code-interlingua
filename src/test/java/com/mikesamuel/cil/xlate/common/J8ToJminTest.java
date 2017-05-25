package com.mikesamuel.cil.xlate.common;

import java.util.logging.Logger;

import org.junit.Test;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.mikesamuel.cil.ast.j8.CompilationUnitNode;
import com.mikesamuel.cil.ast.j8.J8FileNode;
import com.mikesamuel.cil.ast.jmin.JminBaseNode;
import com.mikesamuel.cil.ast.passes.CommonPassRunner;
import com.mikesamuel.cil.ast.passes.PassTestHelpers;
import com.mikesamuel.cil.ast.passes.PassTestHelpers.LoggableOperation;
import com.mikesamuel.cil.parser.Unparse;

import junit.framework.TestCase;

@SuppressWarnings("javadoc")
public final class J8ToJminTest extends TestCase {

  private static void assertTranslated(
      String[][] wantLines, String[][] inputLines,
      String... expectedLog)
  throws Unparse.UnparseVerificationException {

    ImmutableList<? extends JminBaseNode> xlated = PassTestHelpers.expectErrors(
        new LoggableOperation<ImmutableList<? extends JminBaseNode>>() {

          @Override
          public ImmutableList<? extends JminBaseNode> run(Logger logger) {
            ImmutableList<J8FileNode> unprocessed =
                PassTestHelpers.parseCompilationUnits(inputLines);
            CommonPassRunner commonPasses = new CommonPassRunner(logger);
            ImmutableList<J8FileNode> processed = commonPasses.run(unprocessed);
            J8ToJmin translator = new J8ToJmin(
                logger, commonPasses.getTypePool());
            ImmutableList.Builder<com.mikesamuel.cil.ast.j8.CompilationUnitNode>
                cus = ImmutableList.builder();
            for (J8FileNode filenode : processed) {
              cus.add((CompilationUnitNode) filenode);
            }
            return translator.translate(cus.build());
          }

        },
        expectedLog);

    String got = PassTestHelpers.serializeNodes(xlated, null);

    Optional<String> wantOpt = PassTestHelpers.normalizeCompilationUnitSource(
        wantLines);
    Preconditions.checkState(wantOpt.isPresent());
    assertEquals(wantOpt.get(), got);
  }

  @Test
  public static void testSingleClassDeclaration() throws Exception {
    assertTranslated(
        new String[][] {
          {
            "package foo.bar;",
            "class C extends java.lang.Object {",
            "  public C() { super(); }",
            "}",
          }
        },
        new String[][] {
          {
            "package foo.bar;",
            "class C {",
            "  public C() {}",
            "}",
          }
        });
  }


  @Test
  public static void testQualifiedNameInExtends() throws Exception {
    assertTranslated(
        new String[][] {
          {
            "package foo;",
            "",
            "class C extends bar.D {",
            "  public C() {",
            "    super ();",
            "  }",
            "}",
          },
          {
            "package bar;",
            "",
            "class D extends java.lang.Object {",
            "  public D() {",
            "    super ();",
            "  }",
            "}",
          },
        },
        new String[][] {
          {
            "package foo;",
            "",
            "import bar.D;",
            "",
            "class C extends D {",
            "  public C() {}",
            "}",
            ";",
          },
          {
            "package bar;",
            "class D {",
            "}",
          },
        });
  }

  @Test
  public static void testEnum() throws Exception {
if (false)  // TODO
    assertTranslated(
        new String[][] {
          {
            "package foo;",
            "",
            "class C extends bar.D {",
            "  public C() {",
            "    super ();",
            "  }",
            "}",
          },
        },
        new String[][] {
          {
            "package foo;",
            "",
            "public enum E implements I {",
            "  A(42),",
            "  B(1337),",
            "  C(-1, true) {",
            "    @Override public String toString() {",
            "      return \"C!\";",
            "    }",
            "  },",
            "  ;",
            "  public final int x;",
            "  final boolean b;",
            "",
            "  E(int x) {",
            "    this(x, false);",
            "  }",
            "",
            "  E(int x, boolean b) {",
            "    this.x = x;",
            "    this.b = b;",
            "  }",
            "}",
          },
          {
            "package foo;",
            "",
            "interface I {",
            "}",
          },
        });
  }

  @Test
  public static void testQualifiedThisReferences() throws Exception {
    assertTranslated(
        new String[][] {
          {
            "package foo;",
            "",
            "class Inner extends java.lang.Object {",
            "  final foo.Outer $$containingInstance;",
            "  int x = 2;",
            "  {",
            "    System.err.println((this.$$containingInstance).x);",
            "  }",
            "  public Inner(foo.Outer $$containingInstance) {",
            "    super();",
            "    this.$$containingInstance = $$containingInstance;",
            "  }",
            "}",
          },
          {
            "package foo;",
            "",
            "class Outer extends java.lang.Object {",
            "  int x = 1;",
            "  public Outer() {",
            "    super();",
            "  }",
            "}",
          },
        },
        new String[][] {
          {
            "package foo;",
            "",
            "class Outer {",
            "  int x = 1;",
            "  class Inner {",
            "    int x = 2;",
            "    { System.err.println(Outer.this.x); }",
            "  }",
            "}",
          },
        });
  }


  // TODO: test that custom enum instances with fields and overridden methods
  // are converted to enums without specialization

  // TODO: Calls to outer class super-type methods like
  // class Super {
  //   void foo() { System.err.println("Super.foo"); }
  // }
  // class Outer extends Super {
  //   @Override void foo() { System.err.println("Outer.foo"); }
  //
  //   class D {
  //     { Outer.super.foo(); }
  //   }
  // }

}
