package com.mikesamuel.cil.ptree;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.mikesamuel.cil.ast.AmbiguousNameNode;
import com.mikesamuel.cil.ast.ExpressionNameNode;
import com.mikesamuel.cil.ast.IdentifierNode;
import com.mikesamuel.cil.ast.MatchEvent;
import com.mikesamuel.cil.ast.NodeVariant;
import com.mikesamuel.cil.ast.PackageNameNode;
import com.mikesamuel.cil.ast.PackageOrTypeNameNode;
import com.mikesamuel.cil.ast.TypeNameNode;
import com.mikesamuel.cil.parser.Chain;
import com.mikesamuel.cil.parser.ParSerable;
import com.mikesamuel.cil.parser.ParseErrorReceiver;
import com.mikesamuel.cil.parser.ParseState;

final class MagicDotIdentifierHandler extends Concatenation {

  static MagicDotIdentifierHandler of(Iterable<? extends ParSerable> els) {
    return new MagicDotIdentifierHandler(els);
  }

  private MagicDotIdentifierHandler(Iterable<? extends ParSerable> els) {
    super(ImmutableList.copyOf(els));
  }

  static final class MagicVariants {
    /**
     * Variants that we can borrow trailing identifiers from, but which do
     * not directly have a trailing identifier.
     *
     * These left-call a combining variant but are not themselves
     * left-recursive.
     */
    private static final ImmutableSet<NodeVariant> CAN_BORROW_FROM =
        ImmutableSet.<NodeVariant>of(
            TypeNameNode.Variant.PackageOrTypeNameDotIdentifier
            );

    /**
     * Left-associative name variants that we can borrow identifiers from.
     * They must have the form
     * <pre>
     * ThisName:
     *     ThisName "." Identifier
     * </pre>
     */
    private static final ImmutableSet<NodeVariant> COMBINING_VARIANTS =
        ImmutableSet.<NodeVariant>of(
            AmbiguousNameNode.Variant.AmbiguousNameDotIdentifier,
            ExpressionNameNode.Variant.ExpressionNameDotIdentifier,
            PackageNameNode.Variant.PackageNameDotIdentifier,
            PackageOrTypeNameNode.Variant.PackageOrTypeNameDotIdentifier
            );

    /**
     * Variants that simply delegate to {@link NodeType.Identifier}.
     */
    private static final ImmutableSet<NodeVariant> LEAF_VARIANTS =
        ImmutableSet.<NodeVariant>of(
            AmbiguousNameNode.Variant.Identifier,
            ExpressionNameNode.Variant.Identifier,
            PackageNameNode.Variant.Identifier,
            PackageOrTypeNameNode.Variant.Identifier
            );

    static boolean allowedInBorrowSubtree(NodeVariant v) {
      return (COMBINING_VARIANTS.contains(v)
          || LEAF_VARIANTS.contains(v)
          || CAN_BORROW_FROM.contains(v));
    }
  }

  private static final boolean DEBUG = false;

  @SuppressWarnings("synthetic-access")
  @Override
  public Optional<ParseState> parse(ParseState state, ParseErrorReceiver err) {
    Optional<ParseState> greedy = super.parse(state, err);
    if (greedy.isPresent()) {
      return greedy;
    }

    // If there is not a pop that completes a variant we can borrow from then we
    // can early out.
    if (state.output == null
        || !(state.output.x instanceof MatchEvent.Pop)) {
      return Optional.absent();
    }

    int countOfIdentifiers = 0;
    Chain<MatchEvent> lastPushInReverse = null;
    int popCount = 0;
    Chain<MatchEvent> beforeName = null;

    for (Chain<MatchEvent> c = state.output; ; c = c.prev) {
      Preconditions.checkNotNull(c);  // Unmatched push/pop
      MatchEvent e = c.x;
      lastPushInReverse = Chain.<MatchEvent>append(lastPushInReverse, e);
      if (e instanceof MatchEvent.Push) {
        Preconditions.checkState(popCount > 0);
        --popCount;
        NodeVariant v = ((MatchEvent.Push) e).variant;
        if (v == IdentifierNode.Variant.Builtin) {
          ++countOfIdentifiers;
        } else if (!MagicVariants.allowedInBorrowSubtree(v)) {
          if (DEBUG) { System.err.println("***" + v); }
          return Optional.absent();
        }
        if (popCount == 0) {
          beforeName = c.prev;
          break;
        }
      } else if (e instanceof MatchEvent.Pop) {
        ++popCount;
      } else if (e instanceof MatchEvent.LRSuffix
                 || e instanceof MatchEvent.LRStart) {
        // Parsing an LR name production after having found a seed so we
        // shouldn't expect to find a
        return Optional.absent();
      }
    }
    if (countOfIdentifiers < 2) {
      // We cannot borrow one and leave a well-formed name.
      return Optional.absent();
    }
    if (DEBUG) {
      System.err.println("countOfIdentifiers=" + countOfIdentifiers);
      System.err.println("lastPushInReverse");
      for (MatchEvent e : Chain.reverse(lastPushInReverse)) {
        System.err.println("\t" + e);
      }
      System.err.println("beforeName");
      for (MatchEvent e : Chain.forward(beforeName)) {
        System.err.println("\t" + e);
      }
    }

    Preconditions.checkNotNull(
        lastPushInReverse);  // Because loop above always runs once.
    Preconditions.checkState(
        lastPushInReverse.x instanceof MatchEvent.Push);

    while (lastPushInReverse != null
           && lastPushInReverse.x instanceof MatchEvent.Push
           && MagicVariants.CAN_BORROW_FROM.contains(
               ((MatchEvent.Push) lastPushInReverse.x).variant)) {
      beforeName = Chain.append(beforeName, lastPushInReverse.x);
      lastPushInReverse = lastPushInReverse.prev;
    }

    if (lastPushInReverse == null
        || !(lastPushInReverse.x instanceof MatchEvent.Push)) {
      return Optional.absent();
    }

    MatchEvent.Push push = (MatchEvent.Push) lastPushInReverse.x;
    if (DEBUG) {
      System.err.println("combining variant " + push.variant);
    }
    if (!MagicVariants.COMBINING_VARIANTS.contains(push.variant)) {
      return Optional.absent();
    }

    int popsToSkipAtEnd = 1;
    Chain<? extends MatchEvent> toReplay = lastPushInReverse.prev;

    Chain<MatchEvent> outputWithBorrow = beforeName;
    for (; toReplay != null; toReplay = toReplay.prev) {
      outputWithBorrow = Chain.append(outputWithBorrow, toReplay.x);
    }

    for (int i = 0; i < popsToSkipAtEnd; ++i) {
      Preconditions.checkNotNull(outputWithBorrow);
      Preconditions.checkState(
          outputWithBorrow.x instanceof MatchEvent.Pop);
      outputWithBorrow = outputWithBorrow.prev;
    }

    if (DEBUG) {
      System.err.println("outputWithBorrow");
      for (MatchEvent e : Chain.forward(outputWithBorrow)) {
        System.err.println("\t" + e);
      }
    }

    return Optional.of(state.withOutput(outputWithBorrow));
  }
}
