package com.mikesamuel.cil.ast;

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
import com.mikesamuel.cil.parser.SList;

/**
 * Uses {@linkplain NodeVariant#getDelegate() delegate} relationships to make
 * it easy to interpolate a sub-tree into a larger AST.
 */
public final class Intermediates {

  private static final class IntermediateGraphNode {
    final NodeVariant via;
    final NodeType to;

    IntermediateGraphNode(NodeVariant via, NodeType to) {
      this.via = via;
      this.to = to;
    }

    @Override
    public String toString() {
      return "(" + via.getNodeType() + " -> " + to + " via " + via + ")";
    }
  }

  private static final
  ImmutableMultimap<NodeType, IntermediateGraphNode> OUTBOUND;
  static {
    ImmutableMultimap.Builder<NodeType, IntermediateGraphNode> b =
        ImmutableMultimap.builder();
    for (NodeType nt : NodeType.values()) {
      for (Enum<?> e : nt.getVariantType().getEnumConstants()) {
        NodeVariant v = (NodeVariant) e;
        NodeType to = v.getDelegate();
        if (to != null) {
          b.put(nt, new IntermediateGraphNode(v, to));
        }
      }
    }
    OUTBOUND = b.build();
  }

  private static final class Endpoints {
    final NodeType from;
    final NodeType to;

    Endpoints(NodeType from, NodeType to) {
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
          NodeType from = pts.from;
          NodeType to = pts.to;
          if (from == to) {
            return Optional.absent();  // This absent means empty path.
          }

          @SuppressWarnings("synthetic-access")
          ImmutableMultimap<NodeType, IntermediateGraphNode> outbound =
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
  public static
  Optional<BaseNode> wrap(
      BaseNode inner, NodeType outerType,
      Function<? super BaseNode, ?> createdNodeReceiver) {
    NodeType to = inner.getNodeType();
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
    BaseNode result = inner;
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

}
