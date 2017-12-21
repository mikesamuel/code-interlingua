package com.mikesamuel.cil.ast;

import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;

import org.junit.After;
import org.junit.Before;

import com.google.common.base.Functions;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.mikesamuel.cil.ast.j8.J8NodeType;
import com.mikesamuel.cil.event.Debug;
import com.mikesamuel.cil.event.Event;
import com.mikesamuel.cil.format.FormattedSource;
import com.mikesamuel.cil.parser.Input;
import com.mikesamuel.cil.parser.LeftRecursion;
import com.mikesamuel.cil.parser.ParSer;
import com.mikesamuel.cil.parser.ParSerable;
import com.mikesamuel.cil.parser.ParseErrorReceiver;
import com.mikesamuel.cil.parser.ParseResult;
import com.mikesamuel.cil.parser.ParseState;
import com.mikesamuel.cil.parser.SList;
import com.mikesamuel.cil.parser.SerialErrorReceiver;
import com.mikesamuel.cil.parser.SerialState;
import com.mikesamuel.cil.parser.SourcePosition;
import com.mikesamuel.cil.parser.Unparse;
import com.mikesamuel.cil.ptree.PTree;
import com.mikesamuel.cil.template.Templates;

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
      NodeType<?, ?> nodeType,
      String content, Event... expected) {
    assertParsePasses(
        nodeType.getGrammar(), PTree.complete(nodeType),
        null, content, expected);
  }

  protected void assertParsePasses(
      Grammar<?, ?> g, ParSerable ps,
      String content, Event... expected) {
    assertParsePasses(g, ps, null, content, expected);
  }

  protected void assertParsePasses(
      Grammar<?, ?> g, ParSerable ps,
      @Nullable Set<? super NodeType<?, ?>> relevant,
      String content, Event... expected) {
    assertParsePasses(
        g, ps, content,
        new Predicate<ParseState>() {

          @Override
          public boolean apply(ParseState afterParse) {
            ImmutableList<Event> want = ImmutableList.copyOf(expected);
            ImmutableList<Event> got = filterEvents(
                relevant,
                Templates.postprocess(
                    afterParse.input,
                    SList.forwardIterable(afterParse.output)));
            if (!want.equals(got)) {
              assertEquals(
                  content,
                  Joiner.on("\n").join(want),
                  Joiner.on("\n").join(got));
              fail();
              return false;
            }
            return true;
          }

        });
  }

  protected void assertParseTree(
      NodeType<?, ?> nt, String content,
      String... expectedTreeAscii) {
    assertParseTree(
        nt.getGrammar(), PTree.complete(nt), content, expectedTreeAscii);
  }

  protected void assertParseTree(
      Grammar<?, ?> g, ParSerable ps, String content,
      String... expectedTreeAscii) {
    assertParsePasses(
        g, ps, content,
        new Predicate<ParseState>() {

          @Override
          public boolean apply(ParseState afterParse) {
            ImmutableList<Event> generalized = Templates.postprocess(
                afterParse.input,
                SList.forwardIterable(afterParse.output));
            BaseNode<?, ?, ?> root = Trees.forGrammar(g)
                .of(afterParse.input, generalized);
            String want = Joiner.on('\n').join(expectedTreeAscii);
            String got = root.toAsciiArt("", Functions.constant(null));
            assertEquals(want, got);
            return want.equals(got);
          }

        });
  }

  protected void assertParsePasses(
      Grammar<?, ?> g, ParSerable ps,
      String content, Predicate<ParseState> check) {
    ParseState state = parseState(content);
    LeftRecursion lr = new LeftRecursion();
    ParSer parSer = ps.getParSer();
    ParseResult result = parSer.parse(state, lr, parseErr);
    switch (result.synopsis) {
      case SUCCESS:
        ParseState afterParse = result.next();
        assertTrue(check.apply(afterParse));
        doubleCheck(g, parSer, afterParse, ImmutableSet.of());
        return;
      case FAILURE:
        fail("`" + content + "` does not match " + ps.getParSer()
             + " : " + parseErr.getErrorMessage());
        return;
    }
    throw new AssertionError(result.synopsis);
  }

  /** SanityChecks to skip for a particular test case. */
  public enum Fuzz {
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
      NodeVariant<?, ?> variant, String content, Fuzz... fuzzes) {
    parseSanityCheck(variant, input(content), fuzzes);
  }

  protected void parseSanityCheck(
      NodeVariant<?, ?> variant, Input input, Fuzz... fuzzes) {
    ImmutableSet<Fuzz> fuzzSet = Sets.immutableEnumSet(Arrays.asList(fuzzes));
    Grammar<?, ?> g = variant.getNodeType().getGrammar();

    StringBuilder allTokenText = new StringBuilder();
    ParseState start = new ParseState(input);
    CharSequence inputContent = input.content();
    for (ParseState ps = start; !ps.isEmpty();) {
      char ch = inputContent.charAt(ps.index);
      int nConsumed = 1;
      if (ch == '\'' || ch == '"') {
        int end;
        int limit = inputContent.length();
        for (end = ps.index + 1; end < limit; ++end) {
          char c = inputContent.charAt(end);
          if (c == ch) {
            ++end;
            break;
          } else if (c == '\\') {
            ++end;
          }
        }
        nConsumed = end - ps.index;
      }
      allTokenText.append(inputContent, ps.index, ps.index + nConsumed);
      ps = ps.advance(nConsumed);
    }
    LeftRecursion lr = new LeftRecursion();

    NodeType<?, ?> startNodeType = variant.getNodeType();

    ParSer parSer = PTree.complete(startNodeType).getParSer();
    ParseResult result = parSer.parse(start, lr, parseErr);
    if (!result.lrExclusionsTriggered.isEmpty()) {
      fail("LR failure not recovered from");
    }
    switch (result.synopsis) {
      case SUCCESS:
        StringBuilder tokensOnOutput = new StringBuilder();
        ParseState afterParse = result.next();
        List<NodeVariant<?, ?>> firstPushVariants = Lists.newArrayList();
        boolean sawNonPush = false;
        // Check that pops and pushes match up so that the tree is well-formed.
        int stackDepth = 0;
        ImmutableList<Event> events = Templates.postprocess(
            afterParse.input,
            SList.forwardIterable(afterParse.output));
        for (Event e : events) {
          Event.Kind kind = e.getKind();
          if (kind != Event.Kind.PUSH) {
            sawNonPush = true;
          }
          switch (kind) {
            case PUSH:
              ++stackDepth;
              if (!sawNonPush) {
                firstPushVariants.add(e.getNodeVariant());
              }
              break;
            case POP:  // TODO: default
              if (stackDepth == 0) {
                fail(
                    "Parsing `" + inputContent
                    + "`, depth goes negative after `" + tokensOnOutput + "`");
              }
              --stackDepth;
              break;
            case CONTENT: case TOKEN:
              tokensOnOutput.append(e.getContent());
              break;
            case IGNORABLE:
            case POSITION_MARK:
              break;
            case DELAYED_CHECK:
            case LR_END:
            case LR_START:
              fail("Unprocessed event " + e);
              break;
          }
        }

        // Trees.of will throw an IllegalArgumentException if its
        // well-formedness checks fail.
        BaseNode<?, ?, ?> node = Trees.forGrammar(g)
            .of(start.input, afterParse.output);

        if (firstPushVariants.isEmpty()) {
          fail("Variant never pushed");
        } else if (!fuzzSet.contains(Fuzz.SAME_VARIANT)) {
          assertEquals(
              inputContent.toString(), variant, firstPushVariants.get(0));
          if (!variant.isAnon()) {
            assertEquals(variant, node.getVariant());
          }
        }
        assertEquals(
            inputContent.toString(),
            allTokenText.toString(), tokensOnOutput.toString());
        doubleCheck(g, parSer, afterParse, fuzzSet);
        return;
      case FAILURE:
        fail(
            "`" + inputContent + "` does not match " + variant + "\n"
                + parseErr.getErrorMessage());
        return;
    }
    throw new AssertionError(result.synopsis);
  }

  protected static ImmutableList<Event> filterEvents(
      @Nullable Set<? super NodeType<?, ?>> relevant,
      Iterable<? extends Event> events) {
    int depth = 0;
    BitSet included = new BitSet();  // Per depth, whether to include the pop
    ImmutableList.Builder<Event> b = ImmutableList.builder();
    for (Event e : events) {
      switch (e.getKind()) {
        case CONTENT:
        case DELAYED_CHECK:
        case IGNORABLE:
        case LR_END:
        case LR_START:
        case POSITION_MARK:
        case TOKEN:
          b.add(e);
          break;
        case POP:
          Preconditions.checkState(depth >= 0);
          --depth;
          if (included.get(depth)) {
            b.add(e);
          }
          break;
        case PUSH:
          NodeVariant<?, ?> pushVariant = e.getNodeVariant();
          boolean pushRelevant = !pushVariant.isAnon()
              && (relevant == null
                  || relevant.contains(pushVariant.getNodeType()));
          included.set(depth, pushRelevant);
          if (pushRelevant) {
            b.add(e);
          }
          ++depth;
          break;
      }
    }
    Preconditions.checkState(depth == 0);
    return b.build();
  }


  private static final boolean DEBUG_DOUBLE_CHECK = false;

  protected void doubleCheck(
      Grammar<?, ?> g, ParSer parSer, ParseState afterParse,
      ImmutableSet<Fuzz> fuzzSet) {
    BaseNode<?, ?, ?> root = Trees.forGrammar(g)
        .of(afterParse.input, afterParse.output);
    if (DEBUG_DOUBLE_CHECK) {
      System.err.println("root=" + root);
    }

    ImmutableList<Event> structure = ImmutableList.copyOf(
        SList.forwardIterable(Trees.startUnparse(null, root, null)));
    if (DEBUG_DOUBLE_CHECK) {
      System.err.println("\nstructure\n=======");
      Debug.dumpEvents(structure);
    }

    ParSer unparser = parSer;
    if (root.getNodeType() == J8NodeType.TemplatePseudoRoot) {
      // Pseudo roots are synthesized by the , so we can't use the
      unparser = PTree.complete(J8NodeType.TemplatePseudoRoot).getParSer();
      // TODO: We should really confirm that parSer is complete(CompilationUnit)
    }

    SerialState beforeRoot = new SerialState(structure);
    Optional<SerialState> afterRoot = unparser.unparse(beforeRoot, serialErr);
    if (!afterRoot.isPresent()) {
      fail(
          "Failed to unparse: " + serialErr.getErrorMessage()
          + "\n\t" + structure);
    }
    if (DEBUG_DOUBLE_CHECK) {
      System.err.println("afterRoot\n=========");
      Debug.dumpEvents(SList.forwardIterable(afterRoot.get().output));
    }

    Unparse.Verified verified;
    try {
      verified = Unparse.verify(
          SList.forwardIterable(afterRoot.get().output));
    } catch (Unparse.UnparseVerificationException ex) {
      throw (AssertionFailedError)
         new AssertionFailedError(getName()).initCause(ex);
    }
    if (DEBUG_DOUBLE_CHECK) {
      System.err.println("Verified\n=========");
      Debug.dumpEvents(verified.events);
    }

    FormattedSource formattedSource = Unparse.format(verified);
    Input input = input(formattedSource.code);
    LatestParseErrorReceiver reparseErr = new LatestParseErrorReceiver();
    ParseResult reparse = parSer.parse(
        new ParseState(input), new LeftRecursion(), reparseErr);
    switch (reparse.synopsis) {
      case FAILURE:
        fail("Reparse of `" + input.content() + "` failed: "
             + reparseErr.getErrorMessage());
        break;
      case SUCCESS:
        ParseState reparseState = reparse.next();
        ImmutableList<Event> reparsedEvents =
            ImmutableList.copyOf(
                Iterables.filter(
                    SList.forwardIterable(reparseState.output),
                    new Predicate<Event>() {

                      @Override
                      public boolean apply(Event e) {
                        return e.getKind() != Event.Kind.POSITION_MARK;
                      }
                    }));
        ImmutableList<Event> afterParseEvents =
            ImmutableList.copyOf(SList.forwardIterable(afterParse.output));
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

        BaseNode<?, ?, ?> reparsedRoot = Trees.forGrammar(g)
            .of(reparseState.input, reparseState.output);
        if (!root.equals(reparsedRoot)) {
          assertEquals(root.toString(), reparsedRoot.toString());
          assertEquals(root, reparsedRoot);
        }
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
        ImmutableList<Event> got =
            ImmutableList.copyOf(SList.forwardIterable(afterParse.output));
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
    return Input.builder().source(getName()).code(content).build();
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
      SourcePosition pos = latest.input.getSourcePosition(index);
      int ln = pos.startLineInFile();
      int co = pos.startCharInFile();
      return pos.getSource() + ":" + ln + ":" + co + ": " + latestMessage;
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
