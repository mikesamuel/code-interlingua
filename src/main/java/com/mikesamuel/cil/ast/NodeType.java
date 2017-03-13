package com.mikesamuel.cil.ast;

import com.mikesamuel.cil.parser.ParSerable;

/**
 * An interface that an {@code enum} of AST node types can implement.
 */
public interface NodeType<BASE_NODE extends BaseNode<BASE_NODE, SELF, ?>,
                          SELF extends Enum<SELF> & NodeType<BASE_NODE, SELF>>
extends ParSerable, Comparable<SELF> {
  /** The node variant class for nodes of this type. */
  Class<? extends Enum<? extends NodeVariant<BASE_NODE, SELF>>>
      getVariantType();

  /** The class that represents nodes with this type. */
  Class<? extends BASE_NODE> getNodeBaseType();

  /** True iff the node is parsed by taking a non-standard branch. */
  boolean isNonStandard();

  /**
   * True for productions that are often used to parse entire fragments of
   * source code.
   * <p>
   * This is often true for the <i>program</i> or <i>compilation unit</i>
   * production and also for a top-level expression or statement production
   * sed in a REPL.
   */
  boolean isTopLevel();

  /**
   * True if the production just decorates the identifier production.
   */
  boolean isIdentifierWrapper();

  /** Automatically provided for {@link Enum} classes. */
  String name();

  /** Automatically provided for {@link Enum} classes. */
  int ordinal();

  /** The grammar for this node type. */
  Grammar<BASE_NODE, SELF> getGrammar();
}
