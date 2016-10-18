package com.mikesamuel.cil.ast;

/**
 * Base class for a node that represents a type or part of a type.
 */
public abstract class BaseTypeNode extends BaseNode {

  BaseTypeNode(
      NodeType type, NodeVariant variant,
      Iterable<? extends BaseNode> children, String value) {
    super(type, variant, children, value);
  }

}
