package com.mikesamuel.cil.ast;

import com.google.common.base.Optional;
import com.mikesamuel.cil.parser.ParSer;

/**
 * A collection of productions that define a language.
 * <p>
 * This includes some reflective operations over the language and also special
 * ParSer hooks for handling non-standard productions, and value coercion hooks
 * for handling force fitting.
 */
public interface Grammar<
    BASE_NODE extends BaseNode<BASE_NODE, NODE_TYPE, ?>,
    NODE_TYPE extends Enum<NODE_TYPE> & NodeType<BASE_NODE, NODE_TYPE>> {
  /**
   * Given a forest, turns it into a tree.
   * <p>
   * @throws UnsupportedOperationException if there is no pseudo-root,
   *     for example if there are no non-standard productions that can appear
   *     at the top level of a parse.
   */
  BASE_NODE makePseudoRoot(Iterable<? extends BASE_NODE> subRoots);

  /**
   * Casts the given node to the base node type for this grammar.
   *
   * @throws ClassCastException if not castable.
   */
  BASE_NODE cast(NodeI<?, ?, ?> node) throws ClassCastException;

  /**
   * Casts the given variant to one that obviously produces nodes compatible
   * with this grammar.
   *
   * @throws ClassCastException if not castable.
   */
  NodeVariant<BASE_NODE, NODE_TYPE> cast(NodeVariant<?, ?> v)
      throws ClassCastException;

  /**
   * Casts the given variant to one that obviously produces nodes compatible
   * with this grammar.
   *
   * @throws ClassCastException if not castable.
   */
  NODE_TYPE cast(NodeType<?, ?> t) throws ClassCastException;

  /**
   * Makes a best effort to coerce an expression result to an AST node of the
   * given type.
   *
   * @param input the value to coerce.
   * @param nodeType the type of node we want out.
   *
   * @return a node of the given type or a partially coerced input.
   */
  Object tryToCoerce(Object value, NODE_TYPE nodeType);

  /**
   * A parser for parsing a non-standard replacement for the given node type.
   */
  Optional<ParSer> parserForNonStandardReplacement(NodeType<?, ?> nodeType);

  /**
   * A parser for parsing a non-standard replacement for the given node type.
   */
  Optional<ParSer> parserForNonStandardInterstitial(NodeType<?, ?> nodeType);

  /**
   * True if the given node type can be produced by
   * {@link #parserForNonStandardReplacement}.
   */
  boolean isNonStandardReplacement(NodeType<?, ?> nodeType);

  /**
   * True if the given node type can be produced by
   * {@link #parserForNonStandardInterstitial}.
   */
  boolean isNonStandardInterstitial(NodeType<?, ?> nodeType);


  /**
   * Wraps a node in intermediate nodes to produce a node of the given outer
   * type.
   */
  Optional<BASE_NODE> wrap(BaseNode<?, ?, ?> inner, NODE_TYPE outerType);
}
