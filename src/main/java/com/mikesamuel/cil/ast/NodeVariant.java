package com.mikesamuel.cil.ast;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.mikesamuel.cil.event.MatchEvent;
import com.mikesamuel.cil.parser.Chain;
import com.mikesamuel.cil.parser.Lookahead1;
import com.mikesamuel.cil.parser.ParSerable;

/**
 * Base type for an enum of variants of a node type.
 * <p>
 * The JLS grammar is specified in disjunction of concatenations style --
 * instead of using a first-class OR operator, each production has a number of
 * variants.
 */
public interface NodeVariant extends ParSerable {
  /** Such that <code>
   * {@link #getNodeType()}.{@link NodeType#getVariantType() getVariantType()}
   * .isInstance(this)
   * </code>
   */
  NodeType getNodeType();

  /**
   * True if the variant might be left-recursive.
   */
  boolean isLeftRecursive();

  /** @see {Enum#name()}. */
  String name();

  /**
   * The characters that might appear immediately after the input cursor
   * when this variant matches a prefix of the input.
   *
   * @return null if this variant might match the empty string, so no
   *     character is required at the input cursor for this variant to match.
   */
  Lookahead1 getLookahead1();

  /**
   * A builder that produces nodes with this variant.
   *
   * @return a builder, b,  such that {@code b.build().getVariant() == this}.
   */
  BaseNode.Builder<?, ?> nodeBuilder();

  /**
   * True iff the variant is anonymous meaning that parsing using it should not
   * result in {@linkplain MatchEvent#push push}/{@linkplain MatchEvent#pop pop}
   * events.
   */
  default boolean isAnon() {
    return false;
  }

  /**
   * A post-condition that is applied to the output after parsing is completed.
   */
  default Predicate<Chain<MatchEvent>> getPostcond() {
    return Predicates.alwaysTrue();
  }

  /**
   * True if the content has no semantic value.
   */
  default boolean isIgnorable() {
    return false;
  }
}
