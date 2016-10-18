package com.mikesamuel.cil.ast;

/**
 * Base type for leaf nodes that represent simple tokens like Identifiers and
 * value literals.
 */
public abstract class BaseLiteralNode extends BaseNode {

  BaseLiteralNode(
      NodeType type, NodeVariant variant,
      Iterable<? extends BaseNode> children, String value) {
    super(type, variant, children, value);
    // TODO Auto-generated constructor stub
  }

}
