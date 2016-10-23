package com.mikesamuel.cil.ast;

import javax.annotation.Nullable;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

/**
 * A node in a Java AST.
 */
public abstract class BaseNode {
  private final NodeType type;
  private final NodeVariant variant;
  private final ImmutableList<BaseNode> children;
  private final @Nullable String literalValue;

  BaseNode(
      NodeType type, NodeVariant variant,
      Iterable<? extends BaseNode> children, @Nullable String literalValue) {
    Preconditions.checkState(type.getNodeBaseType().isInstance(this));
    Preconditions.checkState(type.getVariantType().isInstance(variant));
    this.type = type;
    this.variant = variant;
    this.children = ImmutableList.copyOf(children);
    this.literalValue = literalValue;
  }

  /** The type of node which corresponds to a production in the JLS grammar. */
  public NodeType getType() {
    return type;
  }

  /** The particular variant within the production. */
  public NodeVariant getVariant() {
    return variant;
  }

  /** Child nodes. */
  public ImmutableList<BaseNode> getChildren() {
    return children;
  }

  /** The value if any. */
  public @Nullable String getValue() {
    return literalValue;
  }


  /**
   * Allows building nodes.
   */
  public static abstract
  class Builder<N extends BaseNode, V extends NodeVariant> {
    private final NodeType newNodeType;
    private final V newNodeVariant;
    private final ImmutableList.Builder<BaseNode> newNodeChildren =
        ImmutableList.builder();
    private Optional<String> newLiteralValue = Optional.absent();

    protected Builder(NodeType type, V variant) {
      this.newNodeType = type;
      this.newNodeVariant = variant;
    }

    protected NodeType getType() {
      return newNodeType;
    }

    protected V getVariant() {
      return newNodeVariant;
    }

    protected ImmutableList<BaseNode> getChildren() {
      return newNodeChildren.build();
    }

    protected String getLiteralValue() {
      return newLiteralValue.orNull();
    }

    /** Adds a child node. */
    public Builder<N, V> add(BaseNode child) {
      this.newNodeChildren.add(child);
      return this;
    }

    /** Specifies the value. */
    public Builder<N, V> leaf(String leafLiteralValue) {
      this.newLiteralValue = Optional.of(leafLiteralValue);
      return this;
    }

    /** Builds a complete node. */
    public abstract N build();
  }
}
