package com.mikesamuel.cil.ast;

import javax.annotation.Nullable;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.mikesamuel.cil.event.Event;
import com.mikesamuel.cil.parser.ParSerable;
import com.mikesamuel.cil.parser.SList;

/**
 * Base type for an enum of variants of a node type.
 * <p>
 * The JLS grammar is specified in disjunction of concatenations style --
 * instead of using a first-class OR operator, each production has a number of
 * variants.
 */
public interface NodeVariant<
    BASE_NODE extends BaseNode<BASE_NODE, NODE_TYPE, ?>,
    NODE_TYPE extends Enum<NODE_TYPE> & NodeType<BASE_NODE, NODE_TYPE>>
extends ParSerable {
  /** Such that <code>
   * {@link #getNodeType()}.{@link NodeType#getVariantType() getVariantType()}
   * .isInstance(this)
   * </code>
   */
  NODE_TYPE getNodeType();

  /** @see {Enum#name()}. */
  String name();

  /**
   * Produces a new node with this variant.
   *
   * @param value the built nodes initial value.
   * @return a leaf node, n,  such that {@code n.getVariant() == this}.
   * @throws IllegalArgumentException if trying to build an inner node with a
   *     leaf value.
   */
  LeafNode<BASE_NODE, NODE_TYPE, ?> buildNode(String value);
  // TODO: maybe have two variants of NodeVariant for leaf/inner

  /**
   * Produces a new node with this variant.
   *
   * @param children the built nodes initial children.
   * @return an inner node, n,  such that {@code n.getVariant() == this}.
   * @throws IllegalArgumentException if trying to build an inner node with a
   *     leaf value.
   */
  InnerNode<BASE_NODE, NODE_TYPE, ?> buildNode(
      Iterable<? extends BASE_NODE> children);
  // TODO: maybe have two variants of NodeVariant for leaf/inner

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
  default @Nullable NODE_TYPE getDelegate() {
    return null;
  }

  /**
   * True iff it starts a template scope.
   */
  default boolean isTemplateStart() {
    return false;
  }

  /**
   * True iff it ends a template scope.
   */
  default boolean isTemplateEnd() {
    return false;
  }
}
