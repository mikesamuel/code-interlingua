package com.mikesamuel.cil.ast;

/**
 * Base class for a node primarily concerned with flow of control within a
 * program.
 */
public abstract class BaseStatementNode extends BaseNode {

  BaseStatementNode(
      NodeType type, NodeVariant variant,
      Iterable<? extends BaseNode> children, String value) {
    super(type, variant, children, value);
    // TODO Auto-generated constructor stub
  }

}
