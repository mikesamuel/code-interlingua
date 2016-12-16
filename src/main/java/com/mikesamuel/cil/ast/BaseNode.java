package com.mikesamuel.cil.ast;

import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;

import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
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

  /**
   * A builder that currently has the state of this node allowing modification.
   */
  public abstract Builder<?, ?> builder();

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

  /** The first child with the given type or null. */
  public @Nullable <T>
  T firstChildWithType(Class<? extends T> cl) {
    for (BaseNode child : children) {
      if (cl.isInstance(child)) {
        return cl.cast(child);
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
    private V newNodeVariant;
    private SourcePosition sourcePosition;
    /** True if it changed from its parent. */
    private boolean changed;

    protected Builder(V variant) {
      this.newNodeVariant = Preconditions.checkNotNull(variant);
      this.changed = true;
    }

    protected Builder(N source) {
      @SuppressWarnings("unchecked")
      // Unsound but safe is subclassing follows discipline.  For this reason
      // we keep constructors package-private.
      V sourceVariant = (V) source.getVariant();
      this.newNodeVariant = sourceVariant;
      this.sourcePosition = source.getSourcePosition();
      this.copyMetadataFrom(source);
      this.changed = false;
    }

    protected V getVariant() {
      return newNodeVariant;
    }

    protected SourcePosition getSourcePosition() {
      return sourcePosition;
    }

    /**
     * Specifies the variant that will be built.
     */
    public Builder<N, V> variant(V newVariant) {
      if (newVariant != this.newNodeVariant) {
        this.newNodeVariant = Preconditions.checkNotNull(newVariant);
        markChanged();
      }
      return this;
    }

    /** Any nodes built will have the same meta-data as the given node. */
    public Builder<N, V> copyMetadataFrom(N source) {
      SourcePosition pos = source.getSourcePosition();
      if (pos != null) {
        setSourcePosition(pos);
      }
      return this;
    }

    /**
     * Specifies the source position for the built node.
     */
    public Builder<N, V> setSourcePosition(SourcePosition newSourcePosition) {
      if (!(sourcePosition == null ? newSourcePosition == null
          : sourcePosition.equals(newSourcePosition))) {
        this.sourcePosition = newSourcePosition;
        markChanged();
      }
      return this;
    }

    /** Builds a complete node. */
    public abstract N build();

    protected void markChanged() {
      this.changed = true;
    }

    /**
     * True iff the builder was derived from a node and no changes were
     * made to that node.
     */
    public boolean changed() {
      return this.changed;
    }
  }

  /**
   * A builder for inner nodes.
   */
  public static abstract
  class InnerBuilder<N extends BaseNode, V extends NodeVariant>
  extends Builder<N, V> {
    private final List<BaseNode> newNodeChildren = Lists.newArrayList();

    protected InnerBuilder(V variant) {
      super(variant);
    }

    protected InnerBuilder(N source) {
      super(source);
      newNodeChildren.addAll(source.getChildren());
    }

    /** The count of children thus far. */
    public int getNChildren() {
      return newNodeChildren.size();
    }

    /** The child at index i */
    public BaseNode getChild(int i) {
      return newNodeChildren.get(i);
    }

    protected ImmutableList<BaseNode> getChildren() {
      return ImmutableList.copyOf(newNodeChildren);
    }

    /** Adds a child node. */
    public InnerBuilder<N, V> add(BaseNode child) {
      return add(newNodeChildren.size(), child);
    }

    /** Adds a child node at the given index. */
    public InnerBuilder<N, V> add(int index, BaseNode child) {
      this.newNodeChildren.add(index, Preconditions.checkNotNull(child));
      this.markChanged();
      return this;
    }

    /** Adds a child node at the given index. */
    public InnerBuilder<N, V> replace(int index, BaseNode child) {
      BaseNode old = this.newNodeChildren.set(
          index, Preconditions.checkNotNull(child));
      if (old != child) {
        this.markChanged();
      }
      return this;
    }

    /** Adds a child node at the given index. */
    public InnerBuilder<N, V> remove(int index) {
      this.newNodeChildren.remove(index);
      this.markChanged();
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

    protected LeafBuilder(N source) {
      super(source);
      newLiteralValue = Optional.fromNullable(source.getValue());
    }

    protected String getLiteralValue() {
      return newLiteralValue.orNull();
    }

    /** Specifies the value. */
    public LeafBuilder<N, V> leaf(String leafLiteralValue) {
      Optional<String> newValueOpt = Optional.of(leafLiteralValue);
      if (!newLiteralValue.equals(newValueOpt)) {
        this.newLiteralValue = newValueOpt;
        this.markChanged();
      }
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
   * A finder rooted at this that returns results of the given node type or
   * trait.
   */
  public <T> Finder<T> finder(Class<T> resultType) {
    return new Finder<>(resultType);
  }

  /** Searches the subtree rooted at {@code BaseNode.this}. */
  public final class Finder<T> {
    private final Class<? extends T> matchType;
    private Predicate<? super BaseNode> match;
    private Predicate<? super BaseNode> enter;
    private boolean allowNonStandard = false;

    Finder(Class<? extends T> matchType) {
      this.matchType = matchType;
      match = Predicates.instanceOf(matchType);
      enter = Predicates.alwaysTrue();
    }

    /**
     * Restricts matched nodes to those with a node type among those given.
     *
     * @return {@code this} to enable chaining.
     */
    public Finder<T> match(NodeType nt, NodeType... nts) {
      match = Predicates.and(match, new HasNodeTypeIn(nt, nts));
      return this;
    }

    /**
     * Restricts nodes recursively searched by excluding them to ones with a
     * node type among those given.
     *
     * @return {@code this} to enable chaining.
     */
    public Finder<T> exclude(NodeType nt, NodeType... nts) {
      enter = Predicates.and(
          enter, Predicates.not(new HasNodeTypeIn(nt, nts)));
      return this;
    }

    /**
     * Sets whether the finder will recurse into
     * {@linkplain NodeTypeTables#NONSTANDARD nonstandard} productions.
     * Defaults to false.
     */
    public Finder<T> allowNonStandard(boolean b) {
      this.allowNonStandard = b;
      return this;
    }

    /**
     * Performs a search and returns the results.
     */
    public ImmutableList<T> find() {
      ImmutableList.Builder<T> results = ImmutableList.builder();
      find(BaseNode.this, results);
      return results.build();
    }

    private void find(BaseNode node, ImmutableList.Builder<T> results) {
      if (match.apply(node)) {
        results.add(Preconditions.checkNotNull(matchType.cast(node)));
      }
      if (enter.apply(node)
          && (allowNonStandard
              || !NodeTypeTables.NONSTANDARD.contains(node.getNodeType()))) {
        for (BaseNode child : node.getChildren()) {
          find(child, results);
        }
      }
    }
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

  /**
   * Like {@link #toAsciiArt(String, Function)} but does not decorate.
   */
  public String toAsciiArt(String prefix) {
    return toAsciiArt(prefix, Functions.constant(null));
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

  private static final class HasNodeTypeIn implements Predicate<BaseNode> {
    final Set<NodeType> nodeTypes;

    HasNodeTypeIn(NodeType nt, NodeType... nts) {
      this.nodeTypes = Sets.immutableEnumSet(nt, nts);
    }

    @Override
    public boolean apply(BaseNode node) {
      return nodeTypes.contains(node.getNodeType());
    }
  }
}
