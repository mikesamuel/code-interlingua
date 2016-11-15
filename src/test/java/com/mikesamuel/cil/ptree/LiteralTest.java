package com.mikesamuel.cil.ptree;

import org.junit.Test;

import com.google.common.collect.ImmutableList;
import com.mikesamuel.cil.ast.MatchEvent;
import com.mikesamuel.cil.parser.Chain;
import com.mikesamuel.cil.parser.Input;
import com.mikesamuel.cil.parser.LeftRecursion;
import com.mikesamuel.cil.parser.ParSer;
import com.mikesamuel.cil.parser.ParseResult;
import com.mikesamuel.cil.parser.ParseState;

import junit.framework.TestCase;

@SuppressWarnings("javadoc")
public final class LiteralTest extends TestCase {

  @Test
  public final void testParse() {
    ParSer lit = Literal.of("===", false, 1, 1, 1);
    ParseState ps = new ParseState(Input.fromCharSequence(getName(), " === "));
    LeftRecursion lr = new LeftRecursion();
    ParseResult result = lit.parse(ps, lr, null);
    assertEquals(ParseResult.Synopsis.SUCCESS, result.synopsis);
    ParseState after = result.next();
    assertEquals(
        ImmutableList.of(MatchEvent.token("===", -1)),
        ImmutableList.copyOf(Chain.forwardIterable(after.output)));
    assertEquals(5, after.index);  // No more tokens
    assertEquals("", after.input.content.substring(after.index));
  }

}
