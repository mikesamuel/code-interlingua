package com.mikesamuel.cil.ast;

/**
 * Base type for leaf nodes that represent simple tokens like Identifiers and
 * value literals.
 */
public abstract class BaseLiteralNode extends BaseNode {

  BaseLiteralNode(
      Iterable<? extends NodeVariant> anonVariants, NodeVariant variant,
      Iterable<? extends BaseNode> children, String value) {
    super(anonVariants, variant, children, value);
  }

}
