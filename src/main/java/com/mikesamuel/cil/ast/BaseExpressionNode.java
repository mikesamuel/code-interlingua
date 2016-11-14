package com.mikesamuel.cil.ast;

/**
 * Base type for nodes that map to sequences of byte-code instructions that
 * produce values or side effects.
 */
public abstract class BaseExpressionNode extends BaseNode {

  BaseExpressionNode(
      Iterable<? extends NodeVariant> anonVariants, NodeVariant variant,
      Iterable<? extends BaseNode> children,
      String value) {
    super(anonVariants, variant, children, value);
  }
}
