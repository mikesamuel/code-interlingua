package com.mikesamuel.cil.ast.passes;

import java.util.logging.Logger;

import org.junit.Test;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.mikesamuel.cil.ast.passes.PassTestHelpers.LoggableOperation;
import com.mikesamuel.cil.ast.traits.FileNode;
import com.mikesamuel.cil.parser.Unparse.UnparseVerificationException;

import junit.framework.TestCase;

@SuppressWarnings("javadoc")
public final class DefragmentTypesPassTest extends TestCase {

  private void assertRewritten(String want, String input, String... errors) {
    ImmutableList<FileNode> wantedFiles =
        PassTestHelpers.parseCompilationUnits(new String[] { want });

    ImmutableList<FileNode> gotFiles = PassTestHelpers.expectErrors(
        new LoggableOperation<ImmutableList<FileNode>>() {
          @Override
          public ImmutableList<FileNode> run(Logger logger) {
            ImmutableList<FileNode> inputCus =
                PassTestHelpers.parseCompilationUnits(new String[] {
                    "//" + getName(), input });

            return new DefragmentTypesPass(logger).run(inputCus);
          }
        }, errors);

    try {
      assertEquals(
          PassTestHelpers.serializeNodes(wantedFiles, null),
          PassTestHelpers.serializeNodes(gotFiles, null));
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
  public void testLongRunsOfDims() {
    assertRewritten(
        Joiner.on('\n').join(
            "class C {",
            "  C() {",
            "    int[] i = null,",
            "          j = {};",
            "    int[][] k = {},",
            "            l = {};",
            "  }",
            "}"),
        Joiner.on('\n').join(
            "class C {",
            "  C() {",
            "    int i[] = null,",
            "        j[] = {},",
            "        k[][] = {},",
            "        l[][] = {};",
            "  }",
            "}")
        );
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
            "    for (int i = 0; i < a.length; ++i) {",
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
        "interface I { int[][] f(int[][][] x); }",
        "interface I { int f(int[] x[][])[][]; }"
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
        "class C { void f()[] {} }",
        //123456789012345678901234567890
        //         1         2
        //                 ^^

        "//testNoReturnType:2+18-20: "
        + "Floating array dimensions [] could not be reattached to a type"
        );
  }

  @Test
  public void testTryCatch() {
    assertRewritten(
        "class C { C() { try { } catch (Exception e[]) { } } }",
        "class C { C() { try { } catch (Exception e[]) { } } }",
        // Arrays can't be throwable so this is harmless.
        "Floating array dimensions [] could not be reattached to a type");
  }

  @Test
  public void testTryResource() {
    // Meh.  Arrays can't be AutoCloseable so useless, but ok.
    assertRewritten(
        "class C { C() { try (OutputStream[] o = open()) { } } }",
        "class C { C() { try (OutputStream o[] = open()) { } } }"
        );
  }

}
