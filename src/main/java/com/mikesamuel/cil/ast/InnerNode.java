package com.mikesamuel.cil.ast;

import java.util.Collections;
import java.util.List;

import javax.annotation.Nullable;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

/**
 * A node that may have children.
 * <p>
 * All nodes implement either this interface or {@link BaseNode}.
 */
public interface InnerNode<
    BASE_NODE extends BaseNode<BASE_NODE, NODE_TYPE, NODE_VARIANT>,
    NODE_TYPE extends Enum<NODE_TYPE> & NodeType<BASE_NODE, NODE_TYPE>,
    NODE_VARIANT extends NodeVariant<BASE_NODE, NODE_TYPE>>
extends NodeI<BASE_NODE, NODE_TYPE, NODE_VARIANT> {

  /** The child list for this inner node.  This should */
  MutableChildList<BASE_NODE> getMutableChildList();

  @Override
  public default @Nullable String getValue() {
    return null;
  }

  @Override
  public default int getNChildren() {
    return getMutableChildList().getNChildren();
  }

  @Override
  public default BASE_NODE getChild(int i) {
    return getMutableChildList().getChild(i);
  }

  @Override
  public default List<BASE_NODE> getChildren() {
    return getMutableChildList().getChildren();
  }


  // MUTATORS
  /** Adds a child node. */
  public default void add(BASE_NODE child) {
    getMutableChildList().add(child);
  }

  /** Adds a child node at the given index. */
  public default void add(int index, BASE_NODE child) {
    getMutableChildList().add(index, child);
  }

  /** Replaces the child node at the given index with the given child. */
  public default void replace(int index, BASE_NODE child) {
    getMutableChildList().replace(index, child);
  }

  /** Equivalent to removing all children and adding the given ones. */
  public default void replaceChildren(
      Iterable<? extends BASE_NODE> newChildren) {
    getMutableChildList().replaceChildren(newChildren);
  }

  /** Adds a child node at the given index. */
  public default void remove(int index) {
    getMutableChildList().remove(index);
  }


  /** A mutable list of children. */
  public static final
  class MutableChildList<BASE_NODE extends BaseNode<BASE_NODE, ?, ?>> {
    private final List<BASE_NODE> children = Lists.newArrayList();

    /** The count of children in the list. */
    public final int getNChildren() {
      return children.size();
    }

    /** The i-th child. */
    public final BASE_NODE getChild(int i) {
      return children.get(i);
    }

    /** The children as an immutable list. */
    public final List<BASE_NODE> getChildren() {
      return Collections.unmodifiableList(children);
    }

    // MUTATORS
    /** Adds a child node. */
    public final void add(BASE_NODE child) {
      add(getNChildren(), child);
    }

    /** Adds a child node at the given index. */
    public final void add(int index, BASE_NODE child) {
      children.add(index, Preconditions.checkNotNull(child));
    }

    /** Replaces the child node at the given index with the given child. */
    public final void replace(int index, BASE_NODE child) {
      children.set(index, Preconditions.checkNotNull(child));
    }

    /** Equivalent to removing all children and adding the given ones. */
    public final void replaceChildren(
        Iterable<? extends BASE_NODE> newChildren) {
      ImmutableList<BASE_NODE> newChildrenKnownNotNull =
          ImmutableList.copyOf(newChildren);
      children.clear();
      children.addAll(newChildrenKnownNotNull);
    }

    /** Adds a child node at the given index. */
    public final void remove(int index) {
      children.remove(index);
    }

  }
}
