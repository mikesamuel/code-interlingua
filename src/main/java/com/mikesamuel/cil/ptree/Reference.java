package com.mikesamuel.cil.ptree;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.mikesamuel.cil.ast.BaseNode;
import com.mikesamuel.cil.ast.Grammar;
import com.mikesamuel.cil.ast.LeafNode;
import com.mikesamuel.cil.ast.NodeType;
import com.mikesamuel.cil.ast.NodeVariant;
import com.mikesamuel.cil.ast.j8.J8NodeType;
import com.mikesamuel.cil.event.Debug;
import com.mikesamuel.cil.event.Event;
import com.mikesamuel.cil.parser.ForceFitState;
import com.mikesamuel.cil.parser.ForceFitState.FixedNode;
import com.mikesamuel.cil.parser.ForceFitState.InterpolatedValue;
import com.mikesamuel.cil.parser.LeftRecursion;
import com.mikesamuel.cil.parser.LeftRecursion.Stage;
import com.mikesamuel.cil.parser.MatchErrorReceiver;
import com.mikesamuel.cil.parser.MatchState;
import com.mikesamuel.cil.parser.ParSer;
import com.mikesamuel.cil.parser.ParSerable;
import com.mikesamuel.cil.parser.ParseErrorReceiver;
import com.mikesamuel.cil.parser.ParseResult;
import com.mikesamuel.cil.parser.ParseState;
import com.mikesamuel.cil.parser.RatPack;
import com.mikesamuel.cil.parser.RatPack.ParseCacheEntry;
import com.mikesamuel.cil.parser.SList;
import com.mikesamuel.cil.parser.SerialErrorReceiver;
import com.mikesamuel.cil.parser.SerialState;

final class Reference extends PTParSer {
  final NodeType<?, ?> nodeType;
  private ImmutableList<NodeVariant<?, ?>> variants;

  Reference(NodeType<?, ?> nodeType) {
    this.nodeType = nodeType;
  }


  private void initLazy() {
    if (variants == null) {
      ImmutableList.Builder<NodeVariant<?, ?>> variantsBuilder =
          ImmutableList.builder();
      for (Enum<?> e : nodeType.getVariantType().getEnumConstants()) {
        NodeVariant<?, ?> nv = (NodeVariant<?, ?>) e;
        variantsBuilder.add(nv);
        Preconditions.checkState(nodeType == nv.getNodeType());
      }
      this.variants = variantsBuilder.build();
    }
  }

  /** The production referred to. */
  public NodeType<?, ?> getNodeType() {
    return nodeType;
  }

  ImmutableList<NodeVariant<?, ?>> getVariants() {
    initLazy();
    return this.variants;
  }

  @Override
  public void appendShallowStructure(StringBuilder sb) {
    sb.append(nodeType.name());
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
            start.appendOutput(
                Event.leftRecursionSuffixEnd(nodeType, start.index)),
            ParseResult.NO_WRITE_BACK_RESTRICTION,
            // Checked to make sure that the growing does not accidentally take
            // a non-left recursing path.
            ImmutableSet.of(nodeType)
            );
      case NOT_ON_STACK:
        break;
      case SEEDING:
        // Do not cache this failure since it is not a foregone conclusion.
        if (DEBUG) {
          System.err.println(
              indent() + "Found LR seeding " + nodeType + " @ " + start.index);
        }
        return ParseResult.failure(ImmutableSet.of(nodeType));
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
    state = maybeParseInterstitialNonstandard(stage, state, err);

    if (DEBUG) {
      System.err.println(
          indent() + "Entered " + nodeType + " @ " + state.index);
      CharSequence content = state.input.content();
      String din = dumpInput(
          content.subSequence(state.index, content.length()).toString());
      if (din != null) {
        System.err.println(indent() + ". . input=`" + din + "`");
      }
      String dout = dumpOutput(state.output);
      if (dout != null && false) {
        System.err.println(indent() + ". . output=" + dout);
      }
    }

    Set<NodeType<?, ?>> allExclusionsTriggered = Sets.newLinkedHashSet();
    ParseResult result = parseVariants(
        state, lr, err, LeftRecursion.Stage.SEEDING,
        allExclusionsTriggered);
    allExclusionsTriggered.addAll(result.lrExclusionsTriggered);
    int writeBack = result.writeBack;

    boolean wasLrTriggered = allExclusionsTriggered.remove(nodeType);

    if (wasLrTriggered && result.synopsis == ParseResult.Synopsis.SUCCESS) {
      ParseState afterSeed = result.next();
      if (DEBUG) {
        CharSequence content = afterSeed.input.content();
        System.err.println(
            indent() + "AfterSeed " + nodeType
            + "\n" + indent() + ". . input=`"
            + content.subSequence(afterSeed.index, content.length()) + "`");
        if (false) {
          System.err.println(
              indent() + ". . output="
              + ImmutableList.copyOf(SList.forwardIterable(afterSeed.output)));
        }
      }

      ParseState grown = afterSeed;

      grow_the_seed:
      while (true) {
        ParseState beforeGrow = grown.appendOutput(
            Event.leftRecursionSuffixStart());
        ParseResult growResult = parseVariants(
            beforeGrow,
            lr, err, LeftRecursion.Stage.GROWING,
            allExclusionsTriggered);
        allExclusionsTriggered.addAll(growResult.lrExclusionsTriggered);
        switch (growResult.synopsis) {
          case FAILURE:
            // Use the last successful growing.
            break grow_the_seed;
          case SUCCESS:
            ParseState next = growResult.next();
            if (!growReachedLR(beforeGrow.index, next)) {
              break grow_the_seed;
            }
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
      for (NodeType<?, ?> nt : allExclusionsTriggered) {
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
        next = maybeParseInterstitialNonstandard(stage, next, err);
        return ParseResult.success(next, writeBack, allExclusionsTriggered);
    }
    throw new AssertionError(result.synopsis);
  }

  private ParseResult parseVariants(
      ParseState state, LeftRecursion lr, ParseErrorReceiver err, Stage stage,
      Set<NodeType<?, ?>> failureExclusionsTriggered) {
    if (DEBUG) { indent(1); }

    try {
      for (NodeVariant<?, ?> variant : getVariants()) {
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

      if (stage != Stage.GROWING) {
        Grammar<?, ?> g = nodeType.getGrammar();
        Optional<ParSer> replacementParser =
            g.parserForNonStandardReplacement(nodeType);
        if (replacementParser.isPresent()) {
          ParseResult nonStandardResult = replacementParser.get()
              .parse(state, new LeftRecursion(), err);
          failureExclusionsTriggered.addAll(
              nonStandardResult.lrExclusionsTriggered);
          switch (nonStandardResult.synopsis) {
            case FAILURE: break;
            case SUCCESS: return nonStandardResult;
          }
        }
      }
    } finally {
      if (DEBUG) { indent(-1); }
    }

    return ParseResult.failure();
  }

  private ParseState maybeParseInterstitialNonstandard(
      Stage stage, ParseState state, ParseErrorReceiver err) {
    if (stage != Stage.NOT_ON_STACK) {
      return state;
    }
    Optional<ParSer> nonstandardParser = nodeType.getGrammar()
        .parserForNonStandardInterstitial(nodeType);
    if (!nonstandardParser.isPresent()) { return state; }
    ParseResult result = nonstandardParser.get()
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
    state = unparseNonStandardInterstitial(state, err);
    // There might be decorations after the template directive.
    // TODO: maybe hide the handling of ignorable tokens from this class.
    state = state.shiftIgnorablesToOutput();

    // Try handling non-anon variants first.
    if (!state.isEmpty()) {
      Event e = state.structure.get(state.index);
      switch (e.getKind()) {
        case PUSH:
          NodeType<?, ?> pushed = e.getNodeType();
          if (pushed == nodeType
              || nodeType.getGrammar().isNonStandardReplacement(pushed)) {
            NodeVariant<?, ?> variant = e.getNodeVariant();
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
                    unparseNonStandardInterstitial(
                        afterContent.advanceWithCopy(), err));
              }
            }
            if (DEBUG_UP) {
              System.err.println(
                  indent() + "Same variant " + variant + " failed");
            }
            err.error(state, "Failed to match " + variant);
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
    for (NodeVariant<?, ?> v : this.variants) {
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
            unparseNonStandardInterstitial(
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

  private SerialState unparseNonStandardInterstitial(
      SerialState start, SerialErrorReceiver err) {
    if (!start.isEmpty()) {
      Event e = start.structure.get(start.index);
      if (e.getKind() == Event.Kind.PUSH) {
        NodeType<?, ?> pushed = e.getNodeType();
        if (pushed.isNonStandard() && pushed != nodeType
            && nodeType.getGrammar().isNonStandardInterstitial(pushed)) {
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
    }
    return start;
  }

  @Override
  public Optional<MatchState> match(
      MatchState state, MatchErrorReceiver err) {
    initLazy();
    return Alternation.of(variants).getParSer().match(state, err);
  }

  @Override
  public ForceFitState forceFit(ForceFitState state) {
    // We do not recurse to the variants.  Instead, we coerce try to match a
    // part against this reference's node type.

    initLazy();
    if (state.fits.isEmpty()) { return state; }
    if (nodeType.isNonStandard()) {
      return state.withFits(ImmutableSet.of());
    }

    int nParts = state.parts.size();

    ImmutableSet.Builder<ForceFitState.PartialFit> b = ImmutableSet.builder();
    // Try to advance each fit by one.
    for (ForceFitState.PartialFit f : state.fits) {
      if (f.index == nParts) { continue; }
      ForceFitState.FitPart p = state.parts.get(f.index);
      if (p instanceof ForceFitState.FixedNode) {
        ForceFitState.FixedNode fn = (FixedNode) p;
        if (reachableViaAnon(this.nodeType, fn.child.getNodeType())) {
          b.add(f.advance());
        }
      } else if (p instanceof ForceFitState.InterpolatedValue) {
        ForceFitState.InterpolatedValue iv = (InterpolatedValue) p;
        Optional<? extends BaseNode<?, ?, ?>> wrapped = coerceAndWrap(
            nodeType.getGrammar(), nodeType, iv.value);
        if (wrapped.isPresent()) {
          b.add(f.advanceAndResolve(wrapped.get()));
        }
      }
    }
    return state.withFits(b.build());
  }

  private static
  <BN extends BaseNode<BN, NT, ?>,
   NT extends Enum<NT> & NodeType<BN, NT>>
  Optional<? extends BaseNode<?, ?, ?>> coerceAndWrap(
      Grammar<BN, NT> g, NodeType<?, ?> t, Object interpValue) {
    NT nodeType = g.cast(t);
    Object value = g.tryToCoerce(interpValue, g.cast(nodeType));
    // If what we end up with after that coercion is a node, check that it
    // can be reached from this nodeType and generate any intermediates we
    // need.
    if (value instanceof BaseNode<?, ?, ?>) {
      return g.wrap((BaseNode<?, ?, ?>) value, nodeType);
    }
    return Optional.absent();
  }

  private boolean growReachedLR(int startIndex, ParseState s) {
    int nOtherLREndsSeen = 0;
    for (SList<Event> o = s.output; o != null; o = o.prev) {
      Event e = o.x;
      switch (e.getKind()) {
        case LR_END:
          if (e.getNodeType() == nodeType) {
            return e.getContentIndex() == startIndex;
          } else {
            ++nOtherLREndsSeen;
          }
          break;
        case LR_START:
          if (nOtherLREndsSeen == 0) {
            return false;
          }
          --nOtherLREndsSeen;
          break;
        default:
          break;
      }
    }
    throw new AssertionError("Missing LRStart");
  }

  private static final class LRRewriter {
    private final NodeType<?, ?> nodeTypeToPushback;
    private final List<List<Event>> pushback = Lists.newArrayList();
    private int popDepth;

    LRRewriter(NodeType<?, ?> nodeType) {
      this.nodeTypeToPushback = nodeType;
    }

    SList<Event> rewrite(SList<Event> out) {
      if (DEBUG_LR) {
        String indent = indent();
        System.err.println(indent + "before rewriteLR " + nodeTypeToPushback);
        Debug.dumpEvents(indent, SList.forwardIterable(out), System.err);
      }
      SList<Event> withPushbackAndPops = pushback(out);
      Preconditions.checkState(pushback.isEmpty());
      if (DEBUG_LR) {
        String indent = indent();
        System.err.println(indent + "after rewriteLR " + nodeTypeToPushback);
        Debug.dumpEvents(
            indent, SList.forwardIterable(withPushbackAndPops), System.err);
      }
      return withPushbackAndPops;
    }

    /**
     * Scan until we find the push of the seed and distribute pushes and pops
     * from LRSuffix events.
     */
    private SList<Event> pushback(SList<Event> outWithLR) {
      if (DEBUG_LR) {
        String indent = indent();
        System.err.println(
            indent + "pushback("
            + (outWithLR != null ? "...," + outWithLR.x : "<null>")
            + ", popDepth=" + popDepth + ", pushback=" + pushback + ")");
      }

      // Accumulates, in reverse, events that do not need to be pushed back.
      SList<Event> pbReverse = null;

      outer_loop:
      for (SList<Event> out = outWithLR; true;
           // No increment.
           // All continues are explicit because of the throw below the switch.
           //
           // The flow control here is odd, but it is simpler to think of this
           // as a recursive algorithm but where recursive calls have been
           // replaced with
           //     { out = recursiveOut; continue outer_loop; }
           // so instead of
           //     return SList.append(pushback(out.prev), e);
           // the non-recursive
           //     pbReverse = SList.append(pbReverse, e);
           //     out = out.prev;
           //     continue outer_loop;
           // with a revAppend on the final return.
           //
           // The original recursive algo was abandoned because it made the
           // max parsable input size dependent on the VM's max stack size.
          ) {
        Preconditions.checkNotNull(out);

        Event e = out.x;

        switch (e.getKind()) {
          case POP:
            ++popDepth;
            pbReverse = SList.append(pbReverse, e);
            out = out.prev;
            continue outer_loop;
          case PUSH:
            Preconditions.checkState(popDepth != 0);  // pop required above.
            --popDepth;
            if (popDepth == 0) {
              Preconditions.checkState(
                  nodeTypeToPushback == e.getNodeType()
                  // TODO: Allow any non-standard replacement.
                  || e.getNodeType() == J8NodeType.TemplateInterpolation);
              SList<Event> pushedBack = out.prev;
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
              return SList.revAppendAll(pushedBack, pbReverse);
            } else {
              out = out.prev;
              pbReverse = SList.append(pbReverse, e);
              continue outer_loop;
            }
          case LR_END:
            if (e.getNodeType() == nodeTypeToPushback) {
              int pushCount = 0;
              int popCount = 0;

              List<Event> onePb = Lists.newArrayList();
              pushback.add(onePb);
              for (SList<Event> c = out.prev; c != null; c = c.prev) {
                Event ce = c.x;
                switch (ce.getKind()) {
                  case LR_START:
                    Preconditions.checkState(pushCount >= popCount);
                    popDepth += popCount - pushCount;
                    Preconditions.checkState(popDepth >= 0);
                    out = c.prev;
                    continue outer_loop;
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
            }
            //$FALL-THROUGH$
          case CONTENT:
          case DELAYED_CHECK:
          case IGNORABLE:
          case LR_START:
          case POSITION_MARK:
          case TOKEN:
            pbReverse = SList.append(pbReverse, e);
            out = out.prev;
            continue outer_loop;
        }
        throw new AssertionError(e);
      }
    }
  }

  private static final
  LoadingCache<NodeType<?, ?>, ImmutableSet<NodeType<?, ?>>> REACHABLE_VIA_ANON
      = CacheBuilder.newBuilder().build(
          new CacheLoader<NodeType<?, ?>, ImmutableSet<NodeType<?, ?>>>() {

            @Override
            public ImmutableSet<NodeType<?, ?>> load(NodeType<?, ?> t)
            throws Exception {
              Set<NodeType<?, ?>> reachable = new LinkedHashSet<>();
              Deque<NodeType<?, ?>> unprocessed = new ArrayDeque<>();
              unprocessed.add(t);
              while (!unprocessed.isEmpty()) {
                NodeType<?, ?> nt = unprocessed.poll();
                if (!reachable.add(nt)) {
                  continue;
                }
                for (Enum<?> nodeVariant
                     : nt.getVariantType().getEnumConstants()) {
                  NodeVariant<?, ?> v = (NodeVariant<?, ?>) nodeVariant;
                  if (v.isAnon()) {
                    NodeType<?, ?> referent = getReferent(
                        (PTParSer) v.getParSer());
                    unprocessed.add(Preconditions.checkNotNull(referent));
                  }
                }
              }
              return ImmutableSet.copyOf(reachable);
            }

            private NodeType<?, ?> getReferent(PTParSer p) {
              switch (p.getKind()) {
                case ALT:
                  return null;
                case CAT:
                  for (ParSerable child : ((Concatenation) p).ps) {
                    NodeType<?, ?> r = getReferent(
                        (PTParSer) child.getParSer());
                    if (r != null) { return r; }
                  }
                  return null;
                case LA:
                case LIT:
                case REP:
                case REX:
                  return null;
                case REF:
                  return ((Reference) p).getNodeType();
              }
              throw new AssertionError(p);
            }
          });

  /** True if there is path from src to tgt only via {@code @anon} variants. */
  static boolean reachableViaAnon(NodeType<?, ?> src, NodeType<?, ?> tgt) {
    if (src == tgt) { return true; }
    ImmutableSet<NodeType<?, ?>> reachable;
    try {
      reachable = REACHABLE_VIA_ANON.get(src);
    } catch (ExecutionException ex) {
      throw (AssertionError) new AssertionError(src).initCause(ex);
    }
    return reachable.contains(tgt);
  }

  static Set<NodeType<?, ?>> leafsReachableViaAnon(NodeType<?, ?> src) {
    if (isLeafNodeType(src)) {
      return ImmutableSet.of(src);
    }
    ImmutableSet<NodeType<?, ?>> reachable;
    try {
      reachable = REACHABLE_VIA_ANON.get(src);
    } catch (ExecutionException ex) {
      throw (AssertionError) new AssertionError(src).initCause(ex);
    }
    return Sets.filter(
        reachable,
        new Predicate<NodeType<?, ?>>() {

          @Override
          public boolean apply(NodeType<?, ?> t) {
            return t != null && isLeafNodeType(t);
          }

        });
  }

  static boolean isLeafNodeType(NodeType<?, ?> nt) {
    return LeafNode.class.isAssignableFrom(nt.getNodeBaseType());
  }

}
