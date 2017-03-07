package com.mikesamuel.cil.template;

import java.util.Arrays;
import java.util.BitSet;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.mikesamuel.cil.ast.NodeType;
import com.mikesamuel.cil.ast.NodeTypeTables;
import com.mikesamuel.cil.ast.NodeVariant;
import com.mikesamuel.cil.ast.TemplateDirectiveNode;
import com.mikesamuel.cil.ast.TemplateDirectivesNode;
import com.mikesamuel.cil.event.Debug;
import com.mikesamuel.cil.event.Event;
import com.mikesamuel.cil.parser.Input;
import com.mikesamuel.cil.parser.SourcePosition;

/**
 * Utilities for dealing with parsed templates.
 */
public final class Templates {

  private Templates() {
    // Static API
  }

  static final boolean DEBUG = false;

  /**
   * Reworks a parse event stream to make sure that interpolations appear in the
   * most general context possible, and that directives bracket
   * complete sub-trees.
   */
  public static ImmutableList<Event> postprocess(
      Input input, Iterable<? extends Event> events)
  throws IllegalArgumentException {
    return hoistTemplateDecls(generalize(input, events));
  }

  private static ImmutableList<Event> generalize(
      Input input, Iterable<? extends Event> events)
  throws IllegalArgumentException {

    // We have three goals here.
    // 1. Make sure that interpolations are in as general a position as
    //    possible.
    // 2. Make sure that start directives like (%%template...{) are at the same
    //    depth as end directives (%}) and that in-between is a forest
    //    (no node at a lower-depth between them and only properly nested
    //    start/end directives between.
    // 3. Make sure that start/end directive pairs are in as general a position
    //    as possible.
    //
    // The first means counting pushes before and pops after and consuming the
    // min of each.  The process of interpolating values into a parse tree
    // involves recreating intermediate inner nodes as necessary.
    //
    // The second involves matching starts with ends, checking the stuff in
    // the middle.  Since we eagerly parse directives, this also usually
    // involves moving the end right past a number of pops equal to the
    // difference in depths, but might also involve moving the left right past
    // some pushes when the end cannot be moved past an intervening token
    // as often happens with variants that are terminated by semicolons instead
    // of being delimited by token pairs.
    //
    // The third, similar to the first, involves counting pushes before each
    // start, pops after the corresponding end, and shifting the nodes by the
    // min amount without consuming any pushes or pops.

    @SuppressWarnings("synthetic-access")
    TemplateDirectiveEnvelopeFilter f = new TemplateDirectiveEnvelopeFilter();
    ImmutableList<Event> eventList = f.filter(events);
    if (!f.hasNonStandard) {
      return eventList;
    }

    if (DEBUG) {
      Debug.dumpEvents(eventList);
    }

    List<Events> chain = Lists.newArrayList();
    {
      // We number push/pop pairs with serial numbers so that we can easily tell
      // whether moving a start directive and an end directive into a particular
      // position would end up with them in the same AST node.
      int nodeIndexCounter = 0;
      int currentNodeIndex = 0;
      // A stack of int pairs for each push that has not been popped yet.
      //   [nodeIndex0, indexOfPush0InEventList,
      //    nodeIndex1, indexOfPush1InEventList, ...]
      List<Integer> nodeAndPushIndices = Lists.newArrayList();

      int nonStandardAncestorDepth = -1;
      for (int i = 0, n = eventList.size(); i < n; ++i) {
        Event e = eventList.get(i);
        Events toAdd = null;
        switch (e.getKind()) {
          case POP: {
            int size = nodeAndPushIndices.size();
            Preconditions.checkState(size != 0);
            int pushEventIndex = nodeAndPushIndices.remove(size - 1);
            @SuppressWarnings("unused")
            int pushedNodeIndex = nodeAndPushIndices.remove(size - 2);
            int nodeIndexBeforePop = currentNodeIndex;
            int nodeIndexAfterPop = size != 2
                ? nodeAndPushIndices.get(size - 4)
                : 0;
            currentNodeIndex = nodeIndexAfterPop;
            if ((size - 2) / 2 == nonStandardAncestorDepth) {
              nonStandardAncestorDepth = -1;
              toAdd = new NonstandardEvents(
                  eventList.subList(pushEventIndex, i + 1));
            } else if (nonStandardAncestorDepth >= 0) {
              continue;
            } else {
              toAdd = new StandardEvents(
                  nodeIndexBeforePop, nodeIndexAfterPop, e);
            }
            break;
          }
          case PUSH: {
            int nodeIndexBeforePush = currentNodeIndex;
            int nodeIndexAfterPush = ++nodeIndexCounter;
            currentNodeIndex = nodeIndexAfterPush;
            NodeType nt = e.getNodeType();
            if (nonStandardAncestorDepth < 0
                && NodeTypeTables.TEMPLATEINSTR.contains(nt)) {
              nonStandardAncestorDepth = nodeAndPushIndices.size() / 2;
            }
            nodeAndPushIndices.add(currentNodeIndex);
            nodeAndPushIndices.add(i);
            if (nonStandardAncestorDepth >= 0) {
              // Will add when the pop corresponding to the original
              // element is found.
              continue;
            }
            toAdd = new StandardEvents(
                nodeIndexBeforePush, nodeIndexAfterPush, e);
            break;
          }
          case CONTENT:
          case IGNORABLE:
          case TOKEN:
            if (nonStandardAncestorDepth >= 0) {
              continue;
            }
            toAdd = new StandardEvents(
                currentNodeIndex, currentNodeIndex, e);
            break;
          case DELAYED_CHECK:
          case LR_END:
          case LR_START:
          case POSITION_MARK:
            throw new IllegalArgumentException(e.toString());
        }
        Preconditions.checkNotNull(toAdd);
        chain.add(toAdd);
      }
      Preconditions.checkState(
          nonStandardAncestorDepth < 0 && currentNodeIndex == 0);
    }

    if (DEBUG) {
      System.err.println("Events");
      for (Events es : chain) {
        System.err.println("\t" + es);
      }
    }
    // If the event list were empty we should have early outed above.
    Preconditions.checkState(!chain.isEmpty());

    {
      List<NonstandardEvents> starts = Lists.newArrayList();
      for (Events es : chain) {
        if (es instanceof NonstandardEvents) {
          NonstandardEvents nes = (NonstandardEvents) es;
          switch (nes.k) {
            case START:
              starts.add(nes);
              break;
            case END:
              int size = starts.size();
              Preconditions.checkState(size != 0);
              NonstandardEvents start = starts.remove(size - 1);
              nes.dual = start;
              start.dual = nes;
              break;
            case INFIX:
              break;
          }
        }
      }
      Preconditions.checkState(starts.isEmpty());
    }

    // Now we rebalance which means allowing the Runs for template elements to
    // look at the pushes and pops around them and maybe splice off portions of
    // the event list and moving them to either side.

    // First we rebalance the start/end pairs.
    // Those prefer to shift starts left and ends right and since the
    // rebalance proceeds from left to right, that leaves the most space for
    // subsequent rebalances.
    // This is a poor-man's constraint satisfaction, but I'm not good at
    // writing sats.
    chain.get(0).rebalance(0, chain, DirectiveKind.START, input);
    // Next, we rebalance infix operators.  This generalizes
    chain.get(0).rebalance(0, chain, DirectiveKind.INFIX, input);
    // Rebalancing the start takes care of rebalancing the corresponding ends.

    boolean inDirectives = false;
    ImmutableList.Builder<Event> rebalanced = ImmutableList.builder();
    for (Events es : chain) {
      ImmutableList<Event> esEvents = es.events();
      if (es instanceof NonstandardEvents) {
        // Recurse to handle nested template instructions.
        int nEvents = esEvents.size();
        Preconditions.checkState(nEvents >= 2);
        esEvents = ImmutableList.<Event>builder()
            .add(esEvents.get(0))  // The push
            .addAll(generalize(input, esEvents.subList(1, nEvents - 2)))
            .add(esEvents.get(nEvents - 1))  // The corresponding pop
            .build();
      }
      Event esEvent0 = esEvents.get(0);
      boolean isDirective = esEvent0.getKind() == Event.Kind.PUSH
          && esEvent0.getNodeType() == NodeType.TemplateDirective;
      if (isDirective != inDirectives) {
        rebalanced.add(isDirective ? PUSH_TEMPLATE_DIRECTIVES : POP);
        inDirectives = isDirective;
      }
      rebalanced.addAll(esEvents);
    }
    if (inDirectives) { rebalanced.add(POP); }
    return rebalanced.build();
  }

  static final Event PUSH_TEMPLATE_DIRECTIVES = Event.push(
      TemplateDirectivesNode.Variant.TemplateDirectiveTemplateDirective);

  static final Event POP = Event.pop();


  private static class TemplateDirectiveEnvelopeFilter {
    boolean hasNonStandard;

    ImmutableList<Event> filter(Iterable<? extends Event> events) {
      // Our TemplateDirectives look like
      //     push TemplateDirectives
      //       push TemplateDirective
      //         "%%"
      //         ...
      //       pop
      //     pop
      // Later in the generalize process, we move the directives around, and we
      // avoid moving past a token as this respects the user's intent as to
      // where they place the directive.
      //
      // The push and pop of TemplateDirectives get in the way of this because
      // we might want to push different directives in different directions.
      //
      // Here we remove each
      //    (push TemplateDirectives)
      // and its corresponding
      //    (pop)
      //
      // Later in the process we rewrap runs of TemplateDirective nodes in
      // TemplateDirectives.
      ImmutableList.Builder<Event> b = ImmutableList.builder();
      int depth = 0;
      BitSet pushRemoved = new BitSet();
      for (Event e : events) {
        switch (e.getKind()) {
          case POP:
            Preconditions.checkState(depth != 0);
            --depth;
            if (pushRemoved.get(depth)) {
              continue;
            }
            break;
          case PUSH:
            NodeType nt = e.getNodeType();
            if (NodeTypeTables.NONSTANDARD.contains(nt)) {
              this.hasNonStandard = true;
            }
            boolean removed = nt == NodeType.TemplateDirectives;
            pushRemoved.set(depth, removed);
            ++depth;
            if (removed) {
              continue;
            }
            break;
          case TOKEN:
          case CONTENT:
          case IGNORABLE:
          case DELAYED_CHECK:
          case POSITION_MARK:
            break;
          case LR_END:
          case LR_START:
            throw new IllegalArgumentException(e.toString());
        }
        b.add(e);
      }
      return b.build();
    }
  }

  enum DirectiveKind {
    START,
    INFIX,
    END,
  }

  enum Bias {
    LEFT,
    NONE,
    RIGHT,
  }

  static abstract class Events {
    Events() {}

    abstract ImmutableList<Event> events();

    void rebalance(
        int pos, List<Events> chain,
        DirectiveKind k, Input input) {
      Preconditions.checkState(chain.get(pos) == this);
      if (pos + 1 < chain.size()) {
        chain.get(pos + 1).rebalance(pos + 1, chain, k, input);
      }
    }
  }

  static final class StandardEvents extends Events {
    final Event e;
    final int nodeIndexLeft;
    final int nodeIndexRight;

    StandardEvents(int nodeIndexLeft, int nodeIndexRight, Event e) {
      this.nodeIndexLeft = nodeIndexLeft;
      this.nodeIndexRight = nodeIndexRight;
      this.e = e;
    }

    @Override
    ImmutableList<Event> events() {
      return ImmutableList.of(e);
    }

    @Override
    public String toString() {
      return e.toString() + "#"
          + (nodeIndexLeft == nodeIndexRight
             ? Integer.toString(nodeIndexLeft)
             : nodeIndexLeft + "/" + nodeIndexRight);
    }
  }

  static final class NonstandardEvents extends Events {
    final ImmutableList<Event> events;
    final DirectiveKind k;
    /** For a start, its corresponding end, and vice-versa or null if infix. */
    NonstandardEvents dual;

    NonstandardEvents(ImmutableList<Event> events) {
      this.events = events;
      Preconditions.checkArgument(!events.isEmpty());
      Event event0 = events.get(0);
      Preconditions.checkState(event0.getKind() == Event.Kind.PUSH);
      DirectiveKind dk = null;
      switch (event0.getNodeType()) {
        case TemplateDirective:
          TemplateDirectiveNode.Variant v =
              (TemplateDirectiveNode.Variant) event0.getNodeVariant();
          if (v.isTemplateStart()) {
            dk = v.isTemplateEnd()
                ? dk = DirectiveKind.INFIX  // TODO: need to readjust later
                : DirectiveKind.START;
          } else if (v.isTemplateEnd()) {
            dk = DirectiveKind.END;
          } else {
            dk = DirectiveKind.INFIX;
          }
          break;
        case TemplateInterpolation:
          dk = DirectiveKind.INFIX;
          break;
        default:
          throw new IllegalArgumentException(event0.toString());
      }
      this.k = Preconditions.checkNotNull(dk);
    }

    @Override
    ImmutableList<Event> events() {
      return events;
    }

    @Override
    void rebalance(
        int pos, List<Events> chain,
        DirectiveKind kindToRebalance, Input input) {
      Preconditions.checkState(chain.get(pos) == this);

      int i = pos;
      if (this.k == kindToRebalance) {
        switch (this.k) {
          case INFIX: {
            int nPushes = nLeft(i - 1, chain, EnumSet.of(Event.Kind.PUSH));
            int nPops = nRight(i + 1, chain, EnumSet.of(Event.Kind.POP));
            int nToRemove = Math.min(nPushes, nPops);  // Number of pairs
            if (nToRemove != 0) {
              List<Events> region = chain.subList(
                  i - nToRemove, i + nToRemove + 1);
              // Splice out the pushes and pops.
              region.clear();
              region.add(this);
              i -= nToRemove;
              Preconditions.checkState(chain.get(i) == this);
            }
            break;
          }
          case START: {
            NonstandardEvents start = this;
            NonstandardEvents end = dual;
            int startIndex = pos;
            int endIndex = chain.indexOf(dual);
            Preconditions.checkState(
                end.k == DirectiveKind.END && end.dual == start
                && endIndex > startIndex);
            Deltas startDeltas = new Deltas(startIndex, chain);
            Deltas endDeltas = new Deltas(endIndex, chain);

            if (DEBUG) {
              System.err.println("StartDeltas=" + startDeltas);
              System.err.println("EndDeltas=" + endDeltas);
            }

            int startDeltaLimit = startDeltas.maxAbsDelta();
            int endDeltaLimit = endDeltas.maxAbsDelta();

            Map<Integer, Integer> nodeIndexToEndDelta = Maps.newHashMap();
            for (int deltaAbs = 0; deltaAbs <= endDeltaLimit; ++deltaAbs) {
              Integer nodeIndexForNegDelta =
                  endDeltas.nodeIndexForDelta(-deltaAbs);
              if (nodeIndexForNegDelta >= 0) {
                if (!nodeIndexToEndDelta.containsKey(nodeIndexForNegDelta)) {
                  nodeIndexToEndDelta.put(nodeIndexForNegDelta, -deltaAbs);
                }
              }

              Integer nodeIndexForPosDelta =
                  endDeltas.nodeIndexForDelta(deltaAbs);
              if (nodeIndexForPosDelta >= 0) {
                if (!nodeIndexToEndDelta.containsKey(nodeIndexForPosDelta)) {
                  nodeIndexToEndDelta.put(nodeIndexForPosDelta, deltaAbs);
                }
              }
            }

            int bestStartDelta = -1;
            int bestEndDelta = -1;
            int newNodeIndex = -1;
            for (int startDelta = 0; startDelta >= -startDeltaLimit;
                --startDelta) {
              int ni = startDeltas.nodeIndexForDelta(startDelta);
              if (ni >= 0) {
                Integer endDelta = nodeIndexToEndDelta.get(ni);
                if (endDelta != null) {
                  bestEndDelta = endDelta;
                  bestStartDelta = startDelta;
                  newNodeIndex = ni;
                  break;
                }
              }
            }

            if (bestStartDelta < 0) {
              for (int startDelta = 0; startDelta <= startDeltaLimit;
                   ++startDelta) {
                int ni = startDeltas.nodeIndexForDelta(startDelta);
                if (ni >= 0) {
                  Integer endDelta = nodeIndexToEndDelta.get(ni);
                  if (endDelta != null) {
                    bestEndDelta = endDelta;
                    bestStartDelta = startDelta;
                    newNodeIndex = ni;
                    break;
                  }
                }
              }
            }

            if (bestStartDelta < 0) {
              // Cannot satisfy
              throw new IllegalArgumentException(
                  "Cannot satisfy start/end pair at " + start.getPosition(input)
                  + " / " + end.getPosition(input));
            }
            if (DEBUG) {
              System.err.println(
                  "Shifting start @ " + start.getPosition(input)
                  + " by " + bestStartDelta + " to #" + newNodeIndex
                  + " and end @ " + end.getPosition(input)
                  + " by " + bestEndDelta);
            }
            startDeltas.pushAround(startIndex, chain, bestStartDelta);
            endDeltas.pushAround(endIndex, chain, bestEndDelta);
            i += bestStartDelta;
            break;
          }
          case END:
            // Handled by START
            break;
        }
      }
      // Recurse right.
      super.rebalance(i, chain, kindToRebalance, input);
    }


    private SourcePosition getPosition(Input input) {
      for (Event e : events) {
        switch (e.getKind()) {
          case TOKEN:
          case CONTENT:
          case IGNORABLE:
            int idx = e.getContentIndex();
            return input.getSourcePosition(idx, idx + e.getContent().length());
          case DELAYED_CHECK:
          case LR_END:
          case LR_START:
          case POP:
          case POSITION_MARK:
          case PUSH:
            continue;
        }
        throw new AssertionError(e.getKind());
      }
      throw new Error(this.toString());  // TODO: Implement me
    }

    @Override
    public String toString() {
      return events.toString();
    }
  }

  static int nLeft(int pos, List<Events> chain, EnumSet<Event.Kind> kinds) {
    int n = 0;
    for (int i = pos; i >= 0; --i) {
      Events es = chain.get(i);
      if (es instanceof StandardEvents) {
        Event.Kind ek = ((StandardEvents) es).e.getKind();
        if (kinds.contains(ek)) {
          n += 1;
          continue;
        }
      }
      break;
    }
    return n;
  }

  static int nRight(int pos, List<Events> chain, EnumSet<Event.Kind> kinds) {
    int n = 0;
    for (int i = pos, chainSize = chain.size(); i < chainSize; ++i) {
      Events es = chain.get(i);
      if (es instanceof StandardEvents) {
        Event.Kind ek = ((StandardEvents) es).e.getKind();
        if (kinds.contains(ek)) {
          n += 1;
          continue;
        }
      }
      break;
    }
    return n;
  }

  static final class Deltas {
    /** node index by deltas packed thus [0, -1, 1, -2, 2, -3, 4, ...] */
    private final int[] deltaToNodeIndex;
    private final int countToPushLeft;
    private final int countToPushRight;

    Deltas(int pos, List<Events> chain) {
      NonstandardEvents nes = (NonstandardEvents) chain.get(pos);
      if (DEBUG) {
        System.err.println("Computing deltas for " + nes + " at " + pos);
      }

      // Assume we can push infix elements around.
      int nToPushLeft = 0;
      int leftIndex = pos - 1;
      while (leftIndex >= 0) {
        Events left = chain.get(leftIndex);
        if (left instanceof NonstandardEvents
             && ((NonstandardEvents) left).k == DirectiveKind.INFIX) {
          --leftIndex;
          ++nToPushLeft;
        } else {
          break;
        }
      }
      if (DEBUG) {
        System.err.println(
            "\tleftIndex=" + leftIndex + ", nToPushLeft=" + nToPushLeft);
      }

      int rightIndex = pos + 1;
      // Assume we can push infix and start elements right.
      int nToPushRight = 0;
      while (rightIndex < chain.size()) {
        Events right = chain.get(rightIndex);
        if (right instanceof NonstandardEvents) {
          DirectiveKind rightKind = ((NonstandardEvents) right).k;
          if (rightKind == DirectiveKind.INFIX
              || rightKind == DirectiveKind.START
              // if rightKind is END and nes is a START, then it's nes's end so
              // we will conclude that delta=0 is fine above.
              // An end cannot push an another end right because that end
              // corresponds to an earlier end so doing so would invalidate a
              // decision committed to earlier.
              ) {
            ++rightIndex;
            ++nToPushRight;
            continue;
          }
        }
        break;
      }
      if (DEBUG) {
        System.err.println(
            "\trightIndex=" + rightIndex + ", nToPushRight=" + nToPushRight);
      }

      EnumSet<Event.Kind> popsAndPushes = EnumSet.of(
          Event.Kind.POP, Event.Kind.PUSH);
      int nPopsAndPushesLeft = nLeft(leftIndex, chain, popsAndPushes);
      int nPopsAndPushesRight = nRight(rightIndex, chain, popsAndPushes);
      if (DEBUG) {
        System.err.println(
            "\tnPopsAndPushesLeft =" + nPopsAndPushesLeft);
        System.err.println(
            "\tnPopsAndPushesRight=" + nPopsAndPushesRight);
      }

      Events left = leftIndex >= 0 ? chain.get(leftIndex) : null;
      Events right = rightIndex < chain.size() ? chain.get(rightIndex) : null;
      int[] deltaToNodeIndexArray = new int[
          1 + Math.max(nPopsAndPushesLeft, nPopsAndPushesRight) * 2];
      Arrays.fill(deltaToNodeIndexArray, -1);
      deltaToNodeIndexArray[0] =
          nPopsAndPushesLeft != 0
          ? ((StandardEvents) Preconditions.checkNotNull(left)).nodeIndexRight
          : nPopsAndPushesRight != 0
          ? ((StandardEvents) Preconditions.checkNotNull(right)).nodeIndexLeft
          : -1;
      {
        int k = 1;  // Odds are negative (left) deltas
        for (int i = leftIndex; k / 2 < nPopsAndPushesLeft; --i, k += 2) {
          Events es = chain.get(i);
          deltaToNodeIndexArray[k] = ((StandardEvents) es).nodeIndexLeft;
        }
      }
      {
        int k = 2;  // Non-zero evens are (right) deltas
        for (int i = rightIndex; k / 2 <= nPopsAndPushesRight; ++i, k += 2) {
          Events es = chain.get(i);
          deltaToNodeIndexArray[k] = ((StandardEvents) es).nodeIndexRight;
        }
      }

      this.deltaToNodeIndex = deltaToNodeIndexArray;
      this.countToPushLeft = nToPushLeft;
      this.countToPushRight = nToPushRight;
    }

    public void pushAround(int pos, List<Events> chain, int delta) {
      int startOfRegion;
      int endOfRegion;
      if (delta < 0) {
        startOfRegion = pos - this.countToPushLeft;
        endOfRegion = pos + 1;
      } else if (delta > 0) {
        startOfRegion = pos;
        endOfRegion = pos + 1 + this.countToPushRight;
      } else {
        return;
      }
      shift(chain, startOfRegion, endOfRegion, delta);
    }

    static int keyForDelta(int delta) {
      if (delta < 0) {
        Preconditions.checkState(delta != Integer.MIN_VALUE);
        return 1 + (~delta * 2);
      } else {
        return 2 * delta;
      }
    }

    int maxAbsDelta() {
      return (deltaToNodeIndex.length + 1) / 2;
    }


    int nodeIndexForDelta(int delta) {
      int k = keyForDelta(delta);
      return k < deltaToNodeIndex.length ? deltaToNodeIndex[k] : -1;
    }

    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder();
      sb.append("(Deltas");
      for (int maxAbs = maxAbsDelta(), i = -maxAbs; i <= maxAbs; ++i) {
        int nodeIndex = nodeIndexForDelta(i);
        if (nodeIndex >= 0) {
          sb.append(' ').append(i).append(":#").append(nodeIndex);
        }
      }
      sb.append(')');
      return sb.toString();
    }
  }

  @VisibleForTesting
  static <T> void shift(
      List<T> ls, int startOfRegion, int endOfRegion, int delta) {
    Preconditions.checkArgument(startOfRegion <= endOfRegion);
    if (delta == 0 || startOfRegion == endOfRegion) { return; }
    ImmutableList<T> region = ImmutableList.copyOf(
        ls.subList(startOfRegion, endOfRegion));
    int nToMove = region.size();
    int startOfDestination = startOfRegion + delta;
    int endOfDestination = startOfDestination + nToMove;
    // Shift the stuff that will be clobbered.
    // Two cases.
    // I. delta < 0
    //   0 1 2[3 4 5]6 7 8    shifting 3 by 2
    //          \ \ \
    //            \ \ \
    //   0 1 2 6 7 3 4 5 8
    //
    //   0 1 2[3]4 5 6 7 8   shifting 1 by 3
    //          \
    //           \
    //            \
    //             \
    //              \
    //   0 1 2 4 5 6 3 7 8
    if (delta > 0) {
      for (int i = 0; i < delta; ++i) {
        ls.set(startOfRegion + i, ls.get(endOfRegion + i));
      }
    } else {
      // II. delta > 0
      //   0 1 2[3 4 5]6 7 8    shifting 3 by -2
      //        / / /
      //       / / /
      //   0 3 4 5 1 2 6 7 8
      //
      //   0 1 2 3[4]5 6 7 8    shifting 1 by -3
      //          /
      //         /
      //        /
      //       /
      //      /
      //   0 4 1 2 3 5 6 7 8
      for (int i = delta; ++i <= 0;) {
        ls.set(endOfDestination - i, ls.get(startOfDestination - i));
      }
    }

    for (int i = 0; i < nToMove; ++i) {
      ls.set(startOfDestination + i, region.get(i));
    }
  }

  /**
   * Shift all {@link NodeType#TemplateDecl} blocks to the start of the closest
   * enclosing {@linkplain NodeVariant#isTemplateStart template start} so that
   * every use of a template occurs after (in an in-order traversal) the
   * declaration.
   */
  private static ImmutableList<Event> hoistTemplateDecls(
      ImmutableList<Event> events) {
    // For each open template scope, keep the index of the start event,
    // a list of hoisted template declarations, and the rest of the content.
    List<HoistState> stack = Lists.newArrayList();
    HoistState top = new HoistState(null);
    stack.add(top);
    hoist_loop:
    for (int i = 0, n = events.size(); i < n; ++i) {
      Event e = events.get(i);
      if (e.getKind() == Event.Kind.PUSH) {
        NodeVariant v = e.getNodeVariant();
        if (v.isTemplateEnd()) {
          HoistState oldTop = stack.remove(stack.size() - 1);
          top = stack.get(stack.size() - 1);
          top.rest.addAll(oldTop.getAllEvents());
        }
        if (v.isTemplateStart()) {
          HoistState newTop = new HoistState(e);
          stack.add(newTop);
          top = newTop;
          continue hoist_loop;
        }
        if (v == TemplateDirectiveNode.Variant.Function) {
          int depth = 1;
          int end = i + 1;
          for (; ; ++end) {
            Preconditions.checkState(end < n);  // No pop for push
            Event f = events.get(end);
            Event.Kind k = f.getKind();
            if (k == Event.Kind.POP) {
              --depth;
              if (depth == 0) {
                top.hoisted.addAll(events.subList(i, end + 1));
                int nRest = top.rest.size();
                if (nRest != 0 && end + 1 < n
                    && events.get(end + 1).getKind() == Event.Kind.POP) {
                  Event lastRest = top.rest.get(nRest - 1);
                  if (lastRest.getKind() == Event.Kind.PUSH
                      && lastRest.getNodeType()
                         == NodeType.TemplateDirectives) {
                    // Get rid of newly empty template directives node.
                    top.rest.remove(nRest - 1);
                    ++end;
                  }
                }
                i = end;
                continue hoist_loop;
              }
            } else if (k == Event.Kind.PUSH) {
              ++depth;
            }
          }
        }
      }
      top.rest.add(e);
    }
    return top.getAllEvents();
  }

  static final class HoistState {
    final Event start;
    final List<Event> hoisted = Lists.newArrayList();
    final List<Event> rest = Lists.newArrayList();

    HoistState(@Nullable Event start) {
      this.start = start;
    }

    ImmutableList<Event> getAllEvents() {
      ImmutableList.Builder<Event> b = ImmutableList.builder();
      if (start != null) {
        b.add(start);
      }
      if (!hoisted.isEmpty()) {
        b.add(Event.push(
            TemplateDirectivesNode.Variant.TemplateDirectiveTemplateDirective));
        b.addAll(hoisted);
        b.add(Event.pop());
      }
      return b.addAll(rest).build();
    }
  }
}
