package com.mikesamuel.cil.parser;

import java.util.List;

import javax.annotation.Nullable;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.mikesamuel.cil.ast.NodeVariant;
import com.mikesamuel.cil.event.DelayedCheckPredicate;
import com.mikesamuel.cil.event.Event;
import com.mikesamuel.cil.format.FormattedSource;
import com.mikesamuel.cil.format.Formatter;
import com.mikesamuel.cil.format.java.Java8Formatters;

/**
 * Allows converting the output of an {@link ParSer#unparse} operations to
 * source.
 * <p>
 * This happens in several phases.
 * <ol>
 *   <li>Run {@link Event#delayedCheck}s.
 *   <li>Extract token events and format them.
 * </ol>
 * <p>
 * The formatting produces two outputs.
 * <ol>
 *   <li>The formatted source code.
 *   <li>A mapping between {@link SourcePosition}s in the input tree and indices
 *      in the formatted source.
 * </ol>
 */
public final class Unparse {

  /**
   * @param unverified the full unparsed output with
   *     {@link Event#delayedCheck}s.
   * @return the verified output which consists only of position and
   *     token/content events.
   */
  public static Verified verify(Iterable<? extends Event> unverified)
  throws UnparseVerificationException {
    List<Object> delayedAndIndices = Lists.newArrayList();
    StringBuilder sb = new StringBuilder();
    List<Event> verifiedTokens = Lists.newArrayList();
    for (Event e : unverified) {
      switch (e.getKind()) {
        case TOKEN: case CONTENT:
          sb.append(e.getContent());
          // Add the space here instead of before the token so that indices
          // added below always correspond to the beginning of a token or the
          // end of input.
          sb.append(' ');

          verifiedTokens.add(e);
          break;
        case IGNORABLE:
          String commentContent = e.getContent();
          if (commentContent.length() == Ignorables.scanPastIgnorablesFrom(
                  commentContent, 0, null)) {
            sb.append(commentContent).append(' ');
            verifiedTokens.add(e);
          }
          break;
        case DELAYED_CHECK:
          delayedAndIndices.add(e);
          delayedAndIndices.add(sb.length());
          break;
        case POSITION_MARK:
          int n = verifiedTokens.size();
          if (n != 0
              && (verifiedTokens.get(n - 1).getKind()
                  == Event.Kind.POSITION_MARK)) {
            verifiedTokens.set(n - 1, e);
          } else {
            verifiedTokens.add(e);
          }
          break;
        case PUSH: case POP:
          verifiedTokens.add(e);
          break;
        case LR_END:
        case LR_START:
          throw new AssertionError(e.toString());
      }
    }

    Input inp = Input.builder().source("UnparseVerifier").code(sb).build();
    final ParseState ps = new ParseState(inp);

    for (int i = 0, n = delayedAndIndices.size(); i < n; i += 2) {
      DelayedCheckPredicate p = ((Event) delayedAndIndices.get(i))
          .getDelayedCheck();
      final int index = (Integer) delayedAndIndices.get(i + 1);
      Suffix s = new Suffix() {

        @Override
        public ParseState asParseState() {
          // It's that index occurs before an ignorable token produced by a
          // Trees.Decorator so advance index over any ignorable tokens.
          int indexAfterIgnorables = Ignorables.scanPastIgnorablesFrom(
              ps.input.content(), index, null);
          return ps.withIndex(indexAfterIgnorables);
        }

      };
      Optional<String> problem = p.problem(s);
      if (problem.isPresent()) {
        throw new UnparseVerificationException(
            "Delayed check " + p + " failed at " + index
            + " because " + problem.get() + " : " + s.asParseState(),
            null);
      }
    }

    @SuppressWarnings("synthetic-access")
    Verified v = new Verified(ImmutableList.copyOf(verifiedTokens));
    return v;
  }

  /**
   * Formats a verified output producing the formatted source code and an
   * input source position -> output source position mapping.
   */
  public static FormattedSource format(Verified v) {
    return format(v, Java8Formatters.createFormatter());
  }

  /**
   * Formats a verified output producing the formatted source code and an
   * input source position -> output source position mapping.
   */
  public static FormattedSource format(
      Verified v, Formatter<SList<NodeVariant<?, ?>>> f) {
    SList<NodeVariant<?, ?>> contextStack = null;
    for (Event e : v.events) {
      switch (e.getKind()) {
        case CONTENT: case TOKEN:
        case IGNORABLE:
          f.token(e.getContent());
          break;
        case POSITION_MARK:
          f.sourcePosition(e.getSourcePosition());
          break;
        case PUSH:
          contextStack = SList.append(contextStack, e.getNodeVariant());
          f.context(contextStack);
          break;
        case POP:
          contextStack = Preconditions.checkNotNull(contextStack).prev;
          f.context(contextStack);
          break;
        case DELAYED_CHECK:
        case LR_END:
        case LR_START:
          throw new AssertionError(e.toString());
      }
    }

    return f.format();
  }


  /**
   * A series of tokens and source positions without any delayed checks.
   */
  public static final class Verified {
    /** Position, token, and content events. */
    public final ImmutableList<Event> events;

    private Verified(ImmutableList<Event> events) {
      this.events = events;
    }
  }

  /** A suffix of the unparsed token stream consisting of complete tokens. */
  public interface Suffix {
    /**
     * A parse input with the suffix tokens and only the suffix tokens following
     * the input cursor.
     */
    ParseState asParseState();
  }


  /**
   * Thrown when a delayed check fails.
   */
  public static final class UnparseVerificationException extends Exception {
    private static final long serialVersionUID = 1L;

    /** */
    public UnparseVerificationException(
        @Nullable String message, @Nullable Throwable cause) {
      super(message, cause);
    }
  }
}
