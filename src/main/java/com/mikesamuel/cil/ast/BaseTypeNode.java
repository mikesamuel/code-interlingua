package com.mikesamuel.cil.ast;

import javax.annotation.Nullable;

/**
 * Base class for a node that represents a type or part of a type.
 */
public abstract class BaseTypeNode extends BaseNode {

  BaseTypeNode(
      NodeVariant variant,
      Iterable<? extends BaseNode> children, @Nullable String value) {
    super(variant, children, value);
  }

}
