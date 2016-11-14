package com.mikesamuel.cil.ast;

/**
 * Base class for nodes primarily concerned with package-level declarations.
 */
public abstract class BasePackageNode extends BaseNode {

  BasePackageNode(
      Iterable<? extends NodeVariant> anonVariants, NodeVariant variant,
      Iterable<? extends BaseNode> children, String value) {
    super(anonVariants, variant, children, value);
  }

}
