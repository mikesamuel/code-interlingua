package com.mikesamuel.cil.ast;

import java.util.Iterator;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.mikesamuel.cil.ast.MatchEvent.Content;
import com.mikesamuel.cil.ast.MatchEvent.Push;
import com.mikesamuel.cil.ast.MatchEvent.Token;
import com.mikesamuel.cil.parser.LineStarts;
import com.mikesamuel.cil.parser.SourcePosition;

/**
 * Utilities for building ASTs.
 */
public final class Trees {
  private Trees() {
    // Not instantiable
  }

  /**
   * @param events a sequence of events with at least one
   *     {@linkplain MatchEvent.Push push} event.
   *     There must be a 1:1 correspondence between pushes and
   *     {@linkplain MatchEvent.Pop pop}s.
   *     The pop that corresponds to a push must be after it in the sequence.
   *     Every push/pop pair must be entirely within a particular other push/pop
   *     pair, before it, or after it.
   *     There must be no push/pop/{@linkplain MatchEvent.Content content}
   *     events before the first push or after
   *     its corresponding pop.
   *     There must be no LR {@linkplain MatchEvent.LRStart start} or
   *     {@linkplain MatchEvent.LREnd end} events on the sequence.
   *     {@linkplain MatchEvent.Token token} events are ignored except when
   *     computing source positions and may appear anywhere.
   *
   */
  public static BaseNode of(
      LineStarts starts,
      Iterable<? extends MatchEvent> events) {
    Iterator<? extends MatchEvent> it = events.iterator();

    Tier root = buildTier(starts, it);

    if (root.content != null) {
      throw new IllegalArgumentException(
          "Content outside root: `" + root.content + "`");
    }

    int nRoots = root.nodes.size();
    if (nRoots != 1) {
      throw new IllegalArgumentException(
          "Expected unique root node but found " + nRoots);
    }

    if (root.sawPop) {
      throw new IllegalArgumentException("Saw orphaned pop");
    }
    Preconditions.checkState(!it.hasNext());

    return root.nodes.get(0);
  }

  private static Tier buildTier(
      LineStarts starts,
      Iterator<? extends MatchEvent> events) {
    @SuppressWarnings("synthetic-access")
    Tier tier = new Tier();

    ImmutableList.Builder<BaseNode> nodes = null;
    while (events.hasNext()) {
      MatchEvent e = events.next();
      if (e instanceof MatchEvent.Pop) {
        tier.sawPop = true;
        break;
      } else if (e instanceof MatchEvent.Push) {
        MatchEvent.Push push = (Push) e;
        Tier nodeContent = buildTier(starts, events);
        if (!nodeContent.sawPop) {
          Preconditions.checkState(!events.hasNext());
          throw new IllegalArgumentException("No pop corresponding to " + push);
        }
        if (nodes == null) {
          nodes = ImmutableList.builder();
        }
        BaseNode.Builder<?, ?> nodeBuilder = push.variant.nodeBuilder();
        if (nodeContent.content != null) {
          Preconditions.checkState(nodeContent.nodes.isEmpty());
          nodeBuilder.leaf(nodeContent.content);
        } else {
          for (BaseNode child : nodeContent.nodes) {
            nodeBuilder.add(child);
          }
        }
        BaseNode node = nodeBuilder.build();
        if (nodeContent.content != null) {
          node.setSourcePosition(nodeContent.contentPosition);
        } else if (nodeContent.startPosition != null) {
          node.setSourcePosition(SourcePosition.spanning(
              nodeContent.startPosition, nodeContent.lastPosition));
        }
        nodes.add(node);
        if (tier.startPosition == null) {
          tier.startPosition = nodeContent.startPosition;
        }
        if (nodeContent.lastPosition != null) {
          tier.lastPosition = nodeContent.lastPosition;
        }
      } else {
        SourcePosition pos;
        if (e instanceof MatchEvent.Content) {
          MatchEvent.Content c = (Content) e;
          if (tier.content != null) {
            throw new IllegalArgumentException(
                "Duplicate content `" + tier.content
                + "` and `" + c.content + "`");
          }
          tier.content = c.content;
          pos = new SourcePosition(
              starts, c.index, c.index + c.nCharsConsumed());
          tier.contentPosition = pos;
        } else if (e instanceof MatchEvent.Token) {
          MatchEvent.Token t = (Token) e;
          pos = new SourcePosition(
              starts, t.index, t.index + t.nCharsConsumed());
        } else {
          throw new IllegalArgumentException("Unexpected event " + e);
        }
        if (tier.startPosition == null) {
          tier.startPosition = pos.start();
        }
        tier.lastPosition = pos.end();
      }
    }

    if (tier.content != null && nodes != null) {
      throw new IllegalArgumentException("Both children and content appear");
    }

    if (nodes != null) {
      tier.nodes = nodes.build();
    }

    return tier;
  }

  private static final class Tier {
    ImmutableList<BaseNode> nodes = ImmutableList.of();
    SourcePosition startPosition;
    SourcePosition lastPosition;
    String content = null;
    SourcePosition contentPosition;
    boolean sawPop;
  }
}
