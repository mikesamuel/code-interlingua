package com.mikesamuel.cil.ast;

import javax.annotation.Nullable;

/**
 * Base class for nodes primarily concerned with package-level declarations.
 */
public abstract class BasePackageNode extends BaseNode {

  BasePackageNode(
      NodeVariant variant,
      Iterable<? extends BaseNode> children, @Nullable String value) {
    super(variant, children, value);
  }

}
