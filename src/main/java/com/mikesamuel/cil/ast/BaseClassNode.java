package com.mikesamuel.cil.ast;

/**
 * Base class for nodes that are directly involved in a
 * {@code class} declaration.
 */
public abstract class BaseClassNode extends BaseNode {

  BaseClassNode(
      Iterable<? extends NodeVariant> anonVariants, NodeVariant variant,
      Iterable<? extends BaseNode> children, String value) {
    super(anonVariants, variant, children, value);
  }

}
