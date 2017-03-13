package com.mikesamuel.cil.ast.j8;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Sets;
import com.mikesamuel.cil.ast.NodeVariant;
import com.mikesamuel.cil.parser.SList;

/**
 * Uses {@linkplain NodeVariant#getDelegate() delegate} relationships to make
 * it easy to interpolate a sub-tree into a larger AST.
 */
public final class Intermediates {

  private static final
  class IntermediateGraphNode {
    final J8NodeVariant via;
    final J8NodeType to;

    IntermediateGraphNode(J8NodeVariant via, J8NodeType to) {
      this.via = via;
      this.to = to;
    }

    @Override
    public String toString() {
      return "(" + via.getNodeType() + " -> " + to + " via " + via + ")";
    }
  }

  private static final
  ImmutableMultimap<J8NodeType, IntermediateGraphNode> OUTBOUND;
  static {
    ImmutableMultimap.Builder<J8NodeType, IntermediateGraphNode> b =
        ImmutableMultimap.builder();
    for (J8NodeType nt : J8NodeType.values()) {
      for (Enum<?> e : nt.getVariantType().getEnumConstants()) {
        J8NodeVariant v = (J8NodeVariant) e;
        J8NodeType to = v.getDelegate();
        if (to != null) {
          b.put(nt, new IntermediateGraphNode(v, to));
        }
      }
    }
    OUTBOUND = b.build();
  }

  private static final class Endpoints {
    final J8NodeType from;
    final J8NodeType to;

    Endpoints(J8NodeType from, J8NodeType to) {
      this.from = from;
      this.to = to;
    }

    @Override
    public int hashCode() {
      final int prime = 31;
      int result = 1;
      result = prime * result + ((from == null) ? 0 : from.hashCode());
      result = prime * result + ((to == null) ? 0 : to.hashCode());
      return result;
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      }
      if (obj == null) {
        return false;
      }
      if (getClass() != obj.getClass()) {
        return false;
      }
      Endpoints other = (Endpoints) obj;
      if (from != other.from) {
        return false;
      }
      if (to != other.to) {
        return false;
      }
      return true;
    }

    @Override
    public String toString() {
      return "(from=" + from + ", to=" + to + ")";
    }
  }

  private static final
  LoadingCache<Endpoints,
               Optional<SList<IntermediateGraphNode>>> BETWEEN_CACHE =
     CacheBuilder.newBuilder()
      .build(new CacheLoader<Endpoints,
                             Optional<SList<IntermediateGraphNode>>>() {

        @Override
        public Optional<SList<IntermediateGraphNode>> load(Endpoints pts) {
          J8NodeType from = pts.from;
          J8NodeType to = pts.to;
          if (from == to) {
            return Optional.absent();  // This absent means empty path.
          }

          @SuppressWarnings("synthetic-access")
          ImmutableMultimap<J8NodeType, IntermediateGraphNode> outbound =
              OUTBOUND;

          Deque<SList<IntermediateGraphNode>> dq = new ArrayDeque<>();
          for (IntermediateGraphNode g : outbound.get(from)) {
            dq.add(SList.append(null, g));
          }
          Set<IntermediateGraphNode> reached = Sets.newIdentityHashSet();
          while (!dq.isEmpty()) {
            SList<IntermediateGraphNode> path = dq.removeFirst();
            if (!reached.add(path.x)) { continue; }
            if (path.x.to == to) {
              return Optional.of(path);
            }
            for (IntermediateGraphNode g : outbound.get(path.x.to)) {
              dq.add(SList.append(path, g));
            }
          }
          return Optional.absent();  // This absent means no path.
        }

      });

  /**
   * Wraps a node in intermediate nodes to produce a node of the given outer
   * type.
   *
   * @see NodeVariant#getDelegate()
   */
  public static Optional<J8BaseNode> wrap(
      J8BaseNode inner, J8NodeType outerType,
      Function<? super J8BaseNode, ?> createdNodeReceiver) {
    J8NodeType to = inner.getNodeType();
    if (to == outerType) {
      return Optional.of(inner);
    }
    Endpoints pts = new Endpoints(outerType, to);
    Optional<SList<IntermediateGraphNode>> pathOpt;
    try {
      pathOpt = BETWEEN_CACHE.get(pts);
    } catch (ExecutionException ex) {
      throw new AssertionError(null, ex);
    }
    if (!pathOpt.isPresent()) {
      return Optional.absent();
    }
    J8BaseNode result = inner;
    for (SList<IntermediateGraphNode> path = pathOpt.get();
         path != null; path = path.prev) {
      IntermediateGraphNode g = path.x;
      if (!g.via.isAnon()) {
        result = g.via.buildNode(ImmutableList.of(result));
        result.setSourcePosition(inner.getSourcePosition());
      }
      createdNodeReceiver.apply(result);
    }
    return Optional.of(result);
  }

  /**
   * True iff target is reachable from source only via intermediate variants.
   */
  public static boolean reachedFrom(J8NodeType target, J8NodeType source) {
    if (target == source) {
      return true;
    }
    Endpoints pts = new Endpoints(source, target);
    Optional<SList<IntermediateGraphNode>> pathOpt;
    try {
      pathOpt = BETWEEN_CACHE.get(pts);
    } catch (ExecutionException ex) {
      throw new AssertionError(null, ex);
    }
    return pathOpt.isPresent();
  }
}
