package com.mikesamuel.cil.ast;

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
}
