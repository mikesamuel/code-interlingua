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
  /** LR exclusions whose failure contributed to this result. */
  public final ImmutableSet<NodeType> lrExclusionsTriggered;

  private ParseResult(
      Synopsis synopsis, ImmutableSet<NodeType> lrExclusionsTriggered) {
    this.synopsis = synopsis;
    this.lrExclusionsTriggered = lrExclusionsTriggered;
  }

  /** A summary of the result. */
  public enum Synopsis {
    /**
     * Failed but not due to an LR exclusion so the failure can be cached in
     * the {@link RatPack}.
     */
    FAILURE,

    /**
     * Failed in part due to a LR exclusion.
     * @see LeftRecursion#stageForProductionAt
     */
    FAILURE_DUE_TO_LR_EXCLUSION,

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
      return success(a.next(), allLrExcl);
    } else {
      return failure(allLrExcl);
    }
  }

  @Override
  public String toString() {
    return synopsis.name();
  }

  private static final ParseResult FAILURE_INSTANCE =
      new ParseResult(Synopsis.FAILURE, ImmutableSet.of());

  /** @see Synopsis#FAILURE */
  public static ParseResult failure() {
    return FAILURE_INSTANCE;
  }

  /** @see Synopsis#FAILURE_DUE_TO_LR_EXCLUSION */
  public static ParseResult failure(Set<NodeType> exclusionsTriggered) {
    if (exclusionsTriggered.isEmpty()) {
      return FAILURE_INSTANCE;
    }
    return new ParseResult(
        Synopsis.FAILURE_DUE_TO_LR_EXCLUSION,
        Sets.immutableEnumSet(exclusionsTriggered));
  }

  /** @see Synopsis#SUCCESS */
  public static ParseResult success(
      ParseState after, Iterable<NodeType> lrExclusionsTriggered) {
    return new Pass(after, lrExclusionsTriggered);
  }


  private static final class Pass extends ParseResult {
    final ParseState next;

    @SuppressWarnings("synthetic-access")
    Pass(ParseState next, Iterable<NodeType> lrExclusionsTriggereed) {
      super(Synopsis.SUCCESS, Sets.immutableEnumSet(lrExclusionsTriggereed));
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