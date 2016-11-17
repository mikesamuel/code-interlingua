package com.mikesamuel.cil.parser;

import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.mikesamuel.cil.ast.MatchEvent;
import com.mikesamuel.cil.ast.NodeVariant;
import com.mikesamuel.cil.format.FormattedSource;
import com.mikesamuel.cil.format.Formatter;
import com.mikesamuel.cil.format.java.Java8Formatters;

/**
 * Allows converting the output of an {@link ParSer#unparse} operations to
 * source.
 * <p>
 * This happens in several phases.
 * <ol>
 *   <li>Run {@link MatchEvent#delayedCheck}s.
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
   *     {@link MatchEvent#delayedCheck}s.
   * @return the verified output which consists only of position and
   *     token/content events.
   */
  public static Verified verify(Iterable<? extends MatchEvent> unverified)
  throws UnparseVerificationException {
    List<Object> delayedAndIndices = Lists.newArrayList();
    StringBuilder sb = new StringBuilder();
    List<MatchEvent> verifiedTokens = Lists.newArrayList();
    for (MatchEvent e : unverified) {
      if (e.nCharsConsumed() != 0) {
        if (e instanceof MatchEvent.Token) {
          sb.append(((MatchEvent.Token) e).content);
        } else {
          sb.append(((MatchEvent.Content) e).content);
        }
        verifiedTokens.add(e);
        // Add the space here instead of before the token so that indices added
        // below always correspond to the beginning of a token or the end of
        // input.
        sb.append(' ');
      } else if (e instanceof MatchEvent.DelayedCheck) {
        delayedAndIndices.add(e);
        delayedAndIndices.add(sb.length());
      } else if (e instanceof MatchEvent.SourcePositionMark) {
        int n = verifiedTokens.size();
        if (n != 0
            && verifiedTokens.get(n - 1)
               instanceof MatchEvent.SourcePositionMark) {
          verifiedTokens.set(n - 1, e);
        } else {
          verifiedTokens.add(e);
        }
      } else if (e instanceof MatchEvent.Push
                 || e instanceof MatchEvent.Pop) {
        verifiedTokens.add(e);
      }
    }

    Input inp = Input.fromCharSequence("UnparseVerifier", sb);
    final ParseState ps = new ParseState(inp);

    for (int i = 0, n = delayedAndIndices.size(); i < n; i += 2) {
      Predicate<Suffix> p =
          ((MatchEvent.DelayedCheck) delayedAndIndices.get(i)).p;
      final int index = (Integer) delayedAndIndices.get(i + 1);
      Suffix s = new Suffix() {

        @Override
        public ParseState asParseState() {
          return ps.withIndex(index);
        }

      };
      if (!p.apply(s)) {
        throw new UnparseVerificationException(
            "Delayed check " + p + " failed at " + index
            + " : " + s.asParseState(),
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
      Verified v, Formatter<Chain<NodeVariant>> f) {
    Chain<NodeVariant> contextStack = null;
    for (MatchEvent e : v.events) {
      if (e instanceof MatchEvent.Token) {
        f.token(((MatchEvent.Token) e).content);
      } else if (e instanceof MatchEvent.Content) {
        f.token(((MatchEvent.Content) e).content);
      } else if (e instanceof MatchEvent.SourcePositionMark) {
        f.sourcePosition(((MatchEvent.SourcePositionMark) e).pos);
      } else if (e instanceof MatchEvent.Push) {
        contextStack = Chain.append(
            contextStack, ((MatchEvent.Push) e).variant);
        f.context(contextStack);
      } else if (e instanceof MatchEvent.Pop) {
        contextStack = Preconditions.checkNotNull(contextStack).prev;
        f.context(contextStack);
      }
    }

    return f.format(100);
  }


  /**
   * A series of tokens and source positions without any delayed checks.
   */
  public static final class Verified {
    /** Position, token, and content events. */
    public final ImmutableList<MatchEvent> events;

    private Verified(ImmutableList<MatchEvent> events) {
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
    public UnparseVerificationException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
