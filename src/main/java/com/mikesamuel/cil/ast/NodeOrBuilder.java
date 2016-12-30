package com.mikesamuel.cil.ast;

import com.google.common.collect.ImmutableList;

/**
 * An interface that allows examination of the content of a node.
 */
public interface NodeOrBuilder {

  /**
   * The variant of the node.
   */
  NodeVariant getVariant();

  /**
   * The type of node.
   */
  default NodeType getNodeType() {
    return getVariant().getNodeType();
  }

  /**
   * The children of this node.  Empty if a leaf.
   */
  ImmutableList<BaseNode> getChildren();

  /**
   * The value if any.  {@code null} if an inner node.
   */
  String getValue();

  /**
   * A builder that currently has the state of this node allowing modification.
   */
  BaseNode.Builder<?, ?> builder();
}
// TODO: Maybe make all traits extend this interface.
