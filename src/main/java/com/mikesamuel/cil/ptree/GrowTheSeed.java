package com.mikesamuel.cil.ptree;

import java.util.concurrent.ExecutionException;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.mikesamuel.cil.ast.LeftRecursivePaths;
import com.mikesamuel.cil.ast.MatchEvent;
import com.mikesamuel.cil.ast.NodeType;
import com.mikesamuel.cil.ast.NodeVariant;
import com.mikesamuel.cil.parser.ParSer;
import com.mikesamuel.cil.parser.ParSerable;

/**
 * Support for Warth's "grow the seed" algorithm for handling LR in analytic
 * parsers.
 */
public final class GrowTheSeed {
  /**
   * The seed -- any non-LR variants which mimics the production when the LR
   * is known to fail to recurse.
   */
  public final ParSerable seed;
  /**
   * A repetition of suffixes that grow the seed.
   *
   */
  public final ParSerable suffix;

  private static final AppendMatchEvent APPEND_POP =
      new AppendMatchEvent(MatchEvent.pop());

  private GrowTheSeed(ParSerable seed, ParSerable suffix) {
    this.seed = seed;
    this.suffix = suffix;
  }

  private static final LoadingCache<NodeType, GrowTheSeed> CACHE =
      CacheBuilder.newBuilder()
      .build(
          new CacheLoader<NodeType, GrowTheSeed>() {

            @SuppressWarnings("synthetic-access")
            @Override
            public GrowTheSeed load(NodeType nt) {
              return split(nt);
            }

          });

  /**
   * The grower for the given node type.
   */
  public static GrowTheSeed of(NodeType nt) {
    try {
      return CACHE.get(nt);
    } catch (ExecutionException ex) {
      throw (AssertionError) new AssertionError().initCause(ex);
    }
  }

  private static final boolean DEBUG = false;

  private static GrowTheSeed split(NodeType nt) {
    if (DEBUG) {
      System.err.println("GrowTheSeed " + nt);
    }

    ImmutableList<NodeVariant> variants = ImmutableList.copyOf(
        Lists.transform(
            ImmutableList.copyOf(nt.getVariantType().getEnumConstants()),
            new Function<Enum<?>, NodeVariant>() {
              @Override
              public NodeVariant apply(Enum<?> ev) {
                return (NodeVariant) ev;
              }
            })
        );

    ImmutableList.Builder<ParSerable> seeds = ImmutableList.builder();
    ImmutableList.Builder<ParSerable> suffixes = ImmutableList.builder();

    for (NodeVariant nv : variants) {
      ParSer body = nv.getParSer();

      Decomposition d = decompose(nv, body, true);
      if (DEBUG) {
        System.err.println(
            "\nsplit " + nv + " : " + body
            + "\n\tseed: " + d.seed + "\n\tsuff: " + d.suffix
            + "\n\thasLeftCall: " + d.hasLeftCall + "\n");
      }
      seeds.add(Concatenation.of(ImmutableList.of(
          new AppendMatchEvent(MatchEvent.push(nv)),
          d.seed, APPEND_POP)));
      if (d.hasLeftCall) {
        suffixes.add(d.suffix);
      }
    }

    ParSerable seed = Alternation.of(seeds.build());
    ParSerable suffix = Alternation.of(suffixes.build());

    if (DEBUG) {
      System.err.println("\nseed\n" + seed);
      System.err.println("\nsuffix\n" + suffix);
    }

    if (seed == Alternation.NULL_LANGUAGE) {
      throw new AssertionError("LR production " + nt + " cannot be handled");
    }

    return new GrowTheSeed(seed, suffix);
  }

  private static Decomposition decompose(
      NodeVariant nv, Alternation p, boolean leftCallPossible) {
    ImmutableList.Builder<ParSerable> seedEls = ImmutableList.builder();
    ImmutableList.Builder<ParSerable> suffixEls = ImmutableList.builder();
    boolean leftCallStillPossible = leftCallPossible;
    boolean hasLeftCall = false;
    for (ParSerable c : p.ps) {
      Decomposition d = decompose(nv, c, leftCallPossible);
      seedEls.add(d.seed);
      if (!leftCallPossible || d.hasLeftCall) {
        suffixEls.add(d.suffix);
      }
      if (!d.leftCallsPossible) {
        leftCallStillPossible = false;
      }
      hasLeftCall |= d.hasLeftCall;
    }
    ParSerable suffix = Alternation.of(suffixEls.build());
    return new Decomposition(
        Alternation.of(seedEls.build()),
        suffix,
        leftCallStillPossible,
        hasLeftCall && !alwaysFails(suffix));
  }

  private static Decomposition decompose(
      NodeVariant nv, Concatenation p, boolean leftCallPossible) {
    ImmutableList.Builder<ParSerable> seedEls = ImmutableList.builder();
    ImmutableList.Builder<ParSerable> suffixEls = ImmutableList.builder();
    boolean leftCallStillPossible = leftCallPossible;
    boolean hasLeftCall = false;
    for (ParSerable c : p.ps) {
      Decomposition d = decompose(nv, c, leftCallStillPossible);

      seedEls.add(d.seed);
      suffixEls.add(d.suffix);

      leftCallStillPossible = d.leftCallsPossible;
      if (d.hasLeftCall) {
        hasLeftCall = true;
      }
    }
    ParSerable suffix = Concatenation.of(suffixEls.build());
    return new Decomposition(
        Concatenation.of(seedEls.build()),
        suffix,
        leftCallStillPossible,
        hasLeftCall && !alwaysFails(suffix));
  }

  private static Decomposition decompose(
      @SuppressWarnings("unused") NodeVariant nv, Literal p,
      boolean leftCallPossible) {
    return new Decomposition(p, p, leftCallPossible && p.text.isEmpty(), false);
  }

  private static Decomposition decompose(
      NodeVariant nv, Repetition p, boolean leftCallPossible) {
    Decomposition d = decompose(nv, p.p, leftCallPossible);

    ParSerable seed, suffix;

    // If we encountered a transition from allowing left calls to not allowing
    // left calls then we need to split the seed into an optional use of the
    // non-left recursing variant followed by a possibly recursing loop.
    if (d.seed == p.p) {
      seed = p;
    } else {
      boolean needToSplit = (leftCallPossible && !d.leftCallsPossible);
      if (needToSplit) {
        seed = Alternation.of(ImmutableList.of(
            Concatenation.of(ImmutableList.of(d.seed, p)),
            Concatenation.EMPTY));
      } else {
        seed = Repetition.of(d.seed);
      }
    }

    suffix = Repetition.of(d.suffix);

    return new Decomposition(seed, suffix, d.leftCallsPossible, d.hasLeftCall);
  }

  private static Decomposition decompose(
      @SuppressWarnings("unused") NodeVariant nv,
      PatternMatch p, boolean leftCallPossible) {
    boolean leftCallStillPossible;
    if (leftCallPossible && p.p.matcher("").find()) {
      // Should not happen in practice
      leftCallStillPossible = leftCallPossible;
    } else {
      leftCallStillPossible = false;
    }
    return new Decomposition(p, p, leftCallStillPossible, false);
  }

  private static Decomposition decompose(
      @SuppressWarnings("unused") NodeVariant nv,
      Lookahead la, boolean leftCallPossible) {
    return new Decomposition(la, la, leftCallPossible, false);
  }

  private static Decomposition decompose(
      NodeVariant nv, Reference p, boolean leftCallPossible) {
    ImmutableList<NodeVariant> leftCallChain;
    if (leftCallPossible) {
      leftCallChain = LeftRecursivePaths.LR_CYCLES.get(nv, p.getNodeType());
    } else {
      leftCallChain = null;
    }

    boolean leftCallStillPossible =
        leftCallPossible
        && LeftRecursivePaths.EPSILON_MATCHERS.contains(p.getNodeType());

    if (leftCallChain != null) {
      return new Decomposition(
          Alternation.NULL_LANGUAGE,
          new AppendMatchEvent(MatchEvent.leftRecursionSuffix(leftCallChain)),
          leftCallStillPossible,
          true);

    } else {
      return new Decomposition(
          p, p,
          leftCallStillPossible,
          false);
    }
  }

  private static Decomposition decompose(
      NodeVariant nv, ParSerable ps, boolean leftCallPossible) {
    PTParSer pts = (PTParSer) ps.getParSer();
    PTParSer.Kind k = pts.getKind();
    Decomposition d = null;
    switch (k) {
      case ALT: d = decompose(nv, (Alternation)   pts, leftCallPossible); break;
      case CAT: d = decompose(nv, (Concatenation) pts, leftCallPossible); break;
      case LIT: d = decompose(nv, (Literal)       pts, leftCallPossible); break;
      case REF: d = decompose(nv, (Reference)     pts, leftCallPossible); break;
      case REP: d = decompose(nv, (Repetition)    pts, leftCallPossible); break;
      case REX: d = decompose(nv, (PatternMatch)  pts, leftCallPossible); break;
      case LA:  d = decompose(nv, (Lookahead)     pts, leftCallPossible); break;
    }
    Preconditions.checkNotNull(d, k);  // switch should cover all cases

    if (DEBUG) {
      System.err.println(
          "Decomposed " + pts
          + " : " + pts.getClass().getSimpleName() + ", nv=" + nv);
      System.err.println("\tleftCallPossible=" + leftCallPossible);
      System.err.println("\td.hasLeftCall=" + d.hasLeftCall);
      System.err.println("\td.leftCallsPossible=" + d.leftCallsPossible);
      System.err.println("\td.seed=" + d.seed);
      System.err.println("\td.suffix=" + d.suffix);
    }
    return d;
  }


  private static boolean alwaysFails(ParSerable ps) {
    ParSer p = ps.getParSer();
    if (p instanceof Alternation) {
      Alternation alt = (Alternation) p;
      return alt.ps.isEmpty();
    }
    return false;
  }


  private static final class Decomposition {
    final ParSerable seed;
    final ParSerable suffix;
    final boolean leftCallsPossible;
    final boolean hasLeftCall;

    Decomposition(
        ParSerable seed, ParSerable suffix, boolean leftCallsPossible,
        boolean hasLeftCall) {
      this.seed = seed;
      this.suffix = suffix;
      this.leftCallsPossible = leftCallsPossible;
      this.hasLeftCall = hasLeftCall;
    }
  }
}
