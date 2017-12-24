package com.mikesamuel.cil.parser;

import java.util.List;

import org.junit.Test;

import com.google.common.base.Optional;
import com.mikesamuel.cil.ast.Trees;
import com.mikesamuel.cil.ast.j8.J8BaseNode;
import com.mikesamuel.cil.ast.j8.J8NodeType;
import com.mikesamuel.cil.parser.Unparse.Verified;

import junit.framework.TestCase;

@SuppressWarnings("javadoc")
public final class InputTest extends TestCase {

  private static J8BaseNode parse(ParSer ps, Input inp) {
    ParseResult result = ps.parse(
        new ParseState(inp), new LeftRecursion(), ParseErrorReceiver.DEV_NULL);
    assertEquals(
        inp.content().toString(),
        ParseResult.Synopsis.SUCCESS, result.synopsis);
    return Trees.forGrammar(J8NodeType.CompilationUnit.getGrammar())
        .of(result.next());
  }

  private void reparseTextInputAsEventInput(ParSer ps, String input)
      throws Exception {
    Input textInput = Input.builder().source(getName()).code(input).build();
    J8BaseNode root = parse(ps, textInput);

    Optional<SerialState> afterReparse = ps.unparse(new SerialState(
        SList.forwardIterable(Trees.startUnparse(null, root, null))),
        SerialErrorReceiver.DEV_NULL);
    assertTrue(afterReparse.isPresent());
    Verified v = Unparse.verify(
        SList.forwardIterable(afterReparse.get().output));

    Input eventsInput = Input.builder().events(v.events).build();
    J8BaseNode reroot = parse(ps, eventsInput);

    if (!root.equals(reroot)) {
      assertEquals(  // Dump string that diffs nicely in Eclipse.
          root.toAsciiArt(""),
          reroot.toAsciiArt(""));
      fail();
    }

    compareSourcePositionsRecursively(root, reroot);
  }

  private void compareSourcePositionsRecursively(J8BaseNode a, J8BaseNode b) {
    SourcePosition apos = a.getSourcePosition();
    SourcePosition bpos = b.getSourcePosition();
    if (!(apos == null ? bpos == null : apos.equals(bpos))) {
      assertEquals(a.getTextContent(" "), apos, bpos);
    }

    List<J8BaseNode> achildren = a.getChildren();
    List<J8BaseNode> bchildren = b.getChildren();
    int n = achildren.size();
    assertEquals(bchildren.size(), n);  // Structure checked before entry.
    for (int i = 0; i < n; ++i) {
      compareSourcePositionsRecursively(achildren.get(i), bchildren.get(i));
    }
  }

  @Test
  public void testEventBasedInput() throws Exception {
    reparseTextInputAsEventInput(
        J8NodeType.Expression.getParSer(), "1 + 1 * 42 - x.y % 32");
  }

  @Test
  public static void testAlreadyDecodedInput() {
    assertEquals(
        "foo\\ubar",
        Input.builder()
            .code("foo\\ubar")
            .source("test")
            .setFragmentOfAlreadyDecodedInput(true)
            .build()
            .content().toString());
    assertEquals(
        "foo\\uabcdef",
        Input.builder()
            .code("foo\\uabcdef")
            .source("test")
            .setFragmentOfAlreadyDecodedInput(true)
            .build()
            .content().toString());
  }
}
