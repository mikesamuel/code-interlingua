package com.mikesamuel.cil.ast;

/**
 * Base class for a node directly involved in an {@code interface} declaration
 * which includes annotation declarations.
 */
public abstract class BaseInterfaceNode extends BaseNode {

  BaseInterfaceNode(
      NodeType type, NodeVariant variant,
      Iterable<? extends BaseNode> children, String value) {
    super(type, variant, children, value);
  }

}
