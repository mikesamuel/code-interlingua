package com.mikesamuel.cil.ast;

import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.mikesamuel.cil.ast.meta.MetadataBridge;
import com.mikesamuel.cil.parser.SourcePosition;

/**
 * A node in an AST.
 * <p>
 * This class is a bit over-parameterized, but this should only affect common
 * parsing and tree-building infrastructure.
 * Each language has its own base node type that overrides this class and
 * binds these parameters.
 *
 * @param <BASE_NODE> The language-specific base node type which is a super-type
 *     of every node in an AST for that language.
 * @param <NODE_TYPE> The node type enum for the language.  Enums tag each
 *     node.
 * @param <NODE_VARIANT> The node variant super-interface for the language.
 *     Each node type corresponds to a concrete subclass of BASE_NODE and
 *     each of those has an inner enum that specifies the variant of the node
 *     type.
 *     <p>
 *     The variant is significant because the
 *     {@link com.mikesamuel.cil.ptree core grammar operators} do not
 *     include an alternation ({@code |} or {@code /}) operator except for those
 *     implicit in the optional ({@code [...]}) operator.
 *     Instead, grammars are specified in disjunction of concatenation form.
 */
public abstract class BaseNode<
    BASE_NODE extends BaseNode<BASE_NODE, NODE_TYPE, NODE_VARIANT>,
    NODE_TYPE extends Enum<NODE_TYPE> & NodeType<BASE_NODE, NODE_TYPE>,
    NODE_VARIANT extends NodeVariant<BASE_NODE, NODE_TYPE>>
implements NodeI<BASE_NODE, NODE_TYPE, NODE_VARIANT> {
  private NODE_VARIANT variant;
  private @Nullable SourcePosition sourcePosition;

  protected BaseNode(NODE_VARIANT variant) {
    setVariant(variant);
  }


  /** The particular variant within the production. */
  @Override
  public NODE_VARIANT getVariant() {
    return variant;
  }

  /**
   * Sets the node variant.
   */
  public void setVariant(NODE_VARIANT newVariant) {
    NODE_TYPE type = newVariant.getNodeType();
    Preconditions.checkArgument(type.getNodeBaseType().isInstance(this));
    this.variant = Preconditions.checkNotNull(newVariant);
  }

  /** The production's node type. */
  @Override
  public final NODE_TYPE getNodeType() {
    return getVariant().getNodeType();
  }

  @Override
  public abstract List<BASE_NODE> getChildren();

  @Override
  public abstract BASE_NODE getChild(int i);

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

  /** Copies all parse and mixin metadata from the given node. */
  @Override
  public void copyMetadataFrom(NodeI<?, ?, ?> source, MetadataBridge bridge) {
    SourcePosition pos = source.getSourcePosition();
    if (pos != null) {
      setSourcePosition(pos);
    }
  }

  protected static
  <BASE_NODE extends BaseNode<BASE_NODE, ?, ?>, T extends BASE_NODE>
  T deepCopyChildren(T node) {
    int n = node.getNChildren();
    if (node instanceof InnerNode<?, ?, ?>) {
      @SuppressWarnings("unchecked")  // By convention.
      InnerNode<BASE_NODE, ?, ?> inode = (InnerNode<BASE_NODE, ?, ?>) node;
      for (int i = 0; i < n; ++i) {
        inode.replace(i, inode.getChild(i).deepClone());
      }
    } else {
      Preconditions.checkArgument(n == 0);
    }
    return node;
  }

  @Override
  public abstract BASE_NODE deepClone();

  @Override
  public abstract BASE_NODE shallowClone();


  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    appendToStringBuilder(sb);
    return sb.toString();
  }

  @Override
  public final int hashCode() {
    List<BASE_NODE> children = getChildren();
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
    BaseNode<?, ?, ?> other = (BaseNode<?, ?, ?>) obj;
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
    List<? extends BaseNode<?, ?, ?>> thisChildren = getChildren();
    List<? extends BaseNode<?, ?, ?>> otherChildren = other.getChildren();
    if (!thisChildren.equals(otherChildren)) {
      return false;
    }
    return true;
  }


  /**
   * A finder rooted at this that returns results of the given node type or
   * mixin.
   */
  @SuppressWarnings("synthetic-access")
  public <T> Finder<T> finder(Class<T> resultType) {
    return new Finder<>(this, resultType);
  }


  /** Searches the subtree rooted at {@code BaseNode.this}. */
  public static final class Finder<T> {
    private final BaseNode<?, ?, ?> root;
    private final Class<? extends T> matchType;
    private Predicate<? super BaseNode<?, ?, ?>> match;
    private Predicate<? super BaseNode<?, ?, ?>> doNotEnter;
    private boolean allowNonStandard = false;

    private Finder(BaseNode<?, ?, ?> root, Class<? extends T> matchType) {
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
    public Finder<T> match(NodeType<?, ?> nt, NodeType<?, ?>... nts) {
      match = Predicates.and(match, new HasNodeTypeIn(nt, nts));
      return this;
    }

    /**
     * Restricts nodes recursively searched by excluding ones with a
     * node type among those given.
     *
     * @return {@code this} to enable chaining.
     */
    public Finder<T> exclude(NodeType<?, ?> nt, NodeType<?, ?>... nts) {
      doNotEnter = Predicates.or(doNotEnter, new HasNodeTypeIn(nt, nts));
      return this;
    }

    /**
     * Restricts nodes recursively searched by excluding ones that are
     * an instance of any of the types given.
     *
     * @return {@code this} to enable chaining.
     */
    public Finder<T> exclude(Class<? extends NodeI<?, ?, ?>> cl) {
      doNotEnter = Predicates.or(doNotEnter, Predicates.instanceOf(cl));
      return this;
    }

    /**
     * Sets whether the finder will recurse into
     * {@linkplain NodeType#isNonStandard nonstandard} productions.
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

    private void find(
        BaseNode<?, ?, ?> node, ImmutableList.Builder<T> results) {
      if (match.apply(node)) {
        results.add(Preconditions.checkNotNull(matchType.cast(node)));
      }
      if (!doNotEnter.apply(node)
          && (allowNonStandard || !node.getNodeType().isNonStandard())) {
        for (int i = 0, n = node.getNChildren(); i < n; ++i) {
          BaseNode<?, ?, ?> child = node.getChild(i);
          find(child, results);
        }
      }
    }
  }

  private static final class HasNodeTypeIn
  implements Predicate<BaseNode<?, ?, ?>> {
    private final Set<NodeType<?, ?>> nodeTypes;

    HasNodeTypeIn(NodeType<?, ?> nt, NodeType<?, ?>... nts) {
      this.nodeTypes = ImmutableSet.<NodeType<?, ?>>builder()
          .add(nt)
          .add(nts)
          .build();
    }

    @Override
    public boolean apply(BaseNode<?, ?, ?> node) {
      return node != null && nodeTypes.contains(node.getNodeType());
    }
  }
}
