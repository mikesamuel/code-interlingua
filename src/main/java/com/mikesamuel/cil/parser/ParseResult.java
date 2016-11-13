package com.mikesamuel.cil.parser;

import java.util.EnumSet;
import java.util.Set;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.mikesamuel.cil.ast.NodeType;

/**
 * The result of a parse operation.
 */
public class ParseResult {
  /** A summary of the result. */
  public final Synopsis synopsis;
  /**
   * If there is a {@link #next} state, then this is an index such that
   * all events produced by parsing input up to the index on the start state
   * also appear as a prefix of the events on the next state.
   * <p>
   * If there is no next state or the start state events are a prefix of the
   * next state events, then {@link #NO_WRITE_BACK_RESTRICTION} is appropriate.
   */
  public final int writeBack;
  /** LR exclusions whose failure contributed to this result. */
  public final ImmutableSet<NodeType> lrExclusionsTriggered;

  private ParseResult(
      Synopsis synopsis, int writeBack,
      ImmutableSet<NodeType> lrExclusionsTriggered) {
    this.synopsis = synopsis;
    this.writeBack = writeBack;
    this.lrExclusionsTriggered = lrExclusionsTriggered;
  }

  /** A summary of the result. */
  public enum Synopsis {
    /**
     * Failed but not due to an LR exclusion so the failure can be cached in
     * the {@link RatPack}.
     */
    FAILURE,

    /** Indicates the parse operation passed and there is a next state. */
    SUCCESS,
    ;
  }

  /**
   * The state after the parse operation.
   *
   * @throws IllegalStateException if {@link #synopsis} is not
   *     {@link Synopsis#SUCCESS}.
   */
  public ParseState next() {
    throw new IllegalStateException(synopsis.name());
  }

  /**
   * The most extreme failure mode.
   * We use the kind of failure to decide whether to
   */
  public static ParseResult mostEpicFail(ParseResult a, ParseResult b) {
    Preconditions.checkNotNull(a);
    Preconditions.checkNotNull(b);

    ImmutableSet<NodeType> allLrExcl = union(
        a.lrExclusionsTriggered, b.lrExclusionsTriggered);

    boolean success =
        a.synopsis == b.synopsis && a.synopsis == Synopsis.SUCCESS;
    if (success) {
      return success(
          a.next(),
          Math.min(a.writeBack, b.writeBack),
          allLrExcl);
    } else {
      return failure(allLrExcl);
    }
  }

  @Override
  public String toString() {
    return synopsis.name();
  }

  /**
   * {@link Integer#MAX_VALUE} per {@link ParseResult#writeBack}.
   */
  public static final int NO_WRITE_BACK_RESTRICTION = Integer.MAX_VALUE;

  private static final ParseResult FAILURE_INSTANCE = new ParseResult(
      Synopsis.FAILURE, NO_WRITE_BACK_RESTRICTION, ImmutableSet.of());

  /** @see Synopsis#FAILURE */
  public static ParseResult failure() {
    return FAILURE_INSTANCE;
  }

  /** @see Synopsis#FAILURE */
  public static ParseResult failure(Set<NodeType> exclusionsTriggered) {
    if (exclusionsTriggered.isEmpty()) {
      return FAILURE_INSTANCE;
    }
    return new ParseResult(
        Synopsis.FAILURE, NO_WRITE_BACK_RESTRICTION,
        Sets.immutableEnumSet(exclusionsTriggered));
  }

  /**
   * @param wroteBack per {@link ParseResult#writeBack}.
   * @see Synopsis#SUCCESS
   */
  public static ParseResult success(
      ParseState after, int wroteBack,
      Iterable<NodeType> lrExclusionsTriggered) {
    return new Pass(after, wroteBack, lrExclusionsTriggered);
  }


  private static final class Pass extends ParseResult {
    final ParseState next;

    @SuppressWarnings("synthetic-access")
    Pass(
        ParseState next, int wroteBack,
        Iterable<NodeType> lrExclusionsTriggereed) {
      super(
          Synopsis.SUCCESS, wroteBack,
          Sets.immutableEnumSet(lrExclusionsTriggereed));
      this.next = Preconditions.checkNotNull(next);
    }

    @Override
    public ParseState next() {
      return next;
    }
  }

  /** Convenience for unioning two sets of enum values. */
  public static <E extends Enum<E>>
  ImmutableSet<E> union(ImmutableSet<E> a, ImmutableSet<E> b) {
    if (!a.isEmpty()) {
      if (!b.isEmpty()) {
        EnumSet<E> u = EnumSet.copyOf(a);
        u.addAll(b);
        if (u.size() == a.size()) { return a; }
        if (u.size() == b.size()) { return b; }
        return Sets.immutableEnumSet(u);
      }
      return a;
    } else if (!b.isEmpty()) {
      return b;
    }
    return a;
  }
}
