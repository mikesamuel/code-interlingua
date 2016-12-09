package com.mikesamuel.cil.parser;

import org.junit.Test;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.mikesamuel.cil.ast.BaseNode;
import com.mikesamuel.cil.ast.NodeType;
import com.mikesamuel.cil.ast.Trees;
import com.mikesamuel.cil.parser.Unparse.Verified;

import junit.framework.TestCase;

@SuppressWarnings("javadoc")
public final class InputTest extends TestCase {

  private static BaseNode parse(ParSer ps, Input inp) {
    ParseResult result = ps.parse(
        new ParseState(inp), new LeftRecursion(), ParseErrorReceiver.DEV_NULL);
    assertEquals(
        inp.content().toString(),
        ParseResult.Synopsis.SUCCESS, result.synopsis);
    return Trees.of(result.next());
  }

  private void reparseTextInputAsEventInput(ParSer ps, String input)
      throws Exception {
    Input textInput = Input.builder().source(getName()).code(input).build();
    BaseNode root = parse(ps, textInput);

    Optional<SerialState> afterReparse = ps.unparse(new SerialState(
        SList.forwardIterable(Trees.startUnparse(null, root, null))),
        SerialErrorReceiver.DEV_NULL);
    assertTrue(afterReparse.isPresent());
    Verified v = Unparse.verify(
        SList.forwardIterable(afterReparse.get().output));

    Input eventsInput = Input.builder().events(v.events).build();
    BaseNode reroot = parse(ps, eventsInput);

    if (!root.equals(reroot)) {
      assertEquals(  // Dump string that diffs nicely in Eclipse.
          root.toAsciiArt(""),
          reroot.toAsciiArt(""));
      fail();
    }

    compareSourcePositionsRecursively(root, reroot);
  }

  private void compareSourcePositionsRecursively(BaseNode a, BaseNode b) {
    SourcePosition apos = a.getSourcePosition();
    SourcePosition bpos = b.getSourcePosition();
    if (!(apos == null ? bpos == null : apos.equals(bpos))) {
      assertEquals(a.getTextContent(" "), apos, bpos);
    }

    ImmutableList<BaseNode> achildren = a.getChildren();
    ImmutableList<BaseNode> bchildren = b.getChildren();
    int n = achildren.size();
    assertEquals(bchildren.size(), n);  // Structure checked before entry.
    for (int i = 0; i < n; ++i) {
      compareSourcePositionsRecursively(achildren.get(i), bchildren.get(i));
    }
  }

  @Test
  public void testEventBasedInput() throws Exception {
    reparseTextInputAsEventInput(
        NodeType.Expression.getParSer(), "1 + 1 * 42 - x.y % 32");
  }
}
