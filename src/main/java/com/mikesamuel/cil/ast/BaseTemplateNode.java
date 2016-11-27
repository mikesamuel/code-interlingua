package com.mikesamuel.cil.ast;

/**
 * Base type for leaf nodes that represent simple tokens like Identifiers and
 * value literals.
 */
public abstract class BaseTemplateNode extends BaseNode {

  BaseTemplateNode(
      NodeVariant variant,
      Iterable<? extends BaseNode> children, String value) {
    super(variant, children, value);
  }

}
