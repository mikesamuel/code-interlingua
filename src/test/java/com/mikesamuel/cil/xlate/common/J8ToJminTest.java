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
import com.mikesamuel.cil.ast.passes.DespecializeEnumPass;
import com.mikesamuel.cil.ast.passes.PassTestHelpers;
import com.mikesamuel.cil.ast.passes.PassTestHelpers.LoggableOperation;
import com.mikesamuel.cil.ast.passes.flatten.FlattenPass;
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
            Optional<ImmutableList<J8FileNode>> unprocessedOpt =
                PassTestHelpers.maybeParseCompilationUnits(logger, inputLines);
            ImmutableList<J8FileNode> unprocessed = unprocessedOpt.get();
            CommonPassRunner commonPasses = new CommonPassRunner(logger);
            ImmutableList<J8FileNode> processed = commonPasses.run(unprocessed);

            DespecializeEnumPass dep = new DespecializeEnumPass(
                logger, commonPasses.getMemberInfoPool(),
                commonPasses.getMethodVariantPool());
            processed = dep.run(processed);
            commonPasses = new CommonPassRunner(logger);
            processed = commonPasses.run(processed);

            FlattenPass fp = new FlattenPass(
                logger, commonPasses.getTypePool().r);
            processed = fp.run(processed);
            commonPasses = new CommonPassRunner(logger);
            processed = commonPasses.run(processed);

            J8ToJmin translator = new J8ToJmin(
                logger, commonPasses.getTypePool());
            ImmutableList
            .Builder<com.mikesamuel.cil.ast.j8.CompilationUnitNode> cus
                = ImmutableList.builder();
            for (J8FileNode filenode : processed) {
              cus.add((CompilationUnitNode) filenode);
            }
            return translator.translate(cus.build());
          }

        },
        expectedLog);

    // TODO: do not disable delayed checks
    String got = PassTestHelpers.serializeNodes(xlated, null, true);

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
            "package bar;",
            "",
            "class D extends java.lang.Object {",
            "  public D() {",
            "    super ();",
            "  }",
            "}",
          },
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
    assertTranslated(
        new String[][] {
          {
            "package foo;",
            "",
            "interface I {",
            "}",
          },
          {
            "package foo;",
            "",
            "public enum E implements foo.I {",
            "  A(42),",
            "  B(1337),",
            "  C(-1, true),",
            "  ;",
            "  public final int x;",
            "  final boolean b;",
            "",
            "  E(int x) {",
            "    this(x, false);",
            "  }",
            "",
            "  E(int x, boolean b) {",
            "    super();",
            "    this.x = x;",
            "    this.b = b;",
            "  }",
            "  static private java.lang.String C__toString() {",
            "    return \"C!\";",
            "  }",
            "  @java.lang.Override() public java.lang.String toString() {",
            "    switch (this) {",
            "      case C:",
            "        return foo.E.C__toString();",
            "    }",
            "    return super.toString();",
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
            "class Outer extends java.lang.Object {",
            "  int x = 1;",
            "  public Outer() {",
            "    super();",
            "  }",
            "}",
          },
          {
            "package foo;",
            "",
            "class Outer$Inner extends java.lang.Object {",
            "  int x = 2;",
            "  {",
            "    System.err.println((this.this__Outer).x);",
            "  }",
            "  public Outer$Inner(foo.Outer this__Outer) {",
            "    super();",
            "    this.this__Outer = this__Outer;",
            "    if (this.this__Outer == null) { throw null; } else {}",
            "  }",
            "  private final foo.Outer this__Outer;",
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

  @Test
  public static void testInnerClassSubtypes() throws Exception {
    assertTranslated(
        new String[][] {
          {
            "package base;",
            "",
            "class Outer<T> extends java.lang.Object {",
            "  public Outer() {",
            "    super();",
            "  }",
            "}",
          },
          {
            "package base;",
            "",
            "class Outer$Inner<T_0, T> extends java.lang.Object {",
            "  public Outer$Inner() {",
            "    super();",
            "  }",
            "}",
          },
          {
            "package sub;",
            "",
            "class Outer extends base.Outer<java.lang.Integer> {",
            "  int x = 1;",
            "  public Outer() {",
            "    super();",
            "  }",
            "}",
          },
          {
            "package sub;",
            "",
            "class Outer$Inner extends"
            + " base.Outer$Inner<java.lang.Integer, java.lang.String> {",
            "  int x = 2;",
            "  { System.err.println((this.this__Outer).x); }",
            "  public Outer$Inner(sub.Outer this__Outer) {",
            "    this(this.initClosedOver(this__Outer));",
            "  }",
            "  private sub.Outer this__Outer;",
            "  private boolean initClosedOver(sub.Outer this__Outer) {",
            "    this.this__Outer = this__Outer;",
            "    if (this.this__Outer == null) { throw null; } else {}",
            "    return false;",
            "  }",
            "  private Outer$Inner(",
            "      @java.lang.SuppressWarnings(value=\"unused\") boolean ign) {",
            "    super();",
            "  }",
            "}",
          },
        },
        new String[][] {
          {
            "package base;",
            "",
            "class Outer<T> {",
            "  class Inner<T> {",
            "  }",
            "}",
          },
          {
            "package sub;",
            "",
            "class Outer extends base.Outer<Integer> {",
            "  int x = 1;",
            "  class Inner extends base.Outer.Inner<String> {",
            "    int x = 2;",
            "    { System.err.println(Outer.this.x); }",
            "  }",
            "}",
          },
        }
        );
  }

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
