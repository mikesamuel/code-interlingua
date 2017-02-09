package com.mikesamuel.cil.ast;

import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import com.mikesamuel.cil.parser.SourcePosition;

/**
 * A node in a Java AST.
 */
public abstract class BaseNode implements NodeI {
  private NodeVariant variant;
  private @Nullable SourcePosition sourcePosition;

  BaseNode(NodeVariant variant) {
    setVariant(variant);
  }


  /** The particular variant within the production. */
  @Override
  public NodeVariant getVariant() {
    return variant;
  }

  /**
   * Sets the node variant.
   */
  public void setVariant(NodeVariant newVariant) {
    NodeType type = newVariant.getNodeType();
    Preconditions.checkArgument(type.getNodeBaseType().isInstance(this));
    this.variant = Preconditions.checkNotNull(newVariant);
  }

  /** The production's node type. */
  @Override
  public final NodeType getNodeType() {
    return getVariant().getNodeType();
  }

  /** The source position.  Non-normative. */
  @Override
  public final @Nullable SourcePosition getSourcePosition() {
    return sourcePosition;
  }

  /**
   * @see #getSourcePosition()
   */
  @Override
  public final void setSourcePosition(SourcePosition newSourcePosition) {
    this.sourcePosition = newSourcePosition;
  }

  /** Copies all parse and trait metadata from the given node. */
  @Override
  public void copyMetadataFrom(BaseNode source) {
    SourcePosition pos = source.getSourcePosition();
    if (pos != null) {
      setSourcePosition(pos);
    }
  }

  /** A clone that has the same children. */
  public abstract BaseNode shallowClone();

  /** A clone whose children are deep clones of this node's children. */
  public abstract BaseNode deepClone();

  protected static <T extends BaseNode> T deepCopyChildren(T node) {
    int n = node.getNChildren();
    if (node instanceof BaseInnerNode) {
      BaseInnerNode inode = (BaseInnerNode) node;
      for (int i = 0; i < n; ++i) {
        inode.replace(i, inode.getChild(i).deepClone());
      }
    } else {
      Preconditions.checkArgument(n == 0);
    }
    return node;
  }


  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    appendToStringBuilder(sb);
    return sb.toString();
  }

  @Override
  public final int hashCode() {
    List<BaseNode> children = getChildren();
    String literalValue = getValue();

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
    if (!variant.equals(other.variant)) {
      return false;
    }
    String thisValue = this.getValue();
    String otherValue = other.getValue();
    if (!variant.isIgnorable()) {
      if (thisValue == null) {
        if (otherValue != null) {
          return false;
        }
      } else if (!thisValue.equals(otherValue)) {
        return false;
      }
    }
    List<BaseNode> thisChildren = getChildren();
    List<BaseNode> otherChildren = other.getChildren();
    if (!thisChildren.equals(otherChildren)) {
      return false;
    }
    return true;
  }


  /**
   * A finder rooted at this that returns results of the given node type or
   * trait.
   */
  @SuppressWarnings("synthetic-access")
  public <T> Finder<T> finder(Class<T> resultType) {
    return new Finder<>(this, resultType);
  }


  /** Searches the subtree rooted at {@code BaseNode.this}. */
  public static final class Finder<T> {
    private final BaseNode root;
    private final Class<? extends T> matchType;
    private Predicate<? super BaseNode> match;
    private Predicate<? super BaseNode> doNotEnter;
    private boolean allowNonStandard = false;

    private Finder(BaseNode root, Class<? extends T> matchType) {
      this.root = root;
      this.matchType = matchType;
      match = Predicates.instanceOf(matchType);
      doNotEnter = Predicates.alwaysFalse();
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
      doNotEnter = Predicates.or(doNotEnter, new HasNodeTypeIn(nt, nts));
      return this;
    }

    /**
     * Restricts nodes recursively searched by excluding them to ones with a
     * node type among those given.
     *
     * @return {@code this} to enable chaining.
     */
    public Finder<T> exclude(Class<? extends NodeI> cl) {
      doNotEnter = Predicates.or(doNotEnter, Predicates.instanceOf(cl));
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
      find(root, results);
      return results.build();
    }

    /**
     * Performs a search and returns the sole result or panics if there is more
     * than one result.
     * <p>
     * With the default assumption that find does not descend into template
     * instructions, this helps with assumptions that there is one node.
     */
    public Optional<T> findOne() {
      ImmutableList<T> results = find();
      if (results.size() == 1) {
        return Optional.of(results.get(0));
      }
      Preconditions.checkState(results.isEmpty(), results);
      return Optional.absent();
    }

    private void find(BaseNode node, ImmutableList.Builder<T> results) {
      if (match.apply(node)) {
        results.add(Preconditions.checkNotNull(matchType.cast(node)));
      }
      if (!doNotEnter.apply(node)
          && (allowNonStandard
              || !NodeTypeTables.NONSTANDARD.contains(node.getNodeType()))) {
        for (int i = 0, n = node.getNChildren(); i < n; ++i) {
          BaseNode child = node.getChild(i);
          find(child, results);
        }
      }
    }
  }

  private static final class HasNodeTypeIn implements Predicate<BaseNode> {
    final Set<NodeType> nodeTypes;

    HasNodeTypeIn(NodeType nt, NodeType... nts) {
      this.nodeTypes = Sets.immutableEnumSet(nt, nts);
    }

    @Override
    public boolean apply(BaseNode node) {
      return node != null && nodeTypes.contains(node.getNodeType());
    }
  }
}
