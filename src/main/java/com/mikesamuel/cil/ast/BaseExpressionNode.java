package com.mikesamuel.cil.ast;

/**
 * Base type for nodes that map to sequences of byte-code instructions that
 * produce values or side effects.
 */
public abstract class BaseExpressionNode extends BaseNode {

  BaseExpressionNode(
      NodeType type, NodeVariant variant,
      Iterable<? extends BaseNode> children, String value) {
    super(type, variant, children, value);
  }
}
