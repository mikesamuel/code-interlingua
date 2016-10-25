package com.mikesamuel.cil.ast;

import java.io.IOException;
import java.util.Arrays;
import java.util.BitSet;
import java.util.EnumSet;
import java.util.Set;

import org.junit.After;
import org.junit.Before;

import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import com.google.common.io.CharSource;
import com.mikesamuel.cil.ast.MatchEvent.PushMatchEvent;
import com.mikesamuel.cil.parser.Chain;
import com.mikesamuel.cil.parser.Input;
import com.mikesamuel.cil.parser.ParSerable;
import com.mikesamuel.cil.parser.ParseErrorReceiver;
import com.mikesamuel.cil.parser.ParseState;
import com.mikesamuel.cil.ptree.PTree;

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
      String content, MatchEvent... expected) {
    assertParsePasses(ps, EnumSet.allOf(NodeType.class), content, expected);
  }


  protected void assertParsePasses(
      ParSerable ps,
      Set<NodeType> relevant,
      String content, MatchEvent... expected) {
    ParseState state = parseState(content);
    Optional<ParseState> afterParse = ps.getParSer().parse(state, parseErr);
    if (afterParse.isPresent()) {
      ImmutableList<MatchEvent> want = ImmutableList.copyOf(expected);
      ImmutableList<MatchEvent> got = filterEvents(
          relevant,
          Chain.forward(afterParse.get().output));
      if (!want.equals(got)) {
        assertEquals(
            content,
            Joiner.on("\n").join(want),
            Joiner.on("\n").join(got));
        fail();
      }
    } else {
      fail("`" + content + "` does not match " + ps.getParSer()
           + " : " + parseErr.getErrorMessage());
    }
  }

  /** SanityChecks to skip for a particular test case. */
  enum Fuzz {
    SAME_VARIANT,
  }

  protected void parseSanityCheck(
      NodeVariant variant, String content, Fuzz... fuzzes) {
    Set<Fuzz> fuzzSet = Sets.immutableEnumSet(Arrays.asList(fuzzes));

    StringBuilder allTokenText = new StringBuilder();
    ParseState start = parseState(content);
    for (ParseState ps = start; !ps.isEmpty(); ps = ps.advance(1, true)) {
      allTokenText.append(content.charAt(ps.indexAfterIgnorables()));
    }

    Optional<ParseState> afterParseOpt = PTree.complete(variant.getNodeType())
        .getParSer().parse(start, parseErr);
    if (!afterParseOpt.isPresent()) {
      fail(
          "`" + content + "` does not match " + variant + "\n"
          + parseErr.getErrorMessage());
    }
    StringBuilder tokensOnOutput = new StringBuilder();
    ParseState afterParse = afterParseOpt.get();
    MatchEvent.PushMatchEvent firstPush = null;
    // Check that pops and pushes match up so that the tree is well-formed.
    int stackDepth = 0;
    for (MatchEvent e : Chain.forward(afterParse.output)) {
      if (e instanceof MatchEvent.PushMatchEvent) {
        ++stackDepth;
        if (firstPush == null) {
          firstPush = (MatchEvent.PushMatchEvent) e;
        }
      } else if (e instanceof MatchEvent.PopMatchEvent) {
        if (stackDepth == 0) {
          fail(
              "Parsing `" + content + "`, depth goes negative after `"
              + tokensOnOutput + "`");
        }
        --stackDepth;
      } else if (e instanceof MatchEvent.TokenMatchEvent) {
        tokensOnOutput.append(((MatchEvent.TokenMatchEvent) e).content);
      } else if (e instanceof MatchEvent.ContentMatchEvent) {
        tokensOnOutput.append(((MatchEvent.ContentMatchEvent) e).content);
      }
    }
    if (firstPush == null) {
      fail("Variant never pushed");
    } else if (!fuzzSet.contains(Fuzz.SAME_VARIANT)) {
      assertEquals(content, variant, firstPush.variant);
    }
    assertEquals(content, allTokenText.toString(), tokensOnOutput.toString());
  }

  protected static ImmutableList<MatchEvent> filterEvents(
      Set<? super NodeType> relevant,
      Iterable<? extends MatchEvent> events) {
    int depth = 0;
    BitSet included = new BitSet();  // Per depth, whether to include the pop
    ImmutableList.Builder<MatchEvent> b = ImmutableList.builder();
    for (MatchEvent e : events) {
      if (e instanceof MatchEvent.PushMatchEvent) {
        MatchEvent.PushMatchEvent push = (PushMatchEvent) e;
        boolean pushRelevant = relevant.contains(push.variant.getNodeType());
        included.set(depth, pushRelevant);
        if (pushRelevant) {
          b.add(e);
        }
        ++depth;
      } else if (e instanceof MatchEvent.PopMatchEvent) {
        Preconditions.checkState(depth >= 0);
        --depth;
        if (included.get(depth)) {
          b.add(e);
        }
      } else {
        b.add(e);
      }
    }
    Preconditions.checkState(depth == 0);
    return b.build();
  }

  protected void assertParseFails(ParSerable ps, String content) {
    ParseState state = parseState(content);
    Optional<ParseState> afterParse = ps.getParSer().parse(state, parseErr);
    if (afterParse.isPresent()) {
      ImmutableList<MatchEvent> got =
          ImmutableList.copyOf(Chain.forward(afterParse.get().output));
      fail(ps + " matched `" + content + "`: " + got);
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

    String getErrorMessage() {
      if (latest == null) { return "No error message"; }
      int index = latest.indexAfterIgnorables();
      int ln = latest.input.lineStarts.getLineNumber(index);
      int co = latest.input.lineStarts.charInLine(index);
      return latest.input.lineStarts.source + ":" + ln + ":" + co + ": "
          + latestMessage;
    }
  }
}
