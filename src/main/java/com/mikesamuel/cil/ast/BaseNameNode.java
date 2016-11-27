package com.mikesamuel.cil.ast;

import javax.annotation.Nullable;

/**
 * Base class for a node that represents a name that can be resolved to
 * a referent within a scope.
 */
public abstract class BaseNameNode extends BaseNode {

  BaseNameNode(
      NodeVariant variant,
      Iterable<? extends BaseNode> children, @Nullable String value) {
    super(variant, children, value);
  }

}
