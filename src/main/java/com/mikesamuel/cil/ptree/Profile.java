package com.mikesamuel.cil.ptree;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.mikesamuel.cil.ast.NodeType;

@SuppressWarnings("javadoc")
@VisibleForTesting
public final class Profile implements AutoCloseable {
  private Map<NodeType<?, ?>, Integer> profileCount = new LinkedHashMap<>();
  private static final ThreadLocal<Profile> local = new ThreadLocal<>();

  public static Profile startCounting() {
    Profile d = local.get();
    if (d == null) {
      local.set(d = new Profile());
    }
    return d;
  }

  @Override
  public void close() {
    if (local.get() != this) {
      throw new IllegalStateException();
    }
    local.set(null);
    List<Map.Entry<NodeType<?, ?>, Integer>> ls =
        Lists.newArrayList(profileCount.entrySet());
    Collections.sort(ls, new Comparator<Map.Entry<NodeType<?, ?>, Integer>>() {
      @Override
      public int compare(
          Map.Entry<NodeType<?, ?>, Integer> a,
          Map.Entry<NodeType<?, ?>, Integer> b) {
        int delta = a.getValue() - b.getValue();
        if (delta == 0) {
          delta = a.getKey().ordinal() - b.getKey().ordinal();
        }
        return delta;
      }
    });
    for (Map.Entry<NodeType<?, ?>, Integer> e : ls) {
      NodeType<?, ?> t = e.getKey();
      int count = e.getValue();
      if (count != 0) {
        System.err.println(t + "\t" + count);
      }
    }
  }

  static void count(NodeType<?, ?> nodeType) {
    @SuppressWarnings("resource")
    Profile p = local.get();
    if (p != null) {
      Integer oldCount = p.profileCount.get(nodeType);
      int newCount = (oldCount != null ? oldCount : 0) + 1;
      p.profileCount.put(nodeType, newCount);
    }
  }
}