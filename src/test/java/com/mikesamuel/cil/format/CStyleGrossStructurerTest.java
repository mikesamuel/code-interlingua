package com.mikesamuel.cil.format;

import java.util.Arrays;

import org.junit.Test;

import com.mikesamuel.cil.ast.NodeVariant;
import com.mikesamuel.cil.ast.j8.J8NodeType;
import com.mikesamuel.cil.format.java.Java8Formatters;
import com.mikesamuel.cil.parser.Input;
import com.mikesamuel.cil.parser.LeftRecursion;
import com.mikesamuel.cil.parser.ParSerable;
import com.mikesamuel.cil.parser.ParseErrorReceiver;
import com.mikesamuel.cil.parser.ParseResult;
import com.mikesamuel.cil.parser.ParseState;
import com.mikesamuel.cil.parser.SList;
import com.mikesamuel.cil.parser.Unparse;
import com.mikesamuel.cil.ptree.PTree;

import junit.framework.TestCase;

@SuppressWarnings("javadoc")
public final class CStyleGrossStructurerTest extends TestCase {

  @Test
  public void testEmptyCompilationUnit() throws Exception {
    assertReformattedJava("", "");
  }

  @Test
  public void testPackageThenClass() throws Exception {
    assertReformattedJava(
        ""
        + "package foo.bar;\n"
        + "interface I extends Runnable {}");
  }

  @Test
  public void testTypeParameters() throws Exception {
    assertReformattedJava(
        ""
        + "class Bar {\n"
        + "  static class T {}\n"
        + "  static <P extends T, T> void f() {}\n"
        + "}");
  }


  @Test
  public void testSimpleClass() throws Exception {
    assertReformattedJava("class C {}", "class C {}");
  }

  @Test
  public void testLineWrapping() throws Exception {
    assertReformattedJava(
        ""
            + "import foo.bar.baz.boo.far.*;"
            + "\n"
            + "import bar.baz.boo.far.foo.*;",
        ""
            + "import foo.bar.baz.boo.far.*;"
            + "\n"
            + "import bar.baz.boo.far.foo.*;");
  }

  @Test
  public void testCommaWrapping() throws Exception {
    assertReformattedJava(
        ""
        + "{\n"
        + "  0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11,\n"
        + "  12, 13, 14, 15,\n"
        + "}",
        "{ 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, }",
       J8NodeType.ArrayInitializer);
  }

  @Test
  public void testTokenThatIsBrokenAfterFollowedByVeryLongToken()
  throws Exception {
    assertReformattedJava(
        ""
            + "foo();"
            + "\n"
            + "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
            + "\n"
            + "();",
        ""
            + "foo();"
            + "\n"
            + "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa();",
        J8NodeType.BlockStatements);
  }

  @Test
  public static void testCommentIndentation() {
    assertFormattedJava(
        ""
        + "{\n"
        + "  /**\n"
        + "   * Foo\n"
        + "   * Bar\n"
        + "   */\n"
        + "  Foo.bar.baz.boo.far.faz(argument,\n"
        + "      argument);\n"
        + "}",

        "{",

        ""
        + "/**\n"
        + " * Foo\n"
        + " * Bar\n"
        + " */",

        "Foo", ".", "bar", ".", "baz", ".", "boo", ".", "far", ".", "faz",
        "(", "argument", ",", "argument", ")", ";",
        "}");
  }

  private void assertReformattedJava(String canon)
  throws Exception {
    assertReformattedJava(canon, canon, J8NodeType.CompilationUnit);
  }

  private void assertReformattedJava(String want, String input)
  throws Exception {
    assertReformattedJava(want, input, J8NodeType.CompilationUnit);
  }

  private void assertReformattedJava(String want, String input, ParSerable ps)
  throws Exception {
    Input inp = Input.builder().source(getName()).code(input).build();
    ParseResult result = PTree.complete(ps).getParSer().parse(
        new ParseState(inp), new LeftRecursion(),
        ParseErrorReceiver.DEV_NULL);
    switch (result.synopsis) {
      case SUCCESS:
        Formatter<SList<NodeVariant<?, ?>>> formatter =
           Java8Formatters.createFormatter();
        formatter.setSoftColumnLimit(40);
        Unparse.Verified v = Unparse.verify(SList.forwardIterable(
            result.next().output));
        FormattedSource code = Unparse.format(v, formatter);
        assertEquals(input, want, code.code);
        break;
      default:
        fail(result.synopsis.name());
    }
  }

  private static void assertFormattedJava(String want, String... tokens) {
    Formatter<SList<NodeVariant<?, ?>>> formatter =
        Java8Formatters.createFormatter();
     formatter.setSoftColumnLimit(40);
     for (String token : tokens) {
       formatter.token(token);
     }
     FormattedSource code = formatter.format();
     assertEquals(
         Arrays.toString(tokens),
         want, code.code);
  }

}
