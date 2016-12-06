package com.mikesamuel.cil.ptree;

import java.util.EnumSet;
import java.util.List;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.mikesamuel.cil.ast.NodeType;
import com.mikesamuel.cil.ast.NodeTypeTables;
import com.mikesamuel.cil.ast.NodeVariant;
import com.mikesamuel.cil.ast.TemplateInterpolationNode;
import com.mikesamuel.cil.event.Debug;
import com.mikesamuel.cil.event.Event;
import com.mikesamuel.cil.parser.SList;
import com.mikesamuel.cil.parser.LeftRecursion;
import com.mikesamuel.cil.parser.LeftRecursion.Stage;
import com.mikesamuel.cil.parser.MatchErrorReceiver;
import com.mikesamuel.cil.parser.MatchState;
import com.mikesamuel.cil.parser.ParseErrorReceiver;
import com.mikesamuel.cil.parser.ParseResult;
import com.mikesamuel.cil.parser.ParseState;
import com.mikesamuel.cil.parser.RatPack;
import com.mikesamuel.cil.parser.RatPack.ParseCacheEntry;
import com.mikesamuel.cil.parser.SerialErrorReceiver;
import com.mikesamuel.cil.parser.SerialState;

final class Reference extends PTParSer {
  final NodeType nodeType;
  private ImmutableList<NodeVariant> variants;

  Reference(NodeType nodeType) {
    this.nodeType = nodeType;
  }


  private void initLazy() {
    if (variants == null) {
      ImmutableList.Builder<NodeVariant> variantsBuilder =
          ImmutableList.builder();
      NodeType nt = null;
      for (Enum<?> e : nodeType.getVariantType().getEnumConstants()) {
        NodeVariant nv = (NodeVariant) e;
        variantsBuilder.add(nv);
        if (nt == null) {
          nt = nv.getNodeType();
        } else {
          Preconditions.checkState(nt == nv.getNodeType());
        }
      }
      this.variants = variantsBuilder.build();
    }
  }

  /** The production referred to. */
  public NodeType getNodeType() {
    return nodeType;
  }

  ImmutableList<NodeVariant> getVariants() {
    initLazy();
    return this.variants;
  }

  @Override
  public String toString() {
    return nodeType.name();
  }

  @Override
  Kind getKind() {
    return Kind.REF;
  }

  // HACK DEBUG: Not thread safe
  private static final boolean DEBUG = false;
  private static final boolean DEBUG_LR = false;
  private static final boolean DEBUG_UP = false;
  private static int depth = 0;
  private static String indent() {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < depth; ++i) {
      sb.append("  ");
    }
    return sb.toString();
  }
  private static void indent(int delta) {
    Preconditions.checkState(depth + delta >= 0);
    depth += delta;
  }
  private static String lastInputSeen = "";
  private static String dumpInput(String inp) {
    if (inp.equals(lastInputSeen)) {
      return null;
    }
    lastInputSeen = inp;
    return inp;
  }
  // END HACK

  private static ImmutableList<Event> lastOutputSeen = ImmutableList.of();
  private static String dumpOutput(SList<Event> out) {
    ImmutableList<Event> lastList = lastOutputSeen;
    ImmutableList<Event> outList = ImmutableList.copyOf(
        SList.forwardIterable(out));
    lastOutputSeen = outList;
    if (!lastList.isEmpty() && outList.size() >= lastList.size()
        && outList.subList(0, lastList.size()).equals(lastList)) {
      if (lastList.size() == outList.size()) {
        return null;
      }
      StringBuilder sb = new StringBuilder("[...");
      for (Event e
           : outList.subList(lastList.size(), outList.size())) {
        sb.append(", ").append(e);
      }
      return sb.append(']').toString();
    }
    return outList.toString();
  }

  @SuppressWarnings("unused")
  @Override
  public ParseResult parse(
      ParseState start, LeftRecursion lr, ParseErrorReceiver err) {
    LeftRecursion.Stage stage = lr.stageForProductionAt(nodeType, start.index);
    switch (stage) {
      case GROWING:
        if (DEBUG) {
          System.err.println(
              indent() + "Found LR Growing " + nodeType + " @ " + start.index);
        }
        return ParseResult.success(
            start.appendOutput(Event.leftRecursionSuffixEnd(nodeType)),
            ParseResult.NO_WRITE_BACK_RESTRICTION,
            // Checked to make sure that the growing does not accidentally take
            // a non-left recursing path.
            Sets.immutableEnumSet(nodeType)
            );
      case NOT_ON_STACK:
        break;
      case SEEDING:
        // Do not cache this failure since it is not a foregone conclusion.
        if (DEBUG) {
          System.err.println(
              indent() + "Found LR seeding " + nodeType + " @ " + start.index);
        }
        return ParseResult.failure(Sets.immutableEnumSet(nodeType));
    }

    ParseCacheEntry cachedParse = start.input.ratPack.getCachedParse(
        nodeType, start.index);
    if (cachedParse.wasTried()) {
      if (cachedParse.passed()) {
        if (DEBUG) {
          System.err.println(
              indent() + "Using cached success for " + nodeType
              + " @ " + start.index);
        }
        return ParseResult.success(
            cachedParse.apply(start),
            ParseResult.NO_WRITE_BACK_RESTRICTION, ImmutableSet.of());
      } else {
        if (DEBUG) {
          System.err.println(
              indent() + "Using cached failure for " + nodeType
              + " @ " + start.index);
        }
        return ParseResult.failure();
      }
    }

    Profile.count(nodeType);

    ParseState state = start;
    state = maybeParseTemplateDirectives(stage, state, err);

    if (DEBUG) {
      System.err.println(
          indent() + "Entered " + nodeType + " @ " + state.index);
      String din = dumpInput(
          state.input.content.subSequence(state.index).toString());
      if (din != null) {
        System.err.println(indent() + ". . input=`" + din + "`");
      }
      String dout = dumpOutput(state.output);
      if (dout != null && false) {
        System.err.println(indent() + ". . output=" + dout);
      }
    }

    EnumSet<NodeType> allExclusionsTriggered =
        EnumSet.noneOf(NodeType.class);
    ParseResult result = parseVariants(
        state, lr, err, LeftRecursion.Stage.SEEDING,
        allExclusionsTriggered);
    allExclusionsTriggered.addAll(result.lrExclusionsTriggered);
    int writeBack = result.writeBack;

    boolean wasLrTriggered = allExclusionsTriggered.remove(nodeType);

    if (wasLrTriggered && result.synopsis == ParseResult.Synopsis.SUCCESS) {
      ParseState afterSeed = result.next();
      if (DEBUG) {
        System.err.println(
            indent() + "AfterSeed " + nodeType
            + "\n" + indent() + ". . input=`"
            + afterSeed.input.content.subSequence(afterSeed.index) + "`");
        if (false) {
          System.err.println(
              indent() + ". . output="
              + ImmutableList.copyOf(SList.forwardIterable(afterSeed.output)));
        }
      }

      ParseState grown = afterSeed;

      grow_the_seed:
      while (true) {
        ParseResult growResult = parseVariants(
            grown.appendOutput(Event.leftRecursionSuffixStart()),
            lr, err, LeftRecursion.Stage.GROWING,
            allExclusionsTriggered);
        allExclusionsTriggered.addAll(growResult.lrExclusionsTriggered);
        switch (growResult.synopsis) {
          case FAILURE:
            // Use the last successful growing.
            break grow_the_seed;
          case SUCCESS:
            if (!growResult.lrExclusionsTriggered.contains(nodeType)) {
              // TODO: check that left-recursion occurred at grown.index.
              // We could walk and check that there is in fact an LREnd on the
              // output, but it would be more efficient for lr to keep track of
              // this.
              break grow_the_seed;
            }
            ParseState next = growResult.next();
            writeBack = Math.min(writeBack, growResult.writeBack);
            if (next.index == grown.index) {
              // no progress made.
              break grow_the_seed;
            }
            if (next.index <= grown.index) {
              if (true) {
                break grow_the_seed;
              } else {
                throw new IllegalStateException(
                    "Grew backwards " + nodeType + " from "
                        + grown.input.getSourcePosition(grown.index)
                        + " to " + next.input.getSourcePosition(next.index));
              }
            }
            grown = next;
            continue;
        }
      }

      allExclusionsTriggered.remove(nodeType);
      LRRewriter rewriter = new LRRewriter(nodeType);
      // TODO: do we need to reapply the postcondition here?
      result = ParseResult.success(
          grown.withOutput(rewriter.rewrite(grown.output)),
          writeBack,
          allExclusionsTriggered);
    }

    boolean canCache;
    if (writeBack <= state.index) {
      canCache = false;
    } else {
      canCache = true;
      for (NodeType nt : allExclusionsTriggered) {
        if (nt != nodeType &&
            lr.stageForProductionAt(nt, state.index)
            != LeftRecursion.Stage.NOT_ON_STACK) {
          canCache = false;
          break;
        }
      }
    }

    switch (result.synopsis) {
      case FAILURE:
        RatPack.ParseCacheEntry e = state.input.ratPack.getCachedParse(
            nodeType, state.index);
        if (canCache) {
          state.input.ratPack.cacheFailure(state.index, nodeType);
        }
        if (DEBUG) {
          System.err.println(
              indent() + "Fail " + nodeType + " @ " + state.index);
        }
        return ParseResult.failure(allExclusionsTriggered);
      case SUCCESS:
        ParseState next = result.next();
        if (canCache) {
          state.input.ratPack.cacheSuccess(
              state.index, next.index, nodeType, next.output);
        }
        if (DEBUG) {
          System.err.println(
              indent() + "Pass " + nodeType + " @ " + state.index
              + " -> " + next.index);
        }
        next = maybeParseTemplateDirectives(stage, next, err);
        return ParseResult.success(next, writeBack, allExclusionsTriggered);
    }
    throw new AssertionError(result.synopsis);
  }

  private ParseResult parseVariants(
      ParseState state, LeftRecursion lr, ParseErrorReceiver err, Stage stage,
      EnumSet<NodeType> failureExclusionsTriggered) {
    if (DEBUG) { indent(1); }

    try {
      for (NodeVariant variant : getVariants()) {
        if (stage == Stage.SEEDING) {
          //Lookahead1 la1 = variant.getLookahead1();
          //if (!(la1 == null || la1.canFollow(state))) {
          //  continue;
          //}
        }

        try (LeftRecursion.VariantScope scope = lr.enter(
                 variant, state.index, stage)) {
          ParseState beforeBody = state.appendOutput(Event.push(variant));
          ParseResult result = variant.getParSer().parse(beforeBody, lr, err);
          switch (result.synopsis) {
            case FAILURE:
              failureExclusionsTriggered.addAll(result.lrExclusionsTriggered);
              continue;
            case SUCCESS:
              ParseState afterBody = result.next();
              Event pop = DEBUG ? Event.pop(variant) : Event.pop();
              ParseState afterVariant = afterBody.appendOutput(pop);
              Predicate<SList<Event>> postcond = variant.getPostcond();
              if (postcond.apply(afterVariant.output)) {
                return ParseResult.success(
                    afterVariant,
                    result.writeBack, result.lrExclusionsTriggered);
              } else {
                failureExclusionsTriggered.addAll(result.lrExclusionsTriggered);
                continue;
              }
          }
          throw new AssertionError(result.synopsis);
        }
      }

      if (stage != Stage.GROWING && shouldHandleTemplatePart(state)
          && !NodeTypeTables.NOINTERP.contains(nodeType)) {
        // Try parsing a template element.
        ParseResult nonStandardResult =
            NodeType.TemplateInterpolation.getParSer()
            .parse(state, new LeftRecursion(), err);
        switch (nonStandardResult.synopsis) {
          case FAILURE: break;
          case SUCCESS:
            failureExclusionsTriggered.addAll(
                nonStandardResult.lrExclusionsTriggered);
            return ParseResult.success(
                nonStandardResult.next(),
                nonStandardResult.writeBack,
                failureExclusionsTriggered);
        }
      }
    } finally {
      if (DEBUG) { indent(-1); }
    }

    return ParseResult.failure();
  }

  private boolean shouldHandleTemplatePart(ParseState state) {
    return state.input.allowNonStandardProductions
        && state.startsWith("<%", Optional.absent())
        && !NodeTypeTables.NONSTANDARD.contains(nodeType);
  }

  private ParseState maybeParseTemplateDirectives(
      Stage stage, ParseState state, ParseErrorReceiver err) {
    if (stage == Stage.NOT_ON_STACK && shouldHandleTemplatePart(state)) {
      // Handle directives at the front.
      ParseResult result = NodeType.TemplateDirectives.getParSer()
          .parse(state, new LeftRecursion(), err);
      switch (result.synopsis) {
        case FAILURE:
          return state;
        case SUCCESS:
          Preconditions.checkState(
              result.writeBack == ParseResult.NO_WRITE_BACK_RESTRICTION);
          Preconditions.checkState(
              result.lrExclusionsTriggered.isEmpty());
          return result.next();
      }
      throw new AssertionError(result.synopsis);
    }
    return state;
  }

  @Override
  public Optional<SerialState> unparse(
      SerialState start, SerialErrorReceiver err) {
    initLazy();
    if (DEBUG_UP) {
      System.err.println(indent() + "Unparse " + nodeType);
    }

    // Trees.startUnparse with a decorator might have inserted a comment node.
    SerialState state = start.shiftIgnorablesToOutput();
    // There might be template directives at the start.
    state = unparseTemplateDirectives(state, err);
    // There might be decorations after the template directive.
    // TODO: maybe hide the handling of ignorable tokens from this class.
    state = state.shiftIgnorablesToOutput();

    // Try handling non-anon variants first.
    if (!state.isEmpty()) {
      Event e = state.structure.get(state.index);
      switch (e.getKind()) {
        case PUSH:
          NodeVariant variant = e.getNodeVariant();
          if (variant.getNodeType() == nodeType
              || variant == TemplateInterpolationNode.Variant.Interpolation) {
            if (DEBUG_UP) {
              System.err.println(indent() + ". Found same variant " + variant);
            }
            // Commit to handling non-anon variant.
            Optional<SerialState> afterContentOpt;
            if (DEBUG_UP) { indent(1); }
            try {
              afterContentOpt = variant.getParSer().unparse(
                  state.advanceWithCopy(), err);
            } finally {
              if (DEBUG_UP) { indent(-1); }
            }
            if (afterContentOpt.isPresent()) {
              SerialState afterContent = afterContentOpt.get();
              if (!afterContent.isEmpty() &&
                  afterContent.structure.get(afterContent.index).getKind()
                  == Event.Kind.POP) {
                if (DEBUG_UP) {
                  System.err.println(
                      indent() + "Same variant " + variant + " passed");
                }
                return Optional.of(
                    unparseTemplateDirectives(
                        afterContent.advanceWithCopy(), err));
              }
            }
            if (DEBUG_UP) {
              System.err.println(
                  indent() + "Same variant " + variant + " failed");
            }
            return Optional.absent();
          }
          break;
        case CONTENT:
        case IGNORABLE:
        case POP:
        case POSITION_MARK:
        case TOKEN:
          return Optional.absent();
        case DELAYED_CHECK:
        case LR_END:
        case LR_START:
          throw new AssertionError(e.getKind());
      }
    }

    // Recurse to anon-variants to see if we can insert the missing pushes/pops.
    for (NodeVariant v : this.variants) {
      if (!v.isAnon()) {
        continue;
      }

      SerialState beforeContent = state.append(Event.push(v));
      if (DEBUG_UP) {
        System.err.println(indent() + ". Trying anon variant " + v);
      }
      Optional<SerialState> afterContentOpt;
      if (DEBUG_UP) { indent(1); }
      try {
        // This assumes there are no @anon cycles in the parse graph.
        afterContentOpt = v.getParSer().unparse(beforeContent, err);
      } finally {
        if (DEBUG_UP) { indent(-1); }
      }
      if (afterContentOpt.isPresent()) {
        if (DEBUG_UP) {
          System.err.println(indent() + "Anon variant " + v + " passed");
        }
        return Optional.of(
            unparseTemplateDirectives(
                afterContentOpt.get().append(Event.pop()), err));
      }
      if (DEBUG_UP) {
        System.err.println(indent() + "Anon variant " + v + " failed");
      }
    }
    if (DEBUG_UP) {
      System.err.println(indent() + "Reference " + nodeType + " failed");
    }
    return Optional.absent();
  }

  private SerialState unparseTemplateDirectives(
      SerialState start, SerialErrorReceiver err) {
    if (NodeTypeTables.NONSTANDARD.contains(nodeType)) {
      return start;
    }
    if (!start.isEmpty()) {
      Event e = start.structure.get(start.index);
      if (e.getKind() == Event.Kind.PUSH
          && e.getNodeType() == NodeType.TemplateDirectives) {
        Optional<SerialState> result = e.getNodeVariant().getParSer().unparse(
            start.advanceWithCopy(), err);
        if (result.isPresent()) {
          SerialState resultState = result.get();
          if (!resultState.isEmpty()
              && resultState.structure.get(resultState.index).getKind()
                 == Event.Kind.POP) {
            return resultState.advanceWithCopy();
          }
        }
      }
    }
    return start;
  }

  @Override
  public Optional<MatchState> match(
      MatchState state, MatchErrorReceiver err) {
    initLazy();
    return Alternation.of(variants).getParSer().match(state, err);
  }


  private static final class LRRewriter {
    private final Event toPushback;
    private final List<List<Event>> pushback = Lists.newArrayList();
    private int popDepth;

    LRRewriter(NodeType nodeType) {
      this.toPushback = Event.leftRecursionSuffixEnd(nodeType);
    }

    SList<Event> rewrite(SList<Event> out) {
      if (DEBUG_LR) {
        String indent = indent();
        System.err.println(indent + "before rewriteLR " + toPushback);
        Debug.dumpEvents(indent, SList.forwardIterable(out), System.err);
      }
      SList<Event> withPushbackAndPops = pushback(out);
      Preconditions.checkState(pushback.isEmpty());
      if (DEBUG_LR) {
        String indent = indent();
        System.err.println(indent + "after rewriteLR " + toPushback);
        Debug.dumpEvents(
            indent, SList.forwardIterable(withPushbackAndPops), System.err);
      }
      return withPushbackAndPops;
    }

    /**
     * Scan until we find the push of the seed and distribute pushes and pops
     * from LRSuffix events.
     */
    private SList<Event> pushback(SList<Event> out) {
      if (DEBUG_LR) {
        String indent = indent();
        System.err.println(
            indent + "pushback(" + (out != null ? "...," + out.x : "<null>")
            + ", popDepth=" + popDepth + ", pushback=" + pushback + ")");
      }
      Preconditions.checkNotNull(out);

      Event e = out.x;

      SList<Event> pushedBack = null;
      switch (e.getKind()) {
        case POP:
          ++popDepth;
          pushedBack = SList.append(pushback(out.prev), e);
          break;
        case PUSH:
          Preconditions.checkState(popDepth != 0);  // pop required above.
          --popDepth;
          if (popDepth == 0) {
            Preconditions.checkState(
                toPushback.getNodeType() == e.getNodeType());
            pushedBack = out.prev;
            if (DEBUG_LR) {
              System.err.println(indent() + "Pushback = " + pushback);
            }
            for (List<Event> onePb : pushback) {
              for (Event pb : Lists.reverse(onePb)) {
                pushedBack = SList.append(pushedBack, pb);
              }
            }
            pushback.clear();
            pushedBack = SList.append(pushedBack, e);
          } else {
            pushedBack = SList.append(pushback(out.prev), e);
          }
          break;
        case LR_END:
          if (e.equals(toPushback)) {
            int pushCount = 0;
            int popCount = 0;

            List<Event> onePb = Lists.newArrayList();
            pushback.add(onePb);
            boolean foundStart = false;
            pb_loop:
            for (SList<Event> c = out.prev; c != null; c = c.prev) {
              Event ce = c.x;
              switch (ce.getKind()) {
                case LR_START:
                  Preconditions.checkState(pushCount >= popCount);
                  popDepth += popCount - pushCount;
                  Preconditions.checkState(popDepth >= 0);
                  pushedBack = pushback(c.prev);
                  foundStart = true;
                  break pb_loop;
                case POP:
                  onePb.add(ce);
                  ++popCount;
                  continue;
                case PUSH:
                  onePb.add(ce);
                  ++pushCount;
                  continue;
                case CONTENT:
                case DELAYED_CHECK:
                case IGNORABLE:
                case LR_END:
                case POSITION_MARK:
                case TOKEN:
                  break;
              }
              throw new AssertionError(
                  "Non push/pop on path to LR invocation " + ce);
            }
            Preconditions.checkState(foundStart);
            break;
          }
          //$FALL-THROUGH$
        case CONTENT:
        case DELAYED_CHECK:
        case IGNORABLE:
        case LR_START:
        case POSITION_MARK:
        case TOKEN:
          pushedBack = SList.append(pushback(out.prev), e);
          break;
      }
      return pushedBack;
    }
  }
}
