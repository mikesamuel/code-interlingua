package com.mikesamuel.cil.ptree;

import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import com.google.common.base.Optional;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import com.mikesamuel.cil.ast.LeftRecursivePaths;
import com.mikesamuel.cil.ast.NodeType;
import com.mikesamuel.cil.ast.NodeVariant;
import com.mikesamuel.cil.parser.Chain;
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

  private static GrowTheSeed split(NodeType nt) {
    System.err.println("GrowTheSeed " + nt);
    ImmutableList.Builder<ParSerable> seeds = ImmutableList.builder();
    ImmutableList.Builder<ParSerable> suffixes = ImmutableList.builder();

    for (Enum<?> ev : nt.getVariantType().getEnumConstants()) {
      NodeVariant nv = (NodeVariant) ev;
      ParSer body = ((VariantWrapper) nv.getParSer()).child.getParSer();

      Decomposition d = decompose(nv, body, true);
      System.err.println(
          "split " + nv + " : " + body
          + "\n\tseed: " + d.seed + "\n\tsuff: " + d.suffix
          + "\n\thasLeftCall: " + d.hasLeftCall);
      seeds.add(d.seed);
      if (d.hasLeftCall) {
        suffixes.add(d.suffix);
      }
    }

    ParSerable seed = Alternation.of(seeds.build());
    ParSerable suffix = Alternation.of(suffixes.build());

    System.err.println("\nseed\n" + seed);
    System.err.println("\nsuffix\n" + suffix);
//    if (true) throw new Error();

    return new GrowTheSeed(seed, suffix);
  }

  private static Decomposition decompose(
      NodeVariant nv, Alternation p, boolean leftCallPossible) {
    ImmutableList.Builder<ParSerable> seedEls = ImmutableList.builder();
    ImmutableList.Builder<ParSerable> suffixEls = ImmutableList.builder();
    boolean leftCallStillPossible = leftCallPossible;
    for (ParSerable c : p.ps) {
      Decomposition d = decompose(nv, c.getParSer(), leftCallPossible);
      seedEls.add(d.seed);
      if (d.hasLeftCall) {
        suffixEls.add(d.suffix);
      }
      if (!d.leftCallsPossible) {
        leftCallStillPossible = false;
      }
    }
    ParSerable suffix = Alternation.of(suffixEls.build());
    return new Decomposition(
        Alternation.of(seedEls.build()),
        suffix,
        leftCallStillPossible,
        !alwaysFails(suffix));
  }

  private static Decomposition decompose(
      NodeVariant nv, Concatenation p, boolean leftCallPossible) {
    ImmutableList.Builder<ParSerable> seedEls = ImmutableList.builder();
    ImmutableList.Builder<ParSerable> suffixEls = ImmutableList.builder();
    boolean leftCallStillPossible = leftCallPossible;
    boolean hasLeftCall = false;
    for (ParSerable c : p.ps) {
      Decomposition d = decompose(nv, c.getParSer(), leftCallStillPossible);

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
    Decomposition d = decompose(nv, p.p.getParSer(), leftCallPossible);

    ParSerable seed, suffix;

    // If we encountered a transition from allowing left calls to not allowing
    // left calls then we need to split the seed into an optional use of the
    // non-left recursing variant followed by a possibly recursing loop.
    boolean needToSplit = (leftCallPossible && !d.leftCallsPossible);
    if (needToSplit) {
      seed = Concatenation.of(ImmutableList.of(d.seed, p));
    } else {
      seed = Repetition.of(d.seed);
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
      NodeVariant nv, VariantWrapper p, boolean leftCallPossible) {
    return decompose(nv, p.child.getParSer(), leftCallPossible);
  }

  private static Decomposition decompose(
      NodeVariant nv, Reference p, boolean leftCallPossible) {
    Optional<ImmutableList<NodeVariant>> leftCallChain;
    if (leftCallPossible) {
      leftCallChain = findShortestLeftCallChainBetween(p.nodeType, nv);
    } else {
      leftCallChain = Optional.absent();
    }
    System.err.println(
        "leftCallChain from " + p.name + "." + p.name
        + " to " + nv.getNodeType() + "." + nv + " = " + leftCallChain);

    boolean leftCallStillPossible =
        leftCallPossible
        && LeftRecursivePaths.EPSILON_MATCHERS.contains(p.nodeType);

    if (leftCallChain.isPresent()) {
      return new Decomposition(
          Alternation.NULL_LANGUAGE,
          new AppendSuffixMarkers(
              ImmutableList.<NodeVariant>builder()
              .add(nv)
              .addAll(leftCallChain.get())
              .build()),
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
      NodeVariant nv, ParSer ps, boolean leftCallPossible) {
    PTParSer pts = (PTParSer) ps.getParSer();
    PTParSer.Kind k = pts.getKind();
    switch (k) {
      case ALT:   return decompose(nv, (Alternation)    pts, leftCallPossible);
      case CAT:   return decompose(nv, (Concatenation)  pts, leftCallPossible);
      case LIT:   return decompose(nv, (Literal)        pts, leftCallPossible);
      case REF:   return decompose(nv, (Reference)      pts, leftCallPossible);
      case REP:   return decompose(nv, (Repetition)     pts, leftCallPossible);
      case REX:   return decompose(nv, (PatternMatch)   pts, leftCallPossible);
      case VNT:   return decompose(nv, (VariantWrapper) pts, leftCallPossible);
    }
    throw new AssertionError(k);
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

  private static
  Optional<ImmutableList<NodeVariant>> findShortestLeftCallChainBetween(
      NodeType src, NodeVariant dest) {
    Set<NodeType> visited = Sets.newLinkedHashSet();
    visited.add(src);
    SearchResult result = shortestLeftCallChain(
        ((Reference) src.getParSer()).body, dest, visited);
    if (result instanceof Solution) {
      ImmutableList<NodeVariant> chainAsList = ImmutableList.copyOf(
          ((Solution) result).v.reverse());
      int n = chainAsList.size();
      return Optional.of(chainAsList.subList(0, n - 1));
    }
    return Optional.absent();
  }

  private static abstract class SearchResult {
    // Result of searching for a cycle between.
  }
  private static final class Solution extends SearchResult {
    final Chain<NodeVariant> v;
    @SuppressWarnings("synthetic-access")
    Solution(Chain<NodeVariant> v) {
      this.v = v;
    }
  }
  @SuppressWarnings("synthetic-access")
  private static final SearchResult DISALLOW_LEFT_CALLS = new SearchResult() {
    @Override public String toString() { return "disallow_left_calls"; }
  };
  @SuppressWarnings("synthetic-access")
  private static final SearchResult CONTINUE = new SearchResult() {
    @Override public String toString() { return "continue"; }
  };

  // TODO: memoize this as it's likely to be used several times within the same
  // production.
  private static
  SearchResult shortestLeftCallChain(
      ParSer p, NodeVariant dest, Set<NodeType> visited) {
    //System.err.println("Looking for shortest in " + p);
    PTParSer.Kind k = ((PTParSer) p).getKind();
    switch (k) {
      case VNT: {
        VariantWrapper vw = (VariantWrapper) p;
        if (vw.v == dest) {
          return new Solution(Chain.append(null, dest));
        }
        SearchResult r = shortestLeftCallChain(
            vw.child.getParSer(), dest, visited);
        if (r instanceof Solution) {
          return new Solution(Chain.append(((Solution) r).v, vw.v));
        } else {
          return r;
        }
      }
      case ALT:
        Alternation alt = (Alternation) p;
        boolean continueAny = false;
        for (ParSerable c : alt.ps) {
          SearchResult r = shortestLeftCallChain(c.getParSer(), dest, visited);
          if (r instanceof Solution) {
            return r;
          } else if (r == CONTINUE) {
            continueAny = true;
          }
        }
        return continueAny ? CONTINUE : DISALLOW_LEFT_CALLS;
      case CAT:
        Concatenation cat = (Concatenation) p;
        for (ParSerable c : cat.ps) {
          SearchResult r = shortestLeftCallChain(c.getParSer(), dest, visited);
          if (r != CONTINUE) {
            return r;
          }
        }
        break;
      case REX:
      case LIT:
        return DISALLOW_LEFT_CALLS;
      case REP:
        return shortestLeftCallChain(
            ((Repetition) p).p.getParSer(), dest, visited);
      case REF:
        Reference ref = (Reference) p;
        if (visited.add(ref.nodeType)) {
          SearchResult sr = shortestLeftCallChain(
              ref.body.getParSer(), dest, visited);
          visited.remove(ref.nodeType);
          return sr;
        }
        if (LeftRecursivePaths.EPSILON_MATCHERS.contains(ref.nodeType)) {
          return CONTINUE;
        } else {
          return DISALLOW_LEFT_CALLS;
        }
    }
    throw new AssertionError(k);
  }
}
