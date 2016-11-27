package com.mikesamuel.cil.template;

import java.util.Arrays;
import java.util.BitSet;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.mikesamuel.cil.ast.NodeType;
import com.mikesamuel.cil.ast.NodeTypeTables;
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
  public static ImmutableList<Event> generalize(
      Input input, Iterable<? extends Event> events)
  throws IllegalArgumentException {

    // We have three goals here.
    // 1. Make sure that interpolations are in as general a position as
    //    possible.
    // 2. Make sure that start directives like <%template...{%> are at the same
    //    depth as end directives <%}%> and that in-between is a forest
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

    Events head = null;
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

      Events tail = null;
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
                && NodeTypeTables.NONSTANDARD.contains(nt)) {
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
        if (tail != null) {
          tail.next = toAdd;
          toAdd.prev = tail;
          tail = toAdd;
        } else {
          Preconditions.checkState(head == null);
          head = tail = toAdd;
        }
      }
      Preconditions.checkState(
          nonStandardAncestorDepth < 0 && currentNodeIndex == 0);
    }

    if (DEBUG) {
      System.err.println("Events");
      for (Events es = head; es != null; es = es.next) {
        System.err.println("\t" + es);
      }
    }
    // If the event list were empty we should have early outed above.
    Preconditions.checkNotNull(head);

    {
      List<NonstandardEvents> starts = Lists.newArrayList();
      for (Events es = head; es != null; es = es.next) {
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
    head = head.rebalance(DirectiveKind.START, input);
    // Next, we rebalance infix operators.  This generalizes
    head = head.rebalance(DirectiveKind.INFIX, input);
    // Rebalancing the start takes care of rebalancing the corresponding ends.

    boolean inDirectives = false;
    ImmutableList.Builder<Event> rebalanced = ImmutableList.builder();
    for (Events es = head; es != null; es = es.next) {
      ImmutableList<Event> esEvents = es.events();
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
      TemplateDirectivesNode.Variant.TemplateDirectives);

  static final Event POP = Event.pop();


  private static class TemplateDirectiveEnvelopeFilter {
    boolean hasNonStandard;

    ImmutableList<Event> filter(Iterable<? extends Event> events) {
      // Our TemplateDirectives look like
      //     TemplateDirectives
      //       "<%"
      //       TemplateDirective
      //       "%>"
      // Later in the generalize process, we move the directives around, and we
      // avoid moving past a token as this respects the user's intent as to where
      // they place the directive.
      //
      // The tokens "<%" and "%>" get in the way of this, and having the
      // TemplateDirectives push/pop in the stream makes gains us nothing.
      //
      // Here we remove the token pairs
      //    (push TemplateDirectives),`<%`
      // and
      //    `%>`, (pop)
      // when not paired with TemplateInterpolation.
      // Later in the process we rewrap TemplateDirective nodes in
      // TemplateDirectives.
      ImmutableList.Builder<Event> b = ImmutableList.builder();
      Event last = null;
      int depth = 0;
      BitSet pushRemoved = new BitSet();
      for (Event e : events) {
        switch (e.getKind()) {
          case TOKEN:
            if ("<%".equals(e.getContent()) && last != null) {
                if (last.getKind() == Event.Kind.PUSH
                    && last.getNodeType() == NodeType.TemplateDirectives) {
                  last = null;
                  Preconditions.checkState(depth != 0);
                  pushRemoved.set(depth - 1);
                  continue;
                } else if (last.getKind() == Event.Kind.TOKEN
                           && "%>".equals(last.getContent())
                           && depth != 0 && pushRemoved.get(depth - 1)) {
                  last = null;
                  continue;
                }
            }
            break;
          case POP:
            Preconditions.checkState(depth != 0);
            --depth;
            if (last != null && last.getKind() == Event.Kind.TOKEN
                && "%>".equals(last.getContent())
                && pushRemoved.get(depth)) {
              last = null;
              continue;
            }
            break;
          case PUSH:
            if (NodeTypeTables.NONSTANDARD.contains(e.getNodeType())) {
              this.hasNonStandard = true;
            }
            pushRemoved.clear(depth);
            ++depth;
            break;
          case CONTENT:
          case IGNORABLE:
            break;
          case DELAYED_CHECK:
          case LR_END:
          case LR_START:
          case POSITION_MARK:
            throw new IllegalArgumentException(e.toString());
        }
        if (last != null) { b.add(last); }
        last = e;
      }
      if (last != null) { b.add(last); }
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
    Events prev;
    Events next;

    Events() {}

    abstract ImmutableList<Event> events();

    Events rebalance(DirectiveKind k, Input input) {
      if (next != null) {
        return next.rebalance(k, input);
      } else {
        // Now that we've rebalanced everything left, make sure the original
        // caller has the right head.
        Events head;
        for (head = this; head.prev != null;) {
          head = head.prev;
        }
        return head;
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
          switch ((TemplateDirectiveNode.Variant) event0.getNodeVariant()) {
            case BlockStart:
            case IfStart:
            case LoopStart:
            case TemplateStart:
              dk = DirectiveKind.START;
              break;
            case Else:
            case Vars:
              dk = DirectiveKind.INFIX;
              break;
            case End:
              dk = DirectiveKind.END;
              break;
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
    Events rebalance(DirectiveKind kindToRebalance, Input input) {
      if (this.k == kindToRebalance) {
        switch (this.k) {
          case INFIX: {
            int nPushes = nLeft(this.prev, EnumSet.of(Event.Kind.PUSH));
            int nPops = nRight(this.next, EnumSet.of(Event.Kind.POP));
            int nToRemove = Math.min(nPushes, nPops);
            for (int i = 0; i < nToRemove; ++i) {
              Events newNext = this.next.next;
              this.next = newNext;

              Events newPrev = this.prev.prev;
              this.prev = newPrev;
            }
            this.next.prev = this.prev.next = this;
            break;
          }
          case START: {
            NonstandardEvents start = this;
            NonstandardEvents end = dual;
            Preconditions.checkState(
                end.k == DirectiveKind.END && end.dual == start);
            Deltas startDeltas = new Deltas(start);
            Deltas endDeltas = new Deltas(end);

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
            startDeltas.pushAround(start, bestStartDelta);
            endDeltas.pushAround(end, bestEndDelta);
            break;
          }
          case END:
            // Handled by START
            break;
        }
      }
      return super.rebalance(kindToRebalance, input);
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

  static int nLeft(Events start, EnumSet<Event.Kind> kinds) {
    int n = 0;
    for (Events es = start; es instanceof StandardEvents; es = es.prev) {
      Event.Kind ek = ((StandardEvents) es).e.getKind();
      if (kinds.contains(ek)) {
        n += 1;
      } else {
        break;
      }
    }
    return n;
  }

  static int nRight(Events start, EnumSet<Event.Kind> kinds) {
    int n = 0;
    for (Events es = start; es instanceof StandardEvents; es = es.next) {
      Event.Kind ek = ((StandardEvents) es).e.getKind();
      if (kinds.contains(ek)) {
        n += 1;
      } else {
        break;
      }
    }
    return n;
  }

  static final class Deltas {
    /** node index by deltas packed thus [0, -1, 1, -2, 2, -3, 4, ...] */
    private final int[] deltaToNodeIndex;
    private final int countToPushLeft;
    private final int countToPushRight;

    Deltas(NonstandardEvents nes) {
      if (DEBUG) {
        System.err.println("Computing deltas for " + nes);
      }

      // Assume we can push infix elements around.
      int nToPushLeft = 0;
      Events left = nes.prev;
      while (left instanceof NonstandardEvents
          && ((NonstandardEvents) left).k == DirectiveKind.INFIX) {
        left = left.prev;
        ++nToPushLeft;
      }
      if (DEBUG) {
        System.err.println("\tleft=" + left + ", nToPushLeft=" + nToPushLeft);
      }

      Events right = nes.next;
      // Assume we can push infix and start elements right.
      int nToPushRight = 0;
      while (right instanceof NonstandardEvents) {
        DirectiveKind rightKind = ((NonstandardEvents) right).k;
        if (rightKind == DirectiveKind.INFIX
            || rightKind == DirectiveKind.START
            // if rightKind is END and nes is a START, then it's nes's end so
            // we will conclude that delta=0 is fine above.
            // An end cannot push an another end right because that end
            // corresponds to an earlier end so doing so would invalidate a
            // decision committed to earlier.
            ) {
          right = right.next;
          ++nToPushRight;
        }
      }
      if (DEBUG) {
        System.err.println(
            "\tright=" + right + ", nToPushRight=" + nToPushRight);
      }

      EnumSet<Event.Kind> popsAndPushes = EnumSet.of(
          Event.Kind.POP, Event.Kind.PUSH);
      int nPopsAndPushesLeft = nLeft(left, popsAndPushes);
      int nPopsAndPushesRight = nRight(right, popsAndPushes);
      if (DEBUG) {
        System.err.println(
            "\tnPopsAndPushesLeft =" + nPopsAndPushesLeft);
        System.err.println(
            "\tnPopsAndPushesRight=" + nPopsAndPushesRight);
      }

      int[] deltaToNodeIndexArray = new int[
        1 + Math.max(nPopsAndPushesLeft, nPopsAndPushesRight) * 2];
      Arrays.fill(deltaToNodeIndexArray, -1);
      deltaToNodeIndexArray[0] =
          nPopsAndPushesLeft != 0
          ? ((StandardEvents) left).nodeIndexRight
          : nPopsAndPushesRight != 0
          ? ((StandardEvents) right).nodeIndexLeft
          : -1;
      {
        int k = 1;  // Odds are negative (left) deltas
        for (Events es = left;
             k / 2 < nPopsAndPushesLeft; es = es.prev, k += 2) {
          deltaToNodeIndexArray[k] = ((StandardEvents) es).nodeIndexLeft;
        }
      }
      {
        int k = 2;  // Non-zero evens are (right) deltas
        for (Events es = right;
             k / 2 <= nPopsAndPushesRight; es = es.next, k += 2) {
          deltaToNodeIndexArray[k] = ((StandardEvents) es).nodeIndexRight;
        }
      }

      this.deltaToNodeIndex = deltaToNodeIndexArray;
      this.countToPushLeft = nToPushLeft;
      this.countToPushRight = nToPushRight;
    }

    public void pushAround(NonstandardEvents es, int delta) {
      Events before = es.prev;
      for (int i = 0; i < countToPushLeft; ++i) {
        before = before.prev;
      }

      Events after = es.next;
      for (int i = 0; i < countToPushRight; ++i) {
        after = after.next;
      }

      if (delta < 0) {
        for (int i = 0; i < -delta; ++i) {
          Events toMove = before;
          Events toMovePrev = toMove.prev;
          Events toMoveNext = toMove.next;

          toMove.next = after;
          after.prev.next = toMove;
          toMove.prev = after.prev;
          after.prev = toMove;
          after = toMove;

          toMovePrev.next = toMoveNext;
          toMoveNext.prev = toMovePrev;
          before = toMovePrev;
        }
      } else {
        for (int i = 0; i < delta; ++i) {
          Events toMove = after;
          Events toMovePrev = toMove.prev;
          Events toMoveNext = toMove.next;

          toMove.prev = before;
          before.next.prev = toMove;
          toMove.next = before.next;
          before.next = toMove;
          before = toMove;

          toMoveNext.prev = toMovePrev;
          toMovePrev.next = toMoveNext;
          after = toMoveNext;
        }
      }
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
}