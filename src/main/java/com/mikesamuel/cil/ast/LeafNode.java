package com.mikesamuel.cil.ast;

import java.util.Collections;
import java.util.List;

/**
 * A node that may not have children and may correspond to a literal token.
 *
 * @see InnerNode
 */
public interface LeafNode<
    BASE_NODE extends BaseNode<BASE_NODE, NODE_TYPE, NODE_VARIANT>,
    NODE_TYPE extends Enum<NODE_TYPE> & NodeType<BASE_NODE, NODE_TYPE>,
    NODE_VARIANT extends NodeVariant<BASE_NODE, NODE_TYPE>>
extends NodeI<BASE_NODE, NODE_TYPE, NODE_VARIANT> {

  @Override
  public default int getNChildren() {
    return 0;
  }

  @Override
  public default BASE_NODE getChild(int i) {
    throw new IndexOutOfBoundsException("" + i);
  }

  @Override
  public default List<BASE_NODE> getChildren() {
    return Collections.emptyList();
  }

  // MUTATORS
  /** Mutator */
  public void setValue(String newValue);
}


