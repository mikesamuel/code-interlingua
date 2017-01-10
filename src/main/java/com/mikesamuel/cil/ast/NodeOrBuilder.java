package com.mikesamuel.cil.ast;

import javax.annotation.Nullable;

import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.mikesamuel.cil.parser.SourcePosition;

/**
 * An interface that allows examination of the content of a node.
 */
public interface NodeOrBuilder {

  /**
   * The variant of the node.
   */
  NodeVariant getVariant();

  /**
   * The source position of the node if known.
   */
  @Nullable SourcePosition getSourcePosition();

  /**
   * The type of node.
   */
  default NodeType getNodeType() {
    return getVariant().getNodeType();
  }

  /**
   * The children of this node.  Empty if a leaf.
   */
  ImmutableList<BaseNode> getChildren();

  /** Like {@code getChildren().size()} but more efficient for builders. */
  int getNChildren();

  /** Like {@code getChildren().get(i)} but more efficient for builders. */
  BaseNode getChild(int i);

  /**
   * The value if any.  {@code null} if an inner node.
   */
  String getValue();

  /**
   * A builder that currently has the state of this node allowing modification.
   */
  BaseNode.Builder<?, ?> builder();

  /**
   * This as a base node.
   */
  BaseNode toBaseNode();



  /** The first child with the given type or null. */
  public default @Nullable BaseNode firstChildWithType(NodeType nt) {
    for (int i = 0, n = getNChildren(); i < n; ++i) {
      BaseNode child = getChild(i);
      if (child.getNodeType() == nt) {
        return child;
      }
    }
    return null;
  }

  /** The first child with the given type or null. */
  public default @Nullable <T>
  T firstChildWithType(Class<? extends T> cl) {
    for (int i = 0, n = getNChildren(); i < n; ++i) {
      BaseNode child = getChild(i);
      if (cl.isInstance(child)) {
        return cl.cast(child);
      }
    }
    return null;
  }

  /**
   * The content of descendant leaf nodes separated by the given string.
   */
  public default String getTextContent(String separator) {
    StringBuilder sb = new StringBuilder();
    NodeOrBuilderHelpers.appendTextContent(this, sb, separator);
    return sb.toString();
  }

  /**
   * Appends a LISPy form.
   */
  public default void appendToStringBuilder(StringBuilder sb) {
    sb.append('(');
    sb.append(getVariant());
    String literalValue = getValue();
    if (literalValue != null) {
      sb.append(" `").append(literalValue).append('`');
    }
    for (int i = 0, n = getNChildren(); i < n; ++i) {
      BaseNode child = getChild(i);
      sb.append(' ');
      child.appendToStringBuilder(sb);
    }
    sb.append(')');
  }

  /**
   * A diagnostic string describing the structure of the tree.
   *
   * @param prefix a string to place to the left on each line.
   *     Can be used to indent.
   * @param decorator called for each node to return a string that is displayed
   *     to the right on the same line.  A return value of null means no
   *     decoration to display.  A value of null is equivalent to
   *     {@code Functions.constant(null)}.
   */
  public default String toAsciiArt(
      String prefix,
      @Nullable Function<? super NodeOrBuilder, ? extends String> decorator) {
    StringBuilder out = new StringBuilder();
    StringBuilder indentation = new StringBuilder();
    indentation.append(prefix);
    NodeOrBuilderHelpers.appendAsciiArt(this, indentation, out, decorator);
    return out.toString();
  }

  /**
   * Like {@link #toAsciiArt(String, Function)} but does not decorate.
   */
  public default String toAsciiArt(String prefix) {
    return toAsciiArt(prefix, Functions.constant(null));
  }

}

final class NodeOrBuilderHelpers {

  static void appendTextContent(
      NodeOrBuilder node, StringBuilder sb, String sep) {
    String literalValue = node.getValue();
    if (!Strings.isNullOrEmpty(literalValue)) {
      if (sep != null && sb.length() != 0) {
        sb.append(sep);
      }
      sb.append(literalValue);
    } else {
      for (int i = 0, n = node.getNChildren(); i < n; ++i) {
        appendTextContent(node.getChild(i), sb, sep);
      }
    }
  }

  static void appendAsciiArt(
      NodeOrBuilder node, StringBuilder prefix, StringBuilder out,
      @Nullable Function<? super NodeOrBuilder, ? extends String> decorator) {
    out.append(prefix);
    appendNodeHeader(node, out);
    String decoration = decorator != null ? decorator.apply(node) : null;
    if (decoration != null) {
      out.append(" : ").append(decoration);
    }
    int prefixLength = prefix.length();
    prefix.append("  ");
    for (int i = 0, n = node.getNChildren(); i < n; ++i) {
      BaseNode child = node.getChild(i);
      out.append('\n');
      appendAsciiArt(child, prefix, out, decorator);
    }
    prefix.setLength(prefixLength);
  }

  private static void appendNodeHeader(NodeOrBuilder node, StringBuilder out) {
    out.append(node.getVariant());
    String literalValue = node.getValue();
    if (literalValue != null) {
      out.append(' ').append(literalValue);
    }
  }
}
