package com.mikesamuel.cil.ast;

import java.util.Collections;
import java.util.List;

import javax.annotation.Nullable;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

/**
 * A node that may have children.
 */
public abstract class BaseInnerNode extends BaseNode {
  private final List<BaseNode> children = Lists.newArrayList();

  BaseInnerNode(NodeVariant variant, Iterable<? extends BaseNode> children) {
    super(variant);
    for (BaseNode child : children) {
      this.children.add(Preconditions.checkNotNull(child));
    }
  }

  @Override
  public final @Nullable String getValue() {
    return null;
  }

  @Override
  public final int getNChildren() {
    return children.size();
  }

  @Override
  public final BaseNode getChild(int i) {
    return children.get(i);
  }

  @Override
  public final List<BaseNode> getChildren() {
    return Collections.unmodifiableList(children);
  }

  @Override
  public abstract BaseInnerNode shallowClone();

  @Override
  public abstract BaseInnerNode deepClone();


  // MUTATORS
  /** Adds a child node. */
  public void add(BaseNode child) {
    add(getNChildren(), child);
  }

  /** Adds a child node at the given index. */
  public void add(int index, BaseNode child) {
    children.add(index, Preconditions.checkNotNull(child));
  }

  /** Adds a child node at the given index. */
  public void replace(int index, BaseNode child) {
    children.set(index, Preconditions.checkNotNull(child));
  }

  /** Adds a child node at the given index. */
  public void remove(int index) {
    children.remove(index);
  }

}
