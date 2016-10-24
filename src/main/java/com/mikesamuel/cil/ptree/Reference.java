package com.mikesamuel.cil.ptree;

import java.util.List;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.RangeSet;
import com.mikesamuel.cil.ast.MatchEvent;
import com.mikesamuel.cil.ast.MatchEvent.LRSuffix;
import com.mikesamuel.cil.ast.MatchEvent.PushMatchEvent;
import com.mikesamuel.cil.ast.NodeType;
import com.mikesamuel.cil.ast.NodeVariant;
import com.mikesamuel.cil.parser.Chain;
import com.mikesamuel.cil.parser.MatchErrorReceiver;
import com.mikesamuel.cil.parser.MatchState;
import com.mikesamuel.cil.parser.ParSer;
import com.mikesamuel.cil.parser.ParSerable;
import com.mikesamuel.cil.parser.ParseErrorReceiver;
import com.mikesamuel.cil.parser.ParseState;
import com.mikesamuel.cil.parser.RatPack;
import com.mikesamuel.cil.parser.RatPack.ParseCacheEntry;
import com.mikesamuel.cil.parser.SerialErrorReceiver;
import com.mikesamuel.cil.parser.SerialState;

final class Reference extends PTParSer {
  final String name;
  final NodeType nodeType;
  final ImmutableList<NodeVariant> variants;
  private GrowTheSeed growTheSeed;
  private LeftRecursionDone transferBackToStart;

  Reference(
      String name, Class<? extends Enum<? extends ParSerable>> variantClass) {
    this.name = name;
    ImmutableList.Builder<NodeVariant> variantsBuilder =
        ImmutableList.builder();
    NodeType nt = null;
    for (Enum<?> e : variantClass.getEnumConstants()) {
      NodeVariant nv = (NodeVariant) e;
      variantsBuilder.add(nv);
      if (nt == null) {
        nt = nv.getNodeType();
      } else {
        Preconditions.checkState(nt == nv.getNodeType());
      }
    }
    this.nodeType = Preconditions.checkNotNull(nt);
    this.variants = variantsBuilder.build();
  }

  @Override
  public String toString() {
    return name;
  }

  @Override
  Kind getKind() {
    return Kind.REF;
  }

  // HACK DEBUG: Not thread safe
  private static final boolean DEBUG = false;
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

  private static ImmutableList<MatchEvent> lastOutputSeen = ImmutableList.of();
  private static String dumpOutput(Chain<MatchEvent> out) {
    ImmutableList<MatchEvent> lastList = lastOutputSeen;
    ImmutableList<MatchEvent> outList = ImmutableList.copyOf(
        Chain.forward(out));
    lastOutputSeen = outList;
    if (!lastList.isEmpty() && outList.size() >= lastList.size()
        && outList.subList(0, lastList.size()).equals(lastList)) {
      if (lastList.size() == outList.size()) {
        return null;
      }
      StringBuilder sb = new StringBuilder("[...");
      for (MatchEvent e
           : outList.subList(lastList.size(), outList.size())) {
        sb.append(", ").append(e);
      }
      return sb.append(']').toString();
    }
    return outList.toString();
  }

  @Override
  public Optional<ParseState> parse(ParseState state, ParseErrorReceiver err) {
    ParseCacheEntry cachedParse = state.input.ratPack.getCachedParse(
        nodeType, state.index, RatPack.Kind.WHOLE);
    if (cachedParse.wasTried()) {
      if (cachedParse.passed()) {
        if (DEBUG) {
          System.err.println(indent() + "Using cached success for " + nodeType);
        }
        return Optional.of(cachedParse.apply(state));
      } else {
        if (DEBUG) {
          System.err.println(indent() + "Using cached failure for " + nodeType);
        }
        return Optional.absent();
      }
    }

    if (DEBUG) {
      System.err.println(indent() + "Entered " + nodeType);
      String din = dumpInput(state.input.content.substring(state.index));
      if (din != null) {
        System.err.println(indent() + ". . input=`" + din + "`");
      }
      String dout = dumpOutput(state.output);
      if (dout != null) {
        System.err.println(indent() + ". . output=" + dout);
      }
    }

    int idx = state.indexAfterIgnorables();
    int firstChar = idx < state.input.content.length()
        ? state.input.content.charAt(idx) : -1;

    boolean lookedForStartOfLeftRecursion = false;
    for (NodeVariant variant : variants) {
      ParSer variantParSer = variant.getParSer();
      if (firstChar >= 0 && variantParSer instanceof PTParSer) {
        RangeSet<Integer> la1 = ((PTParSer) variantParSer).getLookahead1();
        if (la1 != null && !la1.contains(firstChar)) {
          continue;
        }
      }

      if (variant.isLeftRecursive() && !lookedForStartOfLeftRecursion) {
        lookedForStartOfLeftRecursion = true;
        if (hasLeftRecursion(state)) {
          if (transferBackToStart == null) {
            transferBackToStart = new LeftRecursionDone(nodeType);
          }
          // We pre-parse the seed to make sure that the left-recursion
          // succeeds.  Otherwise we just fail.
          // This allows us to fail-over to later non-LR options.
          // For example, there is a prefix ambiguity between
          //     foo.new bar
          //     foo.bar
          //     foo.bar()
          // which is resolved by processing them in that order, but
          // the `new` operator production is left-recursive via Primary.
          if (this.parseSeed(state, err).isPresent()) {
            if (DEBUG) {
              System.err.println(indent() + "Throwing " + transferBackToStart);
            }
            throw transferBackToStart;
          } else {
            if (DEBUG) {
              System.err.println(indent() + variant + " had failing seed");
            }
            continue;
          }
        }
      }
      ParseState result = null;
      if (DEBUG) { indent(1); }
      try {
        try {
          Optional<ParseState> next = variantParSer.parse(
              state.appendOutput(MatchEvent.push(variant)), err);
          if (next.isPresent()) {
            result = next.get().appendOutput(MatchEvent.pop());
          }
        } finally {
          if (DEBUG) {
            indent(-1);
          }
        }
      } catch (LeftRecursionDone d) {
        if (d.leftRecursiveNodeType != nodeType) {
          if (DEBUG) {
            System.err.println(
                indent() + "LeftRecursing out of " + nodeType + " to "
                + d.leftRecursiveNodeType);
          }
          throw d;
        }
        if (DEBUG) {
          System.err.println(indent() + "Control returned to " + nodeType);
        }
        result = handleAsLeftRecursive(state, err).orNull();
        if (DEBUG) {
          System.err.println(
              indent() + "handleAsLeftRecursive produced " + result);
        }
      }
      if (DEBUG) {
        System.err.println(
            indent() + "Result for " + variant
               + (result != null ? " @ " + result.index : " is <null>"));
      }
      if (result != null) {
        state.input.ratPack.cache(
            state.index, result.index, RatPack.Kind.WHOLE, result.output);
        if (nodeType == NodeType.AmbiguousName) {
          Thread.dumpStack();
        }
        return Optional.of(result);
      }
    }
    if (DEBUG) {
      System.err.println(indent() + "Fail " + nodeType);
    }
    return Optional.absent();
  }

  private Optional<ParseState> parseSeed(
      ParseState beforeRecursing, ParseErrorReceiver err) {
    RatPack ratPack = beforeRecursing.input.ratPack;
    RatPack.ParseCacheEntry e = ratPack.getCachedParse(
        nodeType, beforeRecursing.index, RatPack.Kind.SEED);
    if (e.wasTried()) {
      if (e.passed()) {
        ParseState afterSeed = e.apply(beforeRecursing);
        if (DEBUG) {
          System.err.println(
              indent() + "Using cached seed @ " + afterSeed.index);
        }
        return Optional.of(afterSeed);
      } else {
        if (DEBUG) {
          System.err.println(indent() + "Seed failure cached");
        }
        return Optional.absent();
      }
    }

    GrowTheSeed gts = getGrowTheSeed();
    if (DEBUG) {
      System.err.println(indent() + ". . seed=" + gts.seed);
      System.err.println(indent() + ". . suffix=" + gts.suffix);
    }
    ParseState atStartOfLeftRecursion = beforeRecursing
        .appendOutput(MatchEvent.leftRecursionStart(nodeType));

    Optional<ParseState> afterSeedOpt =
        gts.seed.getParSer().parse(atStartOfLeftRecursion, err);

    // Since we apply the seed before popping the stack back to the initial
    // invocation which has to reapply the seed, storage of a successful result
    // here is guaranteed to be used.
    if (afterSeedOpt.isPresent()) {
      ParseState afterSeed = afterSeedOpt.get();
      ratPack.cache(
          beforeRecursing.index, afterSeed.index, RatPack.Kind.SEED,
          afterSeed.output);
    } else {
      ratPack.cacheFailure(
          beforeRecursing.index, nodeType, RatPack.Kind.SEED);
    }

    return afterSeedOpt;
  }

  private Optional<ParseState> handleAsLeftRecursive(
      ParseState beforeRecursing, ParseErrorReceiver err) {
    // Grow the seed instead of calling into it.
    GrowTheSeed gts = getGrowTheSeed();

    Optional<ParseState> afterSeedOpt = parseSeed(beforeRecursing, err);

    if (!afterSeedOpt.isPresent()) {
      return Optional.absent();
    }
    ParseState afterSeed = afterSeedOpt.get();

    if (DEBUG) {
      System.err.println(
          indent() + "AfterSeed " + nodeType
          + "\n" + indent() + ". . input=`"
          + afterSeed.input.content.substring(afterSeed.index) + "`"
          + "\n" + indent() + ". . output="
          + ImmutableList.copyOf(Chain.forward(afterSeed.output)));
    }

    ParseState afterSuffix = Repetition.parseRepeatedly(
        gts.suffix, afterSeed, err);
    if (DEBUG) {
      System.err.println(indent() + "LR passed");
    }
    return Optional.of(
        afterSuffix.withOutput(rewriteLRMatchEvents(afterSuffix.output)));
  }

  private boolean hasLeftRecursion(ParseState state) {
    int nUnpushedPops = 0;
    for (Chain<? extends MatchEvent> o = state.output;
        o != null; o = o.prev) {
      if (o.x.nCharsConsumed() > 0) {
        break;
      } else if (o.x instanceof MatchEvent.PopMatchEvent) {
        ++nUnpushedPops;
      } else if (o.x instanceof MatchEvent.PushMatchEvent) {
        if (nUnpushedPops == 0) {
          MatchEvent.PushMatchEvent push = (PushMatchEvent) o.x;
          if (push.variant.getNodeType() == nodeType) {
            return true;
          }
        } else {
          --nUnpushedPops;
        }
      }
    }
    return false;
  }

  private static Chain<MatchEvent> rewriteLRMatchEvents(Chain<MatchEvent> out) {
    List<MatchEvent> pushback = Lists.newArrayList();
    List<MatchEvent> pops = Lists.newArrayList();
    Chain<MatchEvent> withPushbackAndPops = doPushback(out, pushback, pops);
    for (MatchEvent pop : pops) {
      withPushbackAndPops = Chain.append(withPushbackAndPops, pop);
    }
    return withPushbackAndPops;
  }

  private static Chain<MatchEvent> doPushback(
      Chain<? extends MatchEvent> out, List<MatchEvent> pushback,
      List<MatchEvent> pops) {
    Preconditions.checkNotNull(out, "Did not find LRStart");
    if (out.x instanceof MatchEvent.LRStart) {
      Chain<MatchEvent> withPushback = Chain.copyOf(out.prev);
      for (MatchEvent pb : pushback) {
        withPushback = Chain.append(withPushback, pb);
      }
      return withPushback;
    } else if (out.x instanceof MatchEvent.LRSuffix) {
      Chain<MatchEvent> processed = Chain.copyOf(out.prev);
      MatchEvent.LRSuffix suffix = (LRSuffix) out.x;
      for (NodeVariant nv : suffix.variants) {
        pushback.add(MatchEvent.push(nv));
      }
      Chain<MatchEvent> withPushbackAndPops = doPushback(
          processed, pushback, pops);
      for (MatchEvent pop : pops) {
        withPushbackAndPops = Chain.append(withPushbackAndPops, pop);
      }
      pops.clear();
      for (@SuppressWarnings("unused") NodeVariant nv : suffix.variants) {
        // Pop corresponding to pushback added above.
        pops.add(MatchEvent.pop());
      }
      return withPushbackAndPops;
    } else {
      return Chain.append(doPushback(out.prev, pushback, pops), out.x);
    }
  }

  private GrowTheSeed getGrowTheSeed() {
    if (this.growTheSeed == null) {
      this.growTheSeed = GrowTheSeed.of(nodeType);
    }
    return this.growTheSeed;
   }

  @Override
  public Optional<SerialState> unparse(
      SerialState state, SerialErrorReceiver err) {
    return Alternation.of(variants).getParSer().unparse(state, err);
  }

  @Override
  public Optional<MatchState> match(
      MatchState state, MatchErrorReceiver err) {
    return Alternation.of(variants).getParSer().match(state, err);
  }

  @Override
  RangeSet<Integer> computeLookahead1() {
    if (variants.get(0).isLeftRecursive()) {
      GrowTheSeed gts = this.getGrowTheSeed();
      // TODO: This is probably not sound.
      ParSer seed = gts.seed.getParSer();
      if (seed != Alternation.NULL_LANGUAGE) {
        if (seed instanceof PTParSer) {
          return ((PTParSer) seed).getLookahead1();
        }
      }
    }
    return ((PTParSer) (Alternation.of(variants).getParSer()))
        .getLookahead1();
  }


  /**
   * Thrown to do a non-local transfer of control from where left-recursion is
   * detected to where it started.
   */
  static final class LeftRecursionDone extends Error {

    private static final long serialVersionUID = 1L;

    final NodeType leftRecursiveNodeType;

    LeftRecursionDone(NodeType nt) {
      this.setStackTrace(new StackTraceElement[0]);
      this.leftRecursiveNodeType = nt;
    }

    @Override
    public String toString() {
      return getClass().getSimpleName() + "(" + leftRecursiveNodeType + ")";
    }
  }
}
