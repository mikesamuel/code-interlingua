package com.mikesamuel.cil.ptree;

import org.junit.Test;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.io.CharSource;
import com.mikesamuel.cil.ast.MatchEvent;
import com.mikesamuel.cil.parser.Chain;
import com.mikesamuel.cil.parser.Input;
import com.mikesamuel.cil.parser.ParSer;
import com.mikesamuel.cil.parser.ParseState;

import junit.framework.TestCase;

@SuppressWarnings("javadoc")
public final class LiteralTest extends TestCase {

  @Test
  public final void testParse() throws Exception {
    ParSer lit = Literal.of("===", false, 1, 1, 1);
    ParseState ps = new ParseState(
        new Input(getName(), CharSource.wrap(" === ")));
    Optional<ParseState> resultOpt = lit.parse(ps, null);
    assertTrue(resultOpt.isPresent());
    ParseState result = resultOpt.get();
    assertEquals(
        ImmutableList.of(MatchEvent.token("===")),
        ImmutableList.copyOf(Chain.forward(result.output)));
    assertEquals(4, result.index);
    assertEquals(' ', result.input.content.charAt(result.index));
  }

}
