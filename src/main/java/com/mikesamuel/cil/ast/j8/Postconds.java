package com.mikesamuel.cil.ast.j8;

import com.google.common.base.Predicate;
import com.mikesamuel.cil.event.Debug;
import com.mikesamuel.cil.event.Event;
import com.mikesamuel.cil.parser.SList;

/**
 * Postconditions are applied after parsing a variant.
 * <p>
 * They're used to make it easy to derive productions like
 * {@link J8NodeType#LeftHandSide} and {@link J8NodeType#StatementExpression}
 * match a subset of a larger production like {@Link J8NodeType#Primary} without
 * having to copy node types.
 */
public final class Postconds {

  private static final boolean DEBUG = false;

  /**
   * Checks that the last node at a specified depth has a specified variant.
   */
  private static final class Postcond implements Predicate<SList<Event>> {
    final int depth;
    final J8NodeVariant variant;

    Postcond(int depth, J8NodeVariant variant) {
      this.depth = depth;
      this.variant = variant;
    }

    @Override
    public boolean apply(SList<Event> output) {
      if (DEBUG) {
        System.err.println(
            "Checking postcondition " + variant + " at depth " + depth);
        Debug.dumpEvents(SList.forwardIterable(output));
      }
      if (output == null || output.x.getKind() != Event.Kind.POP) {
        return false;
      }
      int popDepth = 0;
      event_loop:
      for (SList<Event> c = output; c != null; c = c.prev) {
        Event e = c.x;
        switch (e.getKind()) {
          case POP:
            ++popDepth;
            break;
          case PUSH:
            --popDepth;
            if (DEBUG) {
              System.err.println("Found " + e + " at depth " + popDepth);
            }
            if (popDepth == depth) {
              return e.getNodeVariant() == variant;
            }
            if (popDepth == 0) {
              break event_loop;
            }
            break;
          case CONTENT:
          case DELAYED_CHECK:
          case IGNORABLE:
          case LR_END:
          case LR_START:
          case POSITION_MARK:
          case TOKEN:
            break;
        }
      }
      return false;
    }
  }

  /**
   * Postconditions that apply to variants that delegate to
   * {@link J8NodeType#Primary}.
   */
  public static final class Primary {

    /** */
    public static final Postcond Ambiguous = new Postcond(
        1, PrimaryNode.Variant.Ambiguous);

    /** */
    public static final Postcond ArrayAccess = new Postcond(
        1, PrimaryNode.Variant.ArrayAccess);
    /** */
    public static final Postcond FieldAccess = new Postcond(
        1, PrimaryNode.Variant.FieldAccess);
    /** */
    public static final Postcond InnerClassCreation = new Postcond(
        1, PrimaryNode.Variant.InnerClassCreation);
    /** */
    public static final Postcond MethodInvocation = new Postcond(
        1, PrimaryNode.Variant.MethodInvocation);

    /**
     * Postconditions that apply to Primaries that have no PostOps and which
     * check the {@link J8NodeType#ExpressionAtom}'s variant.
     */
    public static final class ExpressionAtom {
      /** */
      public static final Postcond FreeField = new Postcond(
          2, ExpressionAtomNode.Variant.FreeField);
      /** */
      public static final Postcond Local = new Postcond(
          2, ExpressionAtomNode.Variant.Local);
      /** */
      @SuppressWarnings("hiding")
      public static final Postcond MethodInvocation = new Postcond(
          2, ExpressionAtomNode.Variant.MethodInvocation);
      /** */
      public static final Postcond UnqualifiedClassInstanceCreationExpression =
          new Postcond(
              2,
              ExpressionAtomNode.Variant
              .UnqualifiedClassInstanceCreationExpression);
    }
  }
}
