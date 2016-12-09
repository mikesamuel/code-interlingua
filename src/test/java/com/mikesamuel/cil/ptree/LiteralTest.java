package com.mikesamuel.cil.ptree;

import org.junit.Test;

import com.google.common.collect.ImmutableList;
import com.mikesamuel.cil.event.Event;
import com.mikesamuel.cil.parser.SList;
import com.mikesamuel.cil.parser.Input;
import com.mikesamuel.cil.parser.LeftRecursion;
import com.mikesamuel.cil.parser.ParSer;
import com.mikesamuel.cil.parser.ParseErrorReceiver;
import com.mikesamuel.cil.parser.ParseResult;
import com.mikesamuel.cil.parser.ParseState;

import junit.framework.TestCase;

@SuppressWarnings("javadoc")
public final class LiteralTest extends TestCase {

  @Test
  public final void testParse() {
    ParSer lit = Literal.of("===", false, 1, 1, 1);
    Input input = Input.builder().source(getName()).code(" === ").build();
    ParseState ps = new ParseState(input);
    LeftRecursion lr = new LeftRecursion();
    ParseResult result = lit.parse(ps, lr, ParseErrorReceiver.DEV_NULL);
    assertEquals(ParseResult.Synopsis.SUCCESS, result.synopsis);
    ParseState after = result.next();
    assertEquals(
        ImmutableList.of(Event.token("===", -1)),
        ImmutableList.copyOf(SList.forwardIterable(after.output)));
    assertEquals(5, after.index);  // No more tokens
    CharSequence content = after.input.content();
    assertEquals(
        "",
        content.subSequence(after.index, content.length()).toString());
  }

}
