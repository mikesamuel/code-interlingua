package com.mikesamuel.cil.ast;

/**
 * Base type for nodes that map to sequences of byte-code instructions that
 * produce values or side effects.
 */
public abstract class BaseExpressionNode extends BaseNode {

  BaseExpressionNode(
      NodeVariant variant,
      Iterable<? extends BaseNode> children, String value) {
    super(variant, children, value);
  }
}
