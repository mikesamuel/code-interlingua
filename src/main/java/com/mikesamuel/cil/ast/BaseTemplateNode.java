package com.mikesamuel.cil.ast;

import javax.annotation.Nullable;

/**
 * Base type for leaf nodes that represent simple tokens like Identifiers and
 * value literals.
 */
public abstract class BaseTemplateNode extends BaseNode {

  BaseTemplateNode(
      NodeVariant variant,
      Iterable<? extends BaseNode> children, @Nullable String value) {
    super(variant, children, value);
  }

}
