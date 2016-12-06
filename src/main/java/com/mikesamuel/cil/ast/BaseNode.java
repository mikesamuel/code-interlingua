package com.mikesamuel.cil.ast;

import javax.annotation.Nullable;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
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

  /** The production's node type. */
  public final NodeType getNodeType() {
    return getVariant().getNodeType();
  }

  /** Child nodes. */
  public final ImmutableList<BaseNode> getChildren() {
    return children;
  }

  /** The first child with the given type or null. */
  public @Nullable BaseNode firstChildWithType(NodeType nt) {
    for (BaseNode child : children) {
      if (child.getNodeType() == nt) {
        return child;
      }
    }
    return null;
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

  /**
   * The content of descendant leaf nodes separated by the given string.
   */
  public String getTextContent(String separator) {
    StringBuilder sb = new StringBuilder();
    appendTextContent(sb, separator);
    return sb.toString();
  }

  private void appendTextContent(StringBuilder sb, String sep) {
    if (!Strings.isNullOrEmpty(literalValue)) {
      if (sep != null && sb.length() != 0) {
        sb.append(sep);
      }
      sb.append(literalValue);
    } else {
      for (BaseNode child : getChildren()) {
        child.appendTextContent(sb, sep);
      }
    }
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

    protected Builder(V variant) {
      this.newNodeVariant = Preconditions.checkNotNull(variant);
    }

    protected V getVariant() {
      return newNodeVariant;
    }

    /** Builds a complete node. */
    public abstract N build();
  }

  /**
   * A builder for inner nodes.
   */
  public static abstract
  class InnerBuilder<N extends BaseNode, V extends NodeVariant>
  extends Builder<N, V> {
    private final ImmutableList.Builder<BaseNode> newNodeChildren =
        ImmutableList.builder();

    protected InnerBuilder(V variant) {
      super(variant);
    }

    protected ImmutableList<BaseNode> getChildren() {
      return newNodeChildren.build();
    }

    /** Adds a child node. */
    public InnerBuilder<N, V> add(BaseNode child) {
      this.newNodeChildren.add(child);
      return this;
    }
  }

  /**
   * A builder for inner nodes.
   */
  public static abstract
  class LeafBuilder<N extends BaseNode, V extends NodeVariant>
  extends Builder<N, V> {
    private Optional<String> newLiteralValue = Optional.absent();

    protected LeafBuilder(V variant) {
      super(variant);
    }

    protected String getLiteralValue() {
      return newLiteralValue.orNull();
    }

    /** Specifies the value. */
    public LeafBuilder<N, V> leaf(String leafLiteralValue) {
      this.newLiteralValue = Optional.of(leafLiteralValue);
      return this;
    }
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
    result = prime * result + variant.hashCode();
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
    if (!variant.equals(other.variant)) {
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

  /**
   * A diagnostic string describing the structure of the tree.
   *
   * @param prefix a string to place to the left on each line.
   *     Can be used to indent.
   * @param decorator called for each node to return a string that is displayed
   *     to the right on the same line.  A return value of null means no
   *     decoration to display.
   */
  public String toAsciiArt(
      String prefix, Function<? super BaseNode, ? extends String> decorator) {
    StringBuilder out = new StringBuilder();
    StringBuilder indentation = new StringBuilder();
    indentation.append(prefix);
    appendAsciiArt(indentation, out, decorator);
    return out.toString();
  }

  private void appendAsciiArt(
      StringBuilder prefix, StringBuilder out,
      Function<? super BaseNode, ? extends String> decorator) {
    out.append(prefix);
    appendNodeHeader(out);
    String decoration = decorator.apply(this);
    if (decoration != null) {
      out.append(" : ").append(decoration);
    }
    int prefixLength = prefix.length();
    prefix.append("  ");
    for (BaseNode child : children) {
      out.append('\n');
      child.appendAsciiArt(prefix, out, decorator);
    }
    prefix.setLength(prefixLength);
  }

  private void appendNodeHeader(StringBuilder out) {
    out.append(variant);
    if (literalValue != null) {
      out.append(' ').append(literalValue);
    }
  }
}
