package com.mikesamuel.cil.ptree;

import java.util.BitSet;

import com.google.common.collect.ImmutableList;
import com.mikesamuel.cil.ast.NodeVariant;
import com.mikesamuel.cil.ast.j8.ContextFreeNameNode;
import com.mikesamuel.cil.event.Debug;
import com.mikesamuel.cil.event.Event;
import com.mikesamuel.cil.parser.LeftRecursion;
import com.mikesamuel.cil.parser.ParSerable;
import com.mikesamuel.cil.parser.ParseErrorReceiver;
import com.mikesamuel.cil.parser.ParseResult;
import com.mikesamuel.cil.parser.ParseState;
import com.mikesamuel.cil.parser.SList;

final class MagicDotIdentifierHandler extends Concatenation {

  static ParSerable of(Iterable<? extends ParSerable> els) {
    for (ParSerable el : els) {
      if (el == Alternation.NULL_LANGUAGE) {
        return el;
      }
    }
    return new MagicDotIdentifierHandler(els);
  }

  private MagicDotIdentifierHandler(Iterable<? extends ParSerable> els) {
    super(ImmutableList.copyOf(els));
  }

  private static final boolean DEBUG = false;

  @Override
  public ParseResult parse(
      ParseState state, LeftRecursion lr, ParseErrorReceiver err) {
    ParseResult greedy = super.parse(state, lr, err);
    if (greedy.synopsis == ParseResult.Synopsis.SUCCESS) {
      return greedy;
    }
    ParseResult failure = greedy;

    if (DEBUG) {
      System.err.println("Magic happening");
      Debug.dumpEvents(SList.forwardIterable(state.output));
    }

    // Look for push(ContextFreeNameNode...)
    // Look for the preceding diamond.
    BitSet textAfterPop = new BitSet();
    int popDepth = 0;
    boolean sawText = false;
    SList<Event> tailInReverse = null;

    // If there is not a pop that completes a variant we can borrow from then we
    // can early out.
    borrow_loop:
    for (SList<Event> c = state.output; c != null; c = c.prev) {
      Event e = c.x;
      tailInReverse = SList.append(tailInReverse, e);
      switch (e.getKind()) {
        case POP:
          if (sawText) {
            int popIndex = popDepth >= 0 ? popDepth * 2 : (~popDepth * 2) + 1;
            textAfterPop.set(popIndex);
          }
          ++popDepth;
          break;
        case PUSH:
          // We allow things to go negative.
          --popDepth;
          NodeVariant<?, ?> pushVariant = e.getNodeVariant();
          if (pushVariant == ContextFreeNameNode.Variant.Name) {
            int popIndex = popDepth >= 0 ? popDepth * 2 : (~popDepth * 2) + 1;
            if (!textAfterPop.get(popIndex)) {
              if (c.prev != null
                  && c.prev.x.getKind() == Event.Kind.TOKEN) {
                Event prevTok = c.prev.x;
                if (".".equals(prevTok.getContent())) {
                  ParseState borrowState = borrow(
                      state, c.prev.prev, prevTok.getContentIndex(),
                      tailInReverse);
                  ParseResult borrowResult = super.parse(borrowState, lr, err);
                  switch (borrowResult.synopsis) {
                    case FAILURE:
                      return failure;
                    case SUCCESS:
                      return ParseResult.success(
                          borrowResult.next(),
                          prevTok.getContentIndex(),
                          ParseResult.union(
                              failure.lrExclusionsTriggered,
                              borrowResult.lrExclusionsTriggered));
                  }
                }
              }
            }
            break borrow_loop;
          }
          break;
        case CONTENT:
        case DELAYED_CHECK:
        case IGNORABLE:
        case LR_END:
        case LR_START:
        case POSITION_MARK:
        case TOKEN:
          if (e.nCharsConsumed() != 0) {
            sawText = true;
          }
          break;
      }
    }
    return failure;
  }

  private static ParseState borrow(
      ParseState state, SList<Event> beforeDot, int dotIndex,
      SList<Event> contextFreeNamePushAndAfterInReverse) {
    SList<Event> outputWithoutLastIdentifierOrDot = beforeDot;
    int pushDepth = 0;
    boolean skippedOverContextFreeName = false;
    for (SList<Event> c = contextFreeNamePushAndAfterInReverse; c != null;
         c = c.prev) {
      Event e = c.x;
      if (skippedOverContextFreeName) {
        outputWithoutLastIdentifierOrDot = SList.append(
            outputWithoutLastIdentifierOrDot, e);
      }
      switch (e.getKind()) {
        case CONTENT:
        case DELAYED_CHECK:
        case IGNORABLE:
        case LR_END:
        case LR_START:
        case POSITION_MARK:
        case TOKEN:
          break;
        case POP:
          --pushDepth;
          if (pushDepth == 0) {
            skippedOverContextFreeName = true;
          }
          break;
        case PUSH:
          ++pushDepth;
          break;
      }
    }

    return state.withIndex(dotIndex)
        .withOutput(outputWithoutLastIdentifierOrDot);
  }
}
