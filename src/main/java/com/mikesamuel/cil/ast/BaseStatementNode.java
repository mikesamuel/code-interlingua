package com.mikesamuel.cil.ast;

/**
 * Base class for a node primarily concerned with flow of control within a
 * program.
 */
public abstract class BaseStatementNode extends BaseNode {

  BaseStatementNode(
      NodeVariant variant,
      Iterable<? extends BaseNode> children, String value) {
    super(variant, children, value);
  }

}
