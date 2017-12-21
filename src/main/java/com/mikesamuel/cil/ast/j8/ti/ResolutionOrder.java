package com.mikesamuel.cil.ast.j8.ti;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;

final class ResolutionOrder {

  final ImmutableList<Clique> cliquesInResolutionOrder;

  private ResolutionOrder(
      ImmutableList<Clique> cliquesInResolutionOrder) {
    this.cliquesInResolutionOrder = cliquesInResolutionOrder;
  }

  /** A minimal group of nodes that are mutually dependent. */
  static final class Clique {
    private final List<Node> members = new ArrayList<>();

    Clique(Node n) {
      members.add(n);
    }

    ImmutableList<Node> members() { return ImmutableList.copyOf(members); }

    void mergeInto(Clique other) {
      if (other != this) {
        for (Node member : members) {
          other.members.add(member);
          member.setClique(other);
        }
        members.clear();
      }
    }

    ImmutableSet<InferenceVariable> vars() {
      ImmutableSet.Builder<InferenceVariable> b = ImmutableSet.builder();
      for (Node node : members) {
        if (node instanceof VarNode) {
          b.add(((VarNode) node).v);
        }
      }
      return b.build();
    }

    ImmutableSet<Bound> bounds() {
      ImmutableSet.Builder<Bound> b = ImmutableSet.builder();
      for (Node node : members) {
        if (node instanceof BoundNode) {
          b.add(((BoundNode) node).b);
        }
      }
      return b.build();
    }
  }

  static abstract class Node {
    private Clique clique;

    Node() {
      this.clique = new Clique(this);
    }

    void setClique(Clique c) {
      this.clique = Preconditions.checkNotNull(c);
    }

    Clique clique() { return this.clique; }
  }

  static final class VarNode extends Node {
    final InferenceVariable v;

    VarNode(InferenceVariable v) {
      this.v = v;
    }

    @Override public String toString() { return "(VarNode " + v + ")"; }
  }

  static final class BoundNode extends Node {
    final Bound b;

    BoundNode(Bound b) {
      this.b = b;
    }

    @Override public String toString() { return "(BoundNode " + b + ")"; }
  }

  @SuppressWarnings("synthetic-access")
  static Builder builder(BoundSet bs) {
    return new Builder(bs);
  }

  static final class Builder {
    final BoundSet bounds;
    final ImmutableSet<InferenceVariable> unionOfCaptureRelationAlphas;
    private final List<Object> deps = new ArrayList<>();

    private Builder(BoundSet bounds) {
      this.bounds = bounds;
      ImmutableSet.Builder<InferenceVariable> b = ImmutableSet.builder();
      for (Bound bound : bounds.bounds) {
        if (bound instanceof CaptureRelation) {
          b.addAll(((CaptureRelation) bound).alphas);
        }
      }
      this.unionOfCaptureRelationAlphas = b.build();
    }

    Builder mustResolveBeforeOrAtSameTime(
        InferenceVariable before, InferenceVariable dependency) {
      deps.add(before);
      deps.add(dependency);
      return this;
    }

    Builder boundDependsOn(InferenceVariable v, Bound b) {
      deps.add(v);
      deps.add(b);
      return this;
    }

    Builder boundInforms(Bound b, InferenceVariable v) {
      deps.add(b);
      deps.add(v);
      return this;
    }

    private static Node getNode(
        Map<? super InferenceVariable, Node> nodes, InferenceVariable k) {
      Node n = nodes.get(k);
      if (n == null) {
        n = new VarNode(k);
        nodes.put(k, n);
      }
      return n;
    }

    private static Node getNode(Map<? super Bound, Node> nodes, Bound b) {
      Node n = nodes.get(b);
      if (n == null) {
        n = new BoundNode(b);
        nodes.put(b, n);
      }
      return n;
    }

    private static void mergeCliques(
        Node node, List<Node> onPath,
        ImmutableMultimap<Node, Node> followerMap) {
      int index = onPath.indexOf(node);
      if (index >= 0) {
        System.err.println(
            "Merging cliques for " + onPath.subList(index, onPath.size()));
        Clique c = node.clique();
        for (int i = index + 1, n = onPath.size(); i < n; ++i) {
          onPath.get(i).clique().mergeInto(c);
        }
        return;
      } else {
        index = onPath.size();
        onPath.add(node);
        for (Node follower : followerMap.get(node)) {
          mergeCliques(follower, onPath, followerMap);
        }
        Node removed = onPath.remove(index);
        Preconditions.checkState(removed == node);
      }
    }

    ResolutionOrder build() {
      // Build a follower table.
      Map<Object, Node> nodes = new LinkedHashMap<>();
      Multimap<Node, Node> followerMap = LinkedHashMultimap.create();
      for (int i = 0, n = deps.size(); i < n; i += 2) {
        Object before = deps.get(i);
        Object after = deps.get(i + 1);
        Node beforeNode, afterNode;
        beforeNode = before instanceof Bound
            ? getNode(nodes, (Bound) before)
            : getNode(nodes, (InferenceVariable) before);
        afterNode = after instanceof Bound
            ? getNode(nodes, (Bound) after)
            : getNode(nodes, (InferenceVariable) after);
        followerMap.put(beforeNode, afterNode);
      }
      for (Bound b : this.bounds.bounds) {
        getNode(nodes, b);
      }
      for (InferenceVariable v : this.bounds.thrown) {
        getNode(nodes, v);
      }
      ImmutableMultimap<Node, Node> iFollowerMap =
          ImmutableMultimap.copyOf(followerMap);
      System.err.println("iFollowerMap=" + iFollowerMap);

      // Using the follower table, walk from each node,
      // and when a cycle is detected, merge cliques.
      List<Node> onPath = new ArrayList<>();
      for (Node node : nodes.values()) {
        onPath.clear();
        mergeCliques(node, onPath, iFollowerMap);
      }

      // Now that we've merged cliques, come up with a preceder table for
      // cliques.
      Set<Clique> cliques = new LinkedHashSet<>();
      for (Node n : nodes.values()) {
        cliques.add(n.clique());
      }
      System.err.println("cliques=" + cliques);
      Multimap<Clique, Clique> cliquePrecederTable =
          LinkedHashMultimap.create();
      for (Node before : nodes.values()) {
        for (Node after : iFollowerMap.get(before)) {
          if (before.clique() != after.clique()) {
            // Not transitively co-dependent since we merged cliques above.
            cliquePrecederTable.put(after.clique(), before.clique());
          }
        }
      }
      // Use Topological sort to order cliques.
      @SuppressWarnings("synthetic-access")
      ResolutionOrder order = new ResolutionOrder(
          new TopoSort<>(cliquePrecederTable).sort(cliques));
      return order;
    }
  }
}

final class TopoSort<T> {
  private Multimap<? super T, ? extends T> backEdges;

  /** @param backEdges acyclic */
  TopoSort(Multimap<? super T, ? extends T> backEdges) {
    this.backEdges = backEdges;
  }

  ImmutableList<T> sort(Iterable<? extends T> elements) {
    ImmutableList.Builder<T> out = ImmutableList.builder();
    Set<T> ordered = new LinkedHashSet<>();
    for (T element : elements) {
      sortOneOnto(element, ordered, out);
    }
    return out.build();
  }

  private void sortOneOnto(
      T element, Set<T> ordered, ImmutableList.Builder<T> out) {
    if (ordered.add(element)) {
      for (T before : backEdges.get(element)) {
        sortOneOnto(before, ordered, out);
      }
      out.add(element);
    }
  }
}
