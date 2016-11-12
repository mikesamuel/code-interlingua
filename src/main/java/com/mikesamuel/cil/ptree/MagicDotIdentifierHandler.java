package com.mikesamuel.cil.ptree;

import java.util.BitSet;

import com.google.common.collect.ImmutableList;
import com.mikesamuel.cil.ast.ContextFreeNameNode;
import com.mikesamuel.cil.ast.MatchEvent;
import com.mikesamuel.cil.ast.MatchEvent.Push;
import com.mikesamuel.cil.parser.Chain;
import com.mikesamuel.cil.parser.LeftRecursion;
import com.mikesamuel.cil.parser.ParSerable;
import com.mikesamuel.cil.parser.ParseErrorReceiver;
import com.mikesamuel.cil.parser.ParseResult;
import com.mikesamuel.cil.parser.ParseState;

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
      StringBuilder sb = new StringBuilder(". ");
      for (MatchEvent e : Chain.forwardIterable(state.output)) {
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

    // Look for push(ContextFreeNameNode...)
    // Look for the preceding diamond.
    BitSet textAfterPop = new BitSet();
    int popDepth = 0;
    boolean sawText = false;
    Chain<MatchEvent> tailInReverse = null;

    // If there is not a pop that completes a variant we can borrow from then we
    // can early out.
    for (Chain<MatchEvent> c = state.output; c != null; c = c.prev) {
      MatchEvent e = c.x;
      tailInReverse = Chain.append(tailInReverse, e);
      if (e instanceof MatchEvent.Pop) {
        if (sawText) {
          int popIndex = popDepth >= 0 ? popDepth * 2 : (~popDepth * 2) + 1;
          textAfterPop.set(popIndex);
        }
        ++popDepth;
      } else if (e instanceof MatchEvent.Push) {
        // We allow things to go negative.
        --popDepth;
        MatchEvent.Push push = (Push) e;
        if (push.variant == ContextFreeNameNode.Variant.Name) {
          int popIndex = popDepth >= 0 ? popDepth * 2 : (~popDepth * 2) + 1;
          if (!textAfterPop.get(popIndex)) {
            if (c.prev != null && c.prev.x instanceof MatchEvent.Token) {
              MatchEvent.Token prevTok = (MatchEvent.Token) c.prev.x;
              if (".".equals(prevTok.content)) {
                ParseState borrowState = borrow(
                    state, c.prev.prev, prevTok.index, tailInReverse);
                ParseResult borrowResult = super.parse(borrowState, lr, err);
                switch (borrowResult.synopsis) {
                  case FAILURE:
                    return failure;
                  case SUCCESS:
                    return ParseResult.success(
                        borrowResult.next(),
                        true,
                        ParseResult.union(
                            failure.lrExclusionsTriggered,
                            borrowResult.lrExclusionsTriggered));
                }
              }
            }
          }
          break;
        }
      } else if (e.nCharsConsumed() != 0) {
        sawText = true;
      }
    }
    return failure;
  }

  private static ParseState borrow(
      ParseState state, Chain<MatchEvent> beforeDot, int dotIndex,
      Chain<MatchEvent> contextFreeNamePushAndAfterInReverse) {
    Chain<MatchEvent> outputWithoutLastIdentifierOrDot = beforeDot;
    int pushDepth = 0;
    boolean skippedOverContextFreeName = false;
    for (Chain<MatchEvent> c = contextFreeNamePushAndAfterInReverse; c != null;
         c = c.prev) {
      MatchEvent e = c.x;
      if (skippedOverContextFreeName) {
        outputWithoutLastIdentifierOrDot = Chain.append(
            outputWithoutLastIdentifierOrDot, e);
      }
      if (e instanceof MatchEvent.Push) {
        ++pushDepth;
      } else if (e instanceof MatchEvent.Pop) {
        --pushDepth;
        if (pushDepth == 0) {
          skippedOverContextFreeName = true;
        }
      }
    }

    return state.withIndex(dotIndex)
        .withOutput(outputWithoutLastIdentifierOrDot);
  }
}
