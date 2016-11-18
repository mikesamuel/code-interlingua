package com.mikesamuel.cil.ast;

import java.util.Iterator;

import javax.annotation.Nullable;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.mikesamuel.cil.parser.Chain;
import com.mikesamuel.cil.parser.LineStarts;
import com.mikesamuel.cil.parser.ParSer;
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

    Tier root = buildTier(null, starts, it);

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
      @Nullable NodeVariant variant,
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
        MatchEvent.Push push = (MatchEvent.Push) e;
        Tier nodeContent = buildTier(push.variant, starts, events);
        if (!nodeContent.sawPop) {
          Preconditions.checkState(!events.hasNext());
          throw new IllegalArgumentException("No pop corresponding to " + push);
        }
        if (nodes == null) {
          nodes = ImmutableList.builder();
        }
        if (push.variant.isAnon()) {
          Preconditions.checkState(nodeContent.nodes.size() == 1);
          nodes.addAll(nodeContent.nodes);
        } else {
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
        }
        if (tier.startPosition == null) {
          tier.startPosition = nodeContent.startPosition;
        }
        if (nodeContent.lastPosition != null) {
          tier.lastPosition = nodeContent.lastPosition;
        }
      } else {
        SourcePosition pos;
        if (e instanceof MatchEvent.Content) {
          MatchEvent.Content c = (MatchEvent.Content) e;
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
          MatchEvent.Token t = (MatchEvent.Token) e;
          pos = new SourcePosition(
              starts, t.index, t.index + t.nCharsConsumed());
        } else if (e instanceof MatchEvent.Ignorable) {
          MatchEvent.Ignorable ign = (MatchEvent.Ignorable) e;
          pos = new SourcePosition(
              starts, ign.index, ign.index + ign.ignorableContent.length());
          if (variant == JavaDocCommentNode.Variant.Builtin
              && ign.ignorableContent.startsWith("/**")) {
            // Treat the comment as content.
            tier.content = ign.ignorableContent;
            tier.contentPosition = pos;
          }
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


  /**
   * A series of events that describes the tree structure, but which is missing
   * some of the information necessary to serialize the tree.
   *
   * @return A series of MatchEvent which contains all necessary events except
   *     for {@link MatchEvent#token} and
   *     {@link MatchEvent#push}/{@link MatchEvent#pop} for
   *     {@link NodeVariant#isAnon @anon} variants.
   *
   * @see ParSer#unparse
   */
  public static Chain<MatchEvent> startUnparse(
      Chain<MatchEvent> beforeNode, BaseNode node) {

    String value = node.getValue();
    ImmutableList<? extends BaseNode> children = node.getChildren();


    SourcePosition pos = node.getSourcePosition();
    Chain<MatchEvent> beforeContent = maybeAppendPos(
        beforeNode, pos != null ? pos.start() : null);

    NodeVariant variant = node.getVariant();
    beforeContent = Chain.append(
        beforeContent, MatchEvent.push(node.getVariant()));

    Chain<MatchEvent> afterContent;
    if (value != null) {
      Preconditions.checkState(children.isEmpty());
      int startIndex = node.getSourcePosition().startCharInFile();
      afterContent = Chain.append(
          beforeContent,
          variant != JavaDocCommentNode.Variant.Builtin
          ? MatchEvent.content(value, startIndex)
          : MatchEvent.ignorable(value, startIndex));
    } else {
      afterContent = beforeContent;
      for (BaseNode child : children) {
        Chain<MatchEvent> afterChild = startUnparse(afterContent, child);
        afterContent = afterChild;
      }
    }

    Chain<MatchEvent> afterNode = Chain.append(afterContent, MatchEvent.pop());
    if (pos != null) {
      afterNode = maybeAppendPos(afterNode, pos.end());
    }

    return afterNode;
  }

  private static Chain<MatchEvent> maybeAppendPos(
      Chain<MatchEvent> beforePos, SourcePosition pos) {
    if (pos != null) {
      SourcePosition last = null;
      for (Chain<MatchEvent> c = beforePos; c != null; c = c.prev) {
        MatchEvent e = c.x;
        if (e.nCharsConsumed() != 0) {
          break;
        } else if (e instanceof MatchEvent.SourcePositionMark) {
          last = ((MatchEvent.SourcePositionMark) e).pos;
          break;
        }
      }
      if (last == null || !pos.equals(last)) {
        return Chain.append(
            beforePos, MatchEvent.positionMark(pos));
      }
    }
    return beforePos;
  }
}
