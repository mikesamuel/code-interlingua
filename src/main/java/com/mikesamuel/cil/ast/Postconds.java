package com.mikesamuel.cil.ast;

import com.google.common.base.Predicate;
import com.mikesamuel.cil.parser.Chain;

/**
 * Postconditions are applied after parsing a variant.
 * <p>
 * They're used to make it easy to derive productions like
 * {@link NodeType#LeftHandSide} and {@link NodeType#StatementExpression} that
 * match a subset of a larger production like {@Link NodeType#Primary} without
 * having to copy node types.
 */
final class Postconds {

  private static final boolean DEBUG = false;

  private static void dumpEvents(Chain<MatchEvent> output) {
    StringBuilder sb = new StringBuilder(". ");
    for (MatchEvent e : Chain.forwardIterable(output)) {
      if (e instanceof MatchEvent.Pop) {
        if (sb.length() != 0) {
          sb.setLength(sb.length() - 2);
        }
      }
      int len = sb.length();
      System.err.println(sb.append(e));
      sb.setLength(len);
      if (e instanceof MatchEvent.Push) {
        sb.append(". ");
      }
    }
  }

  /**
   * Checks that the last node at a specified depth has a specified variant.
   */
  private static final class Postcond implements Predicate<Chain<MatchEvent>> {
    final int depth;
    final NodeVariant variant;

    Postcond(int depth, NodeVariant variant) {
      this.depth = depth;
      this.variant = variant;
    }

    @Override
    public boolean apply(Chain<MatchEvent> output) {
      if (DEBUG) {
        System.err.println(
            "Checking postcondition " + variant + " at depth " + depth);
        dumpEvents(output);
      }
      if (output == null || !(output.x instanceof MatchEvent.Pop)) {
        return false;
      }
      int popDepth = 0;
      for (Chain<MatchEvent> c = output; c != null; c = c.prev) {
        MatchEvent e = c.x;
        if (e instanceof MatchEvent.Pop) {
          ++popDepth;
        } else if (e instanceof MatchEvent.Push) {
          --popDepth;
          MatchEvent.Push push = (MatchEvent.Push) e;
          if (DEBUG) {
            System.err.print("Found " + push + " at depth " + popDepth);
          }
          if (popDepth == depth) {
            return push.variant == variant;
          }
          if (popDepth == 0) {
            break;
          }
        }
      }
      return false;
    }
  }

  /**
   * Postconditions that apply to variants that delegate to
   * {@link NodeType#Primary}.
   */
  static final class Primary {

    static final Postcond Ambiguous = new Postcond(
        1, PrimaryNode.Variant.Ambiguous);

    /**
     * Postconditions that apply to Primaries that have a
     * {@link NodeType#PostOp} and which check the last PostOp's variant.
     */
    static final class PostOp {
      static final Postcond ArrayAccess = new Postcond(
          2, PostOpNode.Variant.ArrayAccess);
      static final Postcond FieldAccess = new Postcond(
          2, PostOpNode.Variant.FieldAccess);
      static final Postcond InnerClassCreation = new Postcond(
          2, PostOpNode.Variant.InnerClassCreation);
      static final Postcond MethodInvocation = new Postcond(
          2, PostOpNode.Variant.MethodInvocation);
    }

    /**
     * Postconditions that apply to Primaries that have no PostOps and which
     * check the {@link NodeType#ExpressionAtom}'s variant.
     */
    static final class ExpressionAtom {
      static final Postcond FreeField = new Postcond(
          2, ExpressionAtomNode.Variant.FreeField);
      static final Postcond Local = new Postcond(
          2, ExpressionAtomNode.Variant.Local);
      static final Postcond MethodInvocation = new Postcond(
          2, ExpressionAtomNode.Variant.MethodInvocation);
      static final Postcond UnqualifiedClassInstanceCreationExpression =
          new Postcond(
              2,
              ExpressionAtomNode.Variant
              .UnqualifiedClassInstanceCreationExpression);
    }
  }

  /** Apply directly to PostOp variants. */
  static final class PostOp {
    static final Postcond FieldAccess = new Postcond(
        1, PostOpNode.Variant.FieldAccess);
  }

}
