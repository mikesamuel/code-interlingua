package com.mikesamuel.cil.ast.passes;

import org.junit.Test;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.mikesamuel.cil.ast.CompilationUnitNode;
import com.mikesamuel.cil.parser.Unparse.UnparseVerificationException;

import junit.framework.TestCase;

@SuppressWarnings("javadoc")
public final class DefragmentTypesPassTest extends TestCase {

  private void assertRewritten(String want, String input) {
    ImmutableList<CompilationUnitNode> inputCus =
        PassTestHelpers.parseCompilationUnits(new String[] {
            "//" + getName() + "\n" + input });
    ImmutableList<CompilationUnitNode> wantedCus =
        PassTestHelpers.parseCompilationUnits(new String[] { want });
    ImmutableList<CompilationUnitNode> gotCus =
        new DefragmentTypesPass().run(inputCus);

    try {
      assertEquals(
          PassTestHelpers.serializeCompilationUnits(wantedCus, null),
          PassTestHelpers.serializeCompilationUnits(gotCus, null));
    } catch (UnparseVerificationException ex) {
      ex.printStackTrace();
      fail(ex.getMessage());
    }
  }


  @Test
  public void testField() {
    assertRewritten(
        "class C { public int i = 0; public int[] j = {}; public int k; }",
        "class C { public int i = 0, j[] = {}, k; }"
        );
  }

  @Test
  public void testConstants() {
    assertRewritten(
        "interface I {"
        + "public static final int i = 0;"
        + "public static final int[] j = {};"
        + "public static final int k = 2; }",

        "interface I { public static final int i = 0, j[] = {}, k = 2; }"
        );
  }

  @Test
  public void testLocal() {
    assertRewritten(
        "class C { C() { int i = 0; int[] j = {}; int k; } }",
        "class C { C() { int i = 0, j[] = {}, k; } }");
  }

  @Test
  public void testForEach() {
    assertRewritten(
        "class C { C(int[][] arrays) { for (int[] arr : arrays) { } } }",
        "class C { C(int[][] arrays) { for (int arr[] : arrays) { } } }");
  }

  @Test
  public void testForInitializer() {
    assertRewritten(
        Joiner.on('\n').join(
            "class C {",
            "  C() {",
            "    int[] a = getArray();",
            "    int i = 0;",
            "    for (; i < a.length; ++i) {",
            "      a[i] = 1;",
            "    }",
            "  }",
            "}"),
        Joiner.on('\n').join(
            "class C {",
            "  C() {",
            "    for (int a[] = getArray(), i = 0; i < a.length; ++i) {",
            "      a[i] = 1;",
            "    }",
            "  }",
            "}"));
  }

  @Test
  public void testClassMethod() {
    assertRewritten(
        "class C { int[][] f(int[][] x) { return x; } }",
        "class C { int f(int x[][])[][] { return x; } }"
        );
    assertRewritten(
        "class C { int[][] f(int[]... x) { return x; } }",
        "class C { int f(int... x[])[][] { return x; } }"
        );
  }

  @Test
  public void testInterfaceMethod() {
    assertRewritten(
        "interface I { int[][] f(int[][] x); }",
        "interface I { int f(int x[][])[][]; }"
        );
  }

  @Test
  public void testAnnotationElement() {
    assertRewritten(
        "@interface A { String[] x(); }",
        "@interface A { String x()[]; }");
  }

  @Test
  public void testNoReturnType() {
    assertRewritten(
        "class C { void f()[] {} }",
        "class C { void f()[] {} }");  // TODO: log an error
  }

  // TODO: do we need to at least issue an error on floating dims in
  // try/resource and catch
}
