package com.mikesamuel.cil.ast;

import java.io.IOException;

import org.junit.After;
import org.junit.Before;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.io.CharSource;
import com.mikesamuel.cil.parser.Input;
import com.mikesamuel.cil.parser.ParSerable;
import com.mikesamuel.cil.parser.ParseErrorReceiver;
import com.mikesamuel.cil.parser.ParseState;

import junit.framework.TestCase;

abstract class AbstractParSerTestCase extends TestCase {

  protected LatestParseErrorReceiver parseErr;

  @Before @Override
  public void setUp() throws Exception {
    super.setUp();
    this.parseErr = new LatestParseErrorReceiver();
  }

  @After @Override
  public void tearDown() throws Exception {
    super.tearDown();
    this.parseErr = null;
  }

  protected void assertParsePasses(
      ParSerable ps,
      String content, MatchEvent... wanted) {
    ParseState state = parseState(content);
    Optional<ParseState> afterParse = ps.getParSer().parse(state, parseErr);
    if (afterParse.isPresent()) {
      assertEquals(
          content,
          ImmutableList.copyOf(wanted),
          ImmutableList.copyOf(afterParse.get().output.forward()));
    } else {
      fail("`" + content + "` does not match " + ps.getParSer().getClass());
    }
  }

  protected ParseState parseState(String content) {
    try {
      return new ParseState(new Input(getName(), CharSource.wrap(content)));
    } catch (IOException ex) {
      throw (AssertionError) new AssertionError().initCause(ex);
    }
  }

  static final class LatestParseErrorReceiver implements ParseErrorReceiver {

    ParseState latest;
    String latestMessage;

    @Override
    public void error(ParseState state, String message) {
      if (latest == null || state.index >= latest.index) {
        latest = state;
        latestMessage = message;
      }
    }
  }
}
