package com.mikesamuel.cil.parser;

import java.util.EnumMap;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.mikesamuel.cil.ast.NodeType;
import com.mikesamuel.cil.ast.NodeVariant;

/**
 * Allows communication between recursive invocations of a production to handle
 * left-recursion correctly even in the presence of non-left recursion of the
 * same production.
 */
public final class LeftRecursion {
  /**
   * The productions on the stack and the input cursor indices at which they
   * were entered with greater indices earlier in the chain.
   */
  private final EnumMap<NodeType, Chain<PositionAndStage>> onStack =
      new EnumMap<>(NodeType.class);
  private Chain<NodeVariant> variantStack;

  /**
   * True if there is a variant with the given node type on the stack.
   */
  public Stage stageForProductionAt(NodeType nodeType, int index) {
    Chain<PositionAndStage> sps = onStack.get(nodeType);
    for (Chain<PositionAndStage> c = sps ; c != null; c = c.prev) {
      if (c.x.index == index) {
        return c.x.stage;
      }
    }
    return Stage.NOT_ON_STACK;
  }


  /**
   * The variant from the most recent entry of the given production.
   */
  public Optional<ImmutableList<NodeVariant>> getStackFrom(NodeType nodeType) {
    Chain<NodeVariant> stackFrom = null;
    for (Chain<NodeVariant> c = variantStack; c != null; c = c.prev) {
      stackFrom = Chain.append(stackFrom, c.x);
      if (c.x.getNodeType() == nodeType) {
        return Optional.of(ImmutableList.copyOf(Chain.reverseIterable(stackFrom)));
      }
    }
    return Optional.absent();
  }


  /**
   * Pushes a variant scope.
   *
   * @return a scope that must be closed to signal the exit of the scope.
   * @see #getStackFrom
   */
  public VariantScope enter(
      final NodeVariant variant,
      final int index,
      final Stage stage) {
    Preconditions.checkArgument(stage != Stage.NOT_ON_STACK, stage);

    final NodeType nodeType = variant.getNodeType();

    final PositionAndStage ps = new PositionAndStage(index, stage);
    {
      Chain<PositionAndStage> c = onStack.get(nodeType);
      Preconditions.checkState(c == null || c.x.index < index);
      onStack.put(nodeType, Chain.append(c, ps));
    }

    variantStack = Chain.append(variantStack, variant);

    return new VariantScope() {

      @SuppressWarnings("synthetic-access")
      @Override
      public void close() {
        Preconditions.checkState(variantStack.x == variant);

        {
          Chain<PositionAndStage> c = onStack.get(nodeType);
          Preconditions.checkState(c != null && c.x == ps);
          onStack.put(nodeType, c.prev);
        }

        variantStack = variantStack.prev;
      }
    };
  }

  /**
   * Can be used with try/with to bracket a variant stack scope.
   */
  public static interface VariantScope extends AutoCloseable {
    /** Exits the scope. */
    @Override void close();
  }

  /** A stage in the grow-the-seed algorithm. */
  public enum Stage {
    /** Not entered yet. */
    NOT_ON_STACK,
    /**
     * No left recursion has yet been detected, but any left recursion
     * encountered should be treated as a failure so we can compute the seed.
     */
    SEEDING,
    /**
     * We are growing the seed, so we must find a left-recursive invocation and
     * then push the stack reaching it left before continuing.
     */
    GROWING,
    ;
  }


  /**
   * A stage at a particular parse cursor index.
   */
  static final class PositionAndStage {
    final int index;
    final Stage stage;

    PositionAndStage(int index, Stage stage) {
      this.index = index;
      this.stage = stage;
    }
  }
}
