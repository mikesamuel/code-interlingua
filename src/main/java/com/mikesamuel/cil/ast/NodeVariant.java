package com.mikesamuel.cil.ast;

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
}
