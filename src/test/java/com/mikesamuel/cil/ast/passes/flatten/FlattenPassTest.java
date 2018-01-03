package com.mikesamuel.cil.ast.passes.flatten;

import java.util.logging.Logger;

import org.junit.Test;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.mikesamuel.cil.ast.j8.CompilationUnitNode;
import com.mikesamuel.cil.ast.j8.J8FileNode;
import com.mikesamuel.cil.ast.passes.CommonPassRunner;
import com.mikesamuel.cil.ast.passes.DespecializeEnumPass;
import com.mikesamuel.cil.ast.passes.PassTestHelpers;
import com.mikesamuel.cil.ast.passes.PassTestHelpers.LoggableOperation;
import com.mikesamuel.cil.parser.Unparse;

import junit.framework.TestCase;

@SuppressWarnings("javadoc")
public final class FlattenPassTest extends TestCase {

  private static void assertTranslated(
      String[][] wantLines, String[][] inputLines,
      String... expectedLog)
  throws Unparse.UnparseVerificationException {

    ImmutableList<? extends J8FileNode> xlated = PassTestHelpers.expectErrors(
        new LoggableOperation<ImmutableList<? extends J8FileNode>>() {

          @Override
          public ImmutableList<? extends J8FileNode> run(Logger logger) {
            Optional<ImmutableList<J8FileNode>> unprocessedOpt =
                PassTestHelpers.maybeParseCompilationUnits(logger, inputLines);
            ImmutableList<J8FileNode> unprocessed = unprocessedOpt.get();
            CommonPassRunner commonPasses = new CommonPassRunner(logger);
            ImmutableList<J8FileNode> processed = commonPasses.run(unprocessed);
            DespecializeEnumPass dep = new DespecializeEnumPass(
                logger, commonPasses.getMemberInfoPool(),
                commonPasses.getMethodVariantPool());
            processed = dep.run(processed);
            processed = commonPasses.run(processed);

            FlattenPass flattenPass = new FlattenPass(
                logger, commonPasses.getTypeInfoResolver());
            ImmutableList.Builder<CompilationUnitNode> cus =
                ImmutableList.builder();
            for (J8FileNode filenode : processed) {
              cus.add((CompilationUnitNode) filenode);
            }
            return flattenPass.run(cus.build());
          }

        },
        expectedLog);

    String got = PassTestHelpers.serializeNodes(xlated, null);

    Optional<String> wantOpt = PassTestHelpers.normalizeCompilationUnitSource(
        wantLines);
    Preconditions.checkState(wantOpt.isPresent());
    assertEquals(wantOpt.get(), got);

    // TODO: rerun common passes
  }

  @Test
  public static void testSingleClassDeclaration() throws Exception {
    assertTranslated(
        new String[][] {
          {
            "package foo.bar;",
            "class C {",
            "  public C() {}",
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
            "class D {",
            "  public D() {}",
            "}",
          },
          {
            "package foo;",
            "import bar.D;",
            "",
            "class C extends D {",
            "  public C() {}",
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
            "interface I {}",
          },
          {
            "package foo;",
            "public enum E implements I {",
            "  A(42), B(1337), C(-1, true),;",
            "  public final int x;",
            "  final boolean b;",
            "  E(int x) { this (x, false); }",
            "  E(int x, boolean b) { this.x = x; this.b = b; }",
            "  static private String C__toString() { return \"C!\"; }",
            "  @java.lang.Override public java.lang.String toString() {",
            "    switch (this) {",
            "      case C : return foo.E.C__toString();",
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
            "class Outer {",
            "  int x = 1;",
            "  public Outer() {}",
            "}",
          },
          {
            "package foo;",
            "",
            "class Outer$Inner {",
            "  int x = 2;",
            "  {",
            "    System.err.println((this.this__Outer).x);",
            "  }",
            "  public Outer$Inner(foo.Outer this__Outer) {",
            "    this.this__Outer = this__Outer;",
            "    if (this.this__Outer == null) { throw null; }",
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
            "package base; class Outer<T> { public Outer() {} }",
          },
          {
            "package base; class Outer$Inner<T_0, T> { public Outer$Inner() {} }",
          },
          {
            "package sub;",
            "class Outer extends base.Outer<Integer> {",
            "  int x = 1;",
            "  public Outer() {}",
            "}",
          },
          {
            "package sub;",
            "class Outer$Inner",
            "extends base.Outer$Inner<java.lang.Integer, java.lang.String> {",
            "  int x = 2;",
            "  {",
            "    System.err.println((this.this__Outer).x);",
            "  }",
            "  public Outer$Inner(sub.Outer this__Outer) {",
            "    this(this.initClosedOver(this__Outer));",
            "  }",
            "  private sub.Outer this__Outer;",
            "  private boolean initClosedOver(sub.Outer this__Outer) {\n" +
            "    this.this__Outer = this__Outer;\n" +
            "    if (this.this__Outer == null) { throw null; }\n" +
            "    return false;\n" +
            "  }",
            "  private Outer$Inner(",
            "      @java.lang.SuppressWarnings(\"unused\") boolean ign) {",
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


  @Test
  public static void testDoublyNestedInnerThatClosesOverLocal()
  throws Exception {
    assertTranslated(
        new String[][] {
          {
            "package p;",
            "import java.util.Iterator;",
            "class C {",
            "  void f(int start, int end) {",
            "    return new p.C$f$1<java.lang.Integer> (end, start);",
            "  }",
            "  public C() {}",
            "}",
          },
          {
            "package p;",
            "import java.util.Iterator;",
            "final class C$f$1<T> extends java.lang.Object",
            "implements java.lang.Iterable<T> {",
            "  public Iterator<Integer> iterator() {",
            "    return new p.C$f$1$iterator$1<java.lang.Integer> (",
            "        this.closedOver__end, this.closedOver__start);",
            "  }",
            "  private final int closedOver__end;",
            "  private final int closedOver__start;",
            "  C$f$1(int closedOver__end, int closedOver__start) {",
            "    this.closedOver__end = closedOver__end;",
            "    this.closedOver__start = closedOver__start;",
            "  }",
            "}",
          },
          {
            "package p;",
            "import java.util.Iterator;",
            "final class C$f$1$iterator$1<E> extends java.lang.Object",
            "implements java.util.Iterator<E> {",
            // We can't use simple initialization if any field
            // initializers mention captured fields.
            // docs.oracle.com/javase/specs/jls/se8/html/jls-12.html#jls-12.5
            "  private int i = closedOver__start;",
            "  public boolean hasNext() { return i < closedOver__end; }",
            "  public Integer next() { return i++; }",
            "  private int closedOver__end;",
            "  private int closedOver__start;",
            "  C$f$1$iterator$1(int closedOver__end, int closedOver__start) {",
            "    this(this.initClosedOver(closedOver__end, closedOver__start));",
            "  }",
            "  private boolean initClosedOver(int closedOver__end, int closedOver__start) {",
            "    this.closedOver__end = closedOver__end;",
            "    this.closedOver__start = closedOver__start;",
            "    return false;",
            "  }",
            "  private C$f$1$iterator$1(",
            "      @java.lang.SuppressWarnings(\"unused\") boolean ign) {",
            "  }",
            "}",
          }
        },
        new String[][] {
          {
            "package p;",
            "",
            "import java.util.Iterator;",
            "",
            "class C {",
            "  void f(int start, int end) {",
            "    return new Iterable<Integer>() {",
            "      public Iterator<Integer> iterator() {",
            "        return new Iterator<Integer>() {",
            "          private int i = start;",
            "          public boolean hasNext() { return i < end; }",
            "          public Integer next() { return i++; }",
            "        };",
            "      }",
            "    };",
            "  }",
            "}",
          }
        });
  }

  @Test
  public static void testMethodInClassNameAmbiguity() throws Exception {
    assertTranslated(
        new String[][] {
          {
            "package r;",
            "public class Outer {",
            "  void f() {",
            "    r.Outer$f$C x = new r.Outer$f$C();",
            "    r.Outer$C y = new r.Outer$C();",
            "    return new r.Outer$f$1(x, y);",
            "  }",
            "  public Outer() {}",
            "}",
          },
          {
            "package r;",
            "class Outer$f$C {",
            "  public String toString() { return \"in-method\"; }",
            "  public Outer$f$C() {}",
            "}",
          },
          {
            "package r;",
            "final class Outer$f$1 extends java.lang.Object {",
            "  @Override public String toString() {",
            "    return (new r.Outer$f$C())",
            "    + \",\" + new r.Outer$C()",
            "    + \",\" + closedOver__x",
            "    + \",\" + closedOver__y;",
            "  }",
            "  private final r.Outer$f$C closedOver__x;",
            "  private final r.Outer$C closedOver__y;",
            "  Outer$f$1(r.Outer$f$C closedOver__x, r.Outer$C closedOver__y) {",
            "    this.closedOver__x = closedOver__x;",
            "    this.closedOver__y = closedOver__y;",
            "  }",
            "}",
          },
          {
            "package r;",
            "class Outer$C {",
            "  public String toString() { return \"in-class\"; }",
            "  public Outer$C() {}",
            "}",
          },
        },
        new String[][] {
          {
            "package r;",
            "",
            "public class Outer {",
            "  void f() {",
            "    class C {",
            "      public String toString() { return \"in-method\"; }",
            "    }",
            "    C x = new C();",
            "    Outer.C y = new Outer.C();",
            "    return new Object() {",
            "      @Override public String toString() {",
            "        return (new C())",
            "          + \",\" + new r.Outer.C()",
            "          + \",\" + x",
            "          + \",\" + y;",
            "      }",
            "    };",
            "  }",
            "  class C {",
            "    public String toString() { return \"in-class\"; }",
            "  }",
            "}",
          },
        });
  }

  @Test
  public static void testInnerClassWithConstructorArguments()
  throws Exception {
    assertTranslated(
        new String[][] {
          {
            "package p;",
            "abstract class A<N extends Number> {",
            "  final N n;",
            "  A(N n) { this.n = n; }",
            "}",
          },
          {
            "package p;",
            "class C {",
            "  void f(int n) {",
            "    new p.C$f$1<java.lang.Integer>(n);",
            "  }",
            "  public C() {}",
            "}",
          },
          {
            "package p;",
            "final class C$f$1<N extends java.lang.Number> extends p.A<N> {",
            "  C$f$1(N a0) { super(a0); }",
            "}",
          },
        },
        new String[][] {
          {
            "package p;",
            "",
            "abstract class A<N extends Number> {",
            "  final N n;",
            "  A(N n) { this.n = n; }",
            "}",
            "",
            "class C {",
            "  void f(int n) {",
            "    new A<Integer>(n) {};",
            "  }",
            "}",
          }
        });
  }

  @Test
  public static void testExplicitOuterThis() throws Exception {
    assertTranslated(
        new String[][] {
          {
            "package q;",
            "",
            "class Outer {",
            "  private int x;",
            "  private Outer() {}",
            "  private static final Outer OUTER = new Outer();",
            "",
            "  static q.Outer$Inner create() {",
            "    return new q.Outer$Inner(null);",
            "  }",
            "}",
          },
          {
            "package q;",
            "",
            "class Outer$Inner {",
            "  private Outer$Inner(q.Outer this__Outer) {",
            "    super();",
            "    this.this__Outer = (q.Outer.OUTER);",
            "    if (this.this__Outer == null) { throw null; }",
            "  }",
            "  int getX() { return ((this.this__Outer).x); }",
            "  private final q.Outer this__Outer;",
            "}",
          },
        },
        new String[][] {
          {
            "package q;",
            "",
            "class Outer {",
            "  private int x;",
            "  private Outer() {}",
            "  private static final Outer OUTER = new Outer();",
            "",
            "  class Inner {",
            "    private Inner() {",
            "      OUTER.super();",
            "    }",
            "    int getX() { return x; }",
            "  }",
            "",
            "  static Inner create() {",
            "    return new Inner();",
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
