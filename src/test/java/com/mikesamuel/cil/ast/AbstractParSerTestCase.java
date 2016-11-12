package com.mikesamuel.cil.ast;

import java.io.IOException;
import java.util.Arrays;
import java.util.BitSet;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.junit.After;
import org.junit.Before;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.io.CharSource;
import com.mikesamuel.cil.ast.MatchEvent.Push;
import com.mikesamuel.cil.parser.Chain;
import com.mikesamuel.cil.parser.Input;
import com.mikesamuel.cil.parser.LeftRecursion;
import com.mikesamuel.cil.parser.ParSerable;
import com.mikesamuel.cil.parser.ParseErrorReceiver;
import com.mikesamuel.cil.parser.ParseResult;
import com.mikesamuel.cil.parser.ParseState;
import com.mikesamuel.cil.ptree.PTree;

import junit.framework.TestCase;

/**
 * Base class for tests that exercise ParSers.
 */
public abstract class AbstractParSerTestCase extends TestCase {

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
    LeftRecursion lr = new LeftRecursion();
    ParseResult result = ps.getParSer().parse(state, lr, parseErr);
    switch (result.synopsis) {
      case SUCCESS:
        ParseState afterParse = result.next();
        ImmutableList<MatchEvent> want = ImmutableList.copyOf(expected);
        ImmutableList<MatchEvent> got = filterEvents(
            relevant,
            Chain.forwardIterable(afterParse.output));
        if (!want.equals(got)) {
          assertEquals(
              content,
              Joiner.on("\n").join(want),
              Joiner.on("\n").join(got));
          fail();
        }
        return;
      case FAILURE:
        fail("`" + content + "` does not match " + ps.getParSer()
             + " : " + parseErr.getErrorMessage());
        return;
    }
    throw new AssertionError(result.synopsis);
  }

  /** SanityChecks to skip for a particular test case. */
  enum Fuzz {
    SAME_VARIANT,
    START_AT_EXPRESSION,
  }

  protected void parseSanityCheck(
      NodeVariant variant, String content, Fuzz... fuzzes) {
    parseSanityCheck(variant, input(content), fuzzes);
  }

  protected void parseSanityCheck(
      NodeVariant variant, Input input, Fuzz... fuzzes) {
    Set<Fuzz> fuzzSet = Sets.immutableEnumSet(Arrays.asList(fuzzes));

    StringBuilder allTokenText = new StringBuilder();
    ParseState start = new ParseState(input);
    for (ParseState ps = start; !ps.isEmpty();) {
      char ch = input.content.charAt(ps.index);
      int nConsumed = 1;
      if (ch == '\'' || ch == '"') {
        int end;
        int limit = input.content.length();
        for (end = ps.index + 1; end < limit; ++end) {
          char c = input.content.charAt(end);
          if (c == ch) {
            ++end;
            break;
          } else if (c == '\\') {
            ++end;
          }
        }
        nConsumed = end - ps.index;
      }
      allTokenText.append(input.content, ps.index, ps.index + nConsumed);
      ps = ps.advance(nConsumed);
    }
    LeftRecursion lr = new LeftRecursion();

    NodeType startNodeType = variant.getNodeType();
    if (fuzzSet.contains(Fuzz.START_AT_EXPRESSION)) {
      startNodeType = NodeType.Expression;
    }

    ParseResult result = PTree.complete(startNodeType)
        .getParSer().parse(start, lr, parseErr);
    if (!result.lrExclusionsTriggered.isEmpty()) {
      fail("LR failure not recovered from");
    }
    switch (result.synopsis) {
      case SUCCESS:
        StringBuilder tokensOnOutput = new StringBuilder();
        ParseState afterParse = result.next();
        List<MatchEvent.Push> firstPushes = Lists.newArrayList();
        boolean sawNonPush = false;
        // Check that pops and pushes match up so that the tree is well-formed.
        int stackDepth = 0;
        for (MatchEvent e : Chain.forwardIterable(afterParse.output)) {
          if (e instanceof MatchEvent.Push) {
            ++stackDepth;
            if (!sawNonPush) {
              firstPushes.add((MatchEvent.Push) e);
            }
          } else {
            sawNonPush = true;
            if (e instanceof MatchEvent.Pop) {
              if (stackDepth == 0) {
                fail(
                    "Parsing `" + input.content
                    + "`, depth goes negative after `" + tokensOnOutput + "`");
              }
              --stackDepth;
            } else if (e instanceof MatchEvent.Token) {
              tokensOnOutput.append(((MatchEvent.Token) e).content);
            } else if (e instanceof MatchEvent.Content) {
              tokensOnOutput.append(((MatchEvent.Content) e).content);
            }
          }
        }

        // Trees.of will throw an IllegalArgumentException if its
        // well-formedness checks fail.
        BaseNode node = Trees.of(
            start.input.lineStarts,
            Chain.forwardIterable(afterParse.output));

        if (firstPushes.isEmpty()) {
          fail("Variant never pushed");
        } else if (!fuzzSet.contains(Fuzz.SAME_VARIANT)) {
          if (fuzzSet.contains(Fuzz.START_AT_EXPRESSION)) {
            boolean found = false;
            for (MatchEvent.Push p : firstPushes) {
              if (p.variant == variant) {
                found = true;
                break;
              }
            }
            assertTrue(firstPushes.toString(), found);
          } else {
            assertEquals(input.content, variant, firstPushes.get(0).variant);
            assertEquals(variant, node.getVariant());
          }
        }
        assertEquals(
            input.content, allTokenText.toString(), tokensOnOutput.toString());
        return;
      case FAILURE:
        fail(
            "`" + input.content + "` does not match " + variant + "\n"
                + parseErr.getErrorMessage());
        return;
    }
    throw new AssertionError(result.synopsis);
  }

  protected static ImmutableList<MatchEvent> filterEvents(
      Set<? super NodeType> relevant,
      Iterable<? extends MatchEvent> events) {
    int depth = 0;
    BitSet included = new BitSet();  // Per depth, whether to include the pop
    ImmutableList.Builder<MatchEvent> b = ImmutableList.builder();
    for (MatchEvent e : events) {
      if (e instanceof MatchEvent.Push) {
        MatchEvent.Push push = (Push) e;
        boolean pushRelevant = !push.variant.isAnon()
            && relevant.contains(push.variant.getNodeType());
        included.set(depth, pushRelevant);
        if (pushRelevant) {
          b.add(e);
        }
        ++depth;
      } else if (e instanceof MatchEvent.Pop) {
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
    LeftRecursion lr = new LeftRecursion();
    ParseResult result = ps.getParSer().parse(state, lr, parseErr);
    if (!result.lrExclusionsTriggered.isEmpty()) {
      fail("LR failure not recovered from");
    }
    switch (result.synopsis) {
      case SUCCESS:
        ParseState afterParse = result.next();
        ImmutableList<MatchEvent> got =
            ImmutableList.copyOf(Chain.forwardIterable(afterParse.output));
        fail(ps + " matched `" + content + "`: " + got);
        return;
      case FAILURE:
        return;
    }
    throw new AssertionError(result.synopsis);
  }

  protected ParseState parseState(String content) {
      return new ParseState(input(content));
  }

  protected Input input(String content) {
    try {
      return new Input(getName(), CharSource.wrap(content));
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
      int index = latest.index;
      int ln = latest.input.lineStarts.getLineNumber(index);
      int co = latest.input.lineStarts.charInLine(index);
      return latest.input.lineStarts.source + ":" + ln + ":" + co + ": "
          + latestMessage;
    }
  }
}
