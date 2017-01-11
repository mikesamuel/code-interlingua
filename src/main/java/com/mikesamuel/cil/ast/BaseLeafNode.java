package com.mikesamuel.cil.ast;

import java.util.Collections;
import java.util.List;

import com.google.common.base.Preconditions;

/**
 * A node that corresponds to a literal token.
 */
public abstract class BaseLeafNode extends BaseNode {
  private String value;

  BaseLeafNode(NodeVariant variant, String literalValue) {
    super(variant);
    this.value = Preconditions.checkNotNull(literalValue);
  }

  @Override
  public final String getValue() {
    return value;
  }

  @Override
  public final int getNChildren() {
    return 0;
  }

  @Override
  public final BaseNode getChild(int i) {
    throw new IndexOutOfBoundsException("" + i);
  }

  @Override
  public final List<BaseNode> getChildren() {
    return Collections.emptyList();
  }


  // MUTATORS
  /** Mutator */
  public void setValue(String newValue) {
    this.value = Preconditions.checkNotNull(newValue);
  }
}


