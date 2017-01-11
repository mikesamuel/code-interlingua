package com.mikesamuel.cil.ast;

import javax.annotation.Nullable;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.mikesamuel.cil.event.Event;
import com.mikesamuel.cil.parser.SList;
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
   * Produces a new node with this variant.
   *
   * @param value the built nodes initial value.
   * @return a leaf node, n,  such that {@code n.getVariant() == this}.
   * @throws IllegalArgumentException if trying to build an inner node with a
   *     leaf value.
   */
  default BaseLeafNode buildNode(String value) {
    // TODO: maybe have two variants of NodeVariant for leaf/inner
    throw new IllegalArgumentException(this + " is an inner node type");
  }

  /**
   * Produces a new node with this variant.
   *
   * @param children the built nodes initial children.
   * @return an inner node, n,  such that {@code n.getVariant() == this}.
   * @throws IllegalArgumentException if trying to build an inner node with a
   *     leaf value.
   */
  default BaseInnerNode buildNode(Iterable<? extends BaseNode> children) {
    // TODO: maybe have two variants of NodeVariant for leaf/inner
    throw new IllegalArgumentException(this + " is a leaf node type");
  }

  /**
   * True iff the variant is anonymous meaning that the tree builder should
   * ignore push/pop events corresponding to it and not create an AST node.
   */
  default boolean isAnon() {
    return false;
  }

  /**
   * A post-condition that is applied to the output after parsing is completed.
   */
  default Predicate<SList<Event>> getPostcond() {
    return Predicates.alwaysTrue();
  }

  /**
   * True if the content has no semantic value.
   */
  default boolean isIgnorable() {
    return false;
  }

  /**
   * For {@literal @intermediate} nodes, the sole non-terminal that can be
   * contained.
   */
  default @Nullable NodeType getDelegate() {
    return null;
  }
}
