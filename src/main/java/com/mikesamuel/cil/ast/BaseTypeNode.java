package com.mikesamuel.cil.ast;

/**
 * Base class for a node that represents a type or part of a type.
 */
public abstract class BaseTypeNode extends BaseNode {

  BaseTypeNode(
      Iterable<? extends NodeVariant> anonVariants, NodeVariant variant,
      Iterable<? extends BaseNode> children, String value) {
    super(anonVariants, variant, children, value);
  }

}
