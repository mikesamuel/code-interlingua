package com.mikesamuel.cil.ast;

/**
 * Base class for a node that represents a name that can be resolved to
 * a referent within a scope.
 */
public abstract class BaseNameNode extends BaseNode {

  BaseNameNode(
      Iterable<? extends NodeVariant> anonVariants, NodeVariant variant,
      Iterable<? extends BaseNode> children, String value) {
    super(anonVariants, variant, children, value);
  }

}
