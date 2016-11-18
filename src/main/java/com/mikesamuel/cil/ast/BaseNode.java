package com.mikesamuel.cil.ast;

import javax.annotation.Nullable;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.mikesamuel.cil.parser.SourcePosition;

/**
 * A node in a Java AST.
 */
public abstract class BaseNode {
  private final NodeVariant variant;
  private final ImmutableList<BaseNode> children;
  private final @Nullable String literalValue;
  private @Nullable SourcePosition sourcePosition;

  BaseNode(
      NodeVariant variant,
      Iterable<? extends BaseNode> children, @Nullable String literalValue) {
    this.variant = Preconditions.checkNotNull(variant);

    NodeType type = variant.getNodeType();
    Preconditions.checkState(type.getNodeBaseType().isInstance(this));

    this.children = ImmutableList.copyOf(children);
    this.literalValue = literalValue;
  }

  /** The particular variant within the production. */
  public NodeVariant getVariant() {
    return variant;
  }

  /** Child nodes. */
  public final ImmutableList<BaseNode> getChildren() {
    return children;
  }

  /** The value if any. */
  public final @Nullable String getValue() {
    return literalValue;
  }

  /** The source position.  Non-normative. */
  public final @Nullable SourcePosition getSourcePosition() {
    return sourcePosition;
  }

  /**
   * @see #getSourcePosition()
   */
  public final void setSourcePosition(SourcePosition newSourcePosition) {
    this.sourcePosition = newSourcePosition;
  }

  @Override
  public final String toString() {
    StringBuilder sb = new StringBuilder();
    appendToStringBuilder(sb);
    return sb.toString();
  }

  protected void appendToStringBuilder(StringBuilder sb) {
    sb.append('(');
    sb.append(variant);
    if (literalValue != null) {
      sb.append(" `").append(literalValue).append('`');
    }
    for (BaseNode child : children) {
      sb.append(' ');
      child.appendToStringBuilder(sb);
    }
    sb.append(')');
  }


  /**
   * Allows building nodes.
   */
  public static abstract
  class Builder<N extends BaseNode, V extends NodeVariant> {
    private final V newNodeVariant;
    private final ImmutableList.Builder<BaseNode> newNodeChildren =
        ImmutableList.builder();
    private Optional<String> newLiteralValue = Optional.absent();

    protected Builder(V variant) {
      this.newNodeVariant = variant;
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



  @Override
  public final int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((children == null) ? 0 : children.hashCode());
    if (!variant.isIgnorable()) {
      result = prime * result
          + ((literalValue == null) ? 0 : literalValue.hashCode());
    }
    result = prime * result + ((variant == null) ? 0 : variant.hashCode());
    return result;
  }

  @Override
  public final boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    BaseNode other = (BaseNode) obj;
    if (children == null) {
      if (other.children != null) {
        return false;
      }
    } else if (!children.equals(other.children)) {
      return false;
    }
    if (variant == null) {
      if (other.variant != null) {
        return false;
      }
    } else if (!variant.equals(other.variant)) {
      return false;
    }
    if (!variant.isIgnorable()) {
      if (literalValue == null) {
        if (other.literalValue != null) {
          return false;
        }
      } else if (!literalValue.equals(other.literalValue)) {
        return false;
      }
    }
    return true;
  }
}
