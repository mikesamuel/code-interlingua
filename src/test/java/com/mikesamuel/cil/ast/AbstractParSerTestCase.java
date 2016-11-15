package com.mikesamuel.cil.ast;

import java.util.Arrays;
import java.util.BitSet;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.junit.After;
import org.junit.Before;

import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.mikesamuel.cil.ast.MatchEvent.Push;
import com.mikesamuel.cil.parser.Chain;
import com.mikesamuel.cil.parser.Input;
import com.mikesamuel.cil.parser.LeftRecursion;
import com.mikesamuel.cil.parser.ParSer;
import com.mikesamuel.cil.parser.ParSerable;
import com.mikesamuel.cil.parser.ParseErrorReceiver;
import com.mikesamuel.cil.parser.ParseResult;
import com.mikesamuel.cil.parser.ParseState;
import com.mikesamuel.cil.parser.SerialErrorReceiver;
import com.mikesamuel.cil.parser.SerialState;
import com.mikesamuel.cil.parser.Unparse;
import com.mikesamuel.cil.ptree.PTree;

import junit.framework.AssertionFailedError;
import junit.framework.TestCase;

/**
 * Base class for tests that exercise ParSers.
 */
public abstract class AbstractParSerTestCase extends TestCase {

  protected LatestParseErrorReceiver parseErr;
  protected LatestSerialErrorReceiver serialErr;

  @Before @Override
  public void setUp() throws Exception {
    super.setUp();
    this.parseErr = new LatestParseErrorReceiver();
    this.serialErr = new LatestSerialErrorReceiver();
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
    ParSer parSer = ps.getParSer();
    ParseResult result = parSer.parse(state, lr, parseErr);
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
        doubleCheck(parSer, afterParse, ImmutableSet.of());
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
    /**
     * The variant of the root is not exactly the same as that under test.
     */
    SAME_VARIANT,
    /**
     * There are tokens in the serialized form that are not present in the
     * input so the double check does not match, though the parse trees will.
     */
    IMPLIED_TOKENS,
  }

  protected void parseSanityCheck(
      NodeVariant variant, String content, Fuzz... fuzzes) {
    parseSanityCheck(variant, input(content), fuzzes);
  }

  protected void parseSanityCheck(
      NodeVariant variant, Input input, Fuzz... fuzzes) {
    ImmutableSet<Fuzz> fuzzSet = Sets.immutableEnumSet(Arrays.asList(fuzzes));

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

    ParSer parSer = PTree.complete(startNodeType).getParSer();
    ParseResult result = parSer.parse(start, lr, parseErr);
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
          assertEquals(input.content, variant, firstPushes.get(0).variant);
          if (!variant.isAnon()) {
            assertEquals(variant, node.getVariant());
          }
        }
        assertEquals(
            input.content, allTokenText.toString(), tokensOnOutput.toString());
        doubleCheck(parSer, afterParse, fuzzSet);
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


  private static final boolean DEBUG_DOUBLE_CHECK = false;

  protected void doubleCheck(
      ParSer parSer, ParseState afterParse, ImmutableSet<Fuzz> fuzzSet) {
    BaseNode root = Trees.of(
        afterParse.input.lineStarts,
        Chain.forwardIterable(afterParse.output));
    if (DEBUG_DOUBLE_CHECK) {
      System.err.println("root=" + root);
    }

    ImmutableList<MatchEvent> structure = ImmutableList.copyOf(
        Chain.forwardIterable(Trees.startUnparse(null, root)));
    if (DEBUG_DOUBLE_CHECK) {
      System.err.println("\nstructure\n=======");
      Debug.dumpEvents(structure);
    }

    SerialState beforeRoot = new SerialState(structure);
    Optional<SerialState> afterRoot = parSer.unparse(beforeRoot, serialErr);
    if (!afterRoot.isPresent()) {
      fail(
          "Failed to unparse: " + serialErr.getErrorMessage()
          + "\n\t" + structure);
    }
    if (DEBUG_DOUBLE_CHECK) {
      System.err.println("afterRoot\n=========");
      Debug.dumpEvents(Chain.forwardIterable(afterRoot.get().output));
    }

    Unparse.Verified verified;
    try {
      verified = Unparse.verify(
          Chain.forwardIterable(afterRoot.get().output));
    } catch (Unparse.UnparseVerificationException ex) {
      throw (AssertionFailedError)
         new AssertionFailedError(getName()).initCause(ex);
    }
    if (DEBUG_DOUBLE_CHECK) {
      System.err.println("Verified\n=========");
      Debug.dumpEvents(verified.events);
    }

    StringBuilder sb = new StringBuilder();
    // TODO: Use Unparse.format
    for (MatchEvent e : verified.events) {
      String content = null;
      if (e instanceof MatchEvent.Token) {
        content = ((MatchEvent.Token) e).content;
      } else if (e instanceof MatchEvent.Content) {
        content = ((MatchEvent.Content) e).content;
      }
      if (content != null) {
        if (sb.length() != 0) {
          sb.append(' ');
        }
        sb.append(content);
      }
    }

    Input input = input(sb.toString());
    LatestParseErrorReceiver reparseErr = new LatestParseErrorReceiver();
    ParseResult reparse = parSer.parse(
        new ParseState(input), new LeftRecursion(), reparseErr);
    switch (reparse.synopsis) {
      case FAILURE:
        fail("Reparse of `" + input.content + "` failed: "
             + reparseErr.getErrorMessage());
        break;
      case SUCCESS:
        ParseState reparseState = reparse.next();
        ImmutableList<MatchEvent> reparsedEvents =
            ImmutableList.copyOf(
                Iterables.filter(
                    Chain.forwardIterable(reparseState.output),
                    new Predicate<MatchEvent>() {

                      @Override
                      public boolean apply(MatchEvent e) {
                        return !(e instanceof MatchEvent.SourcePositionMark);
                      }
                    }));
        ImmutableList<MatchEvent> afterParseEvents =
            ImmutableList.copyOf(Chain.forwardIterable(afterParse.output));
        if (!fuzzSet.contains(Fuzz.IMPLIED_TOKENS)) {
          // TODO: Do comparison when IMPLIED_TOKENS is present and ignore
          // differences based on transformations like s/\(\)//, s/,\}/\}/
          if (!afterParseEvents.equals(reparsedEvents)) {
            assertEquals(
                Joiner.on('\n').join(afterParseEvents),
                Joiner.on('\n').join(reparsedEvents));
            fail();
          }
        }

        BaseNode reparsedRoot = Trees.of(
            reparseState.input.lineStarts,
            Chain.forwardIterable(reparseState.output));
        assertEquals(root, reparsedRoot);
        return;
    }
    throw new AssertionError(reparse.synopsis);
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
    return Input.fromCharSequence(getName(), content);
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

  static final class LatestSerialErrorReceiver implements SerialErrorReceiver {

    SerialState latest;
    String latestMessage;

    @Override
    public void error(SerialState state, String message) {
      if (latest == null || state.index >= latest.index) {
        latest = state;
        latestMessage = message;
      }
    }

    String getErrorMessage() {
      if (latest == null) { return "No error message"; }
      return latestMessage + " @ " + latest.index;
    }
  }
}
