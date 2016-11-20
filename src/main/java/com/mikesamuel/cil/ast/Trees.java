package com.mikesamuel.cil.ast;

import java.util.Iterator;

import javax.annotation.Nullable;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.mikesamuel.cil.event.Event;
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
   *     {@linkplain Event#push push} event.
   *     There must be a 1:1 correspondence between pushes and
   *     {@linkplain Event#pop pop}s.
   *     The pop that corresponds to a push must be after it in the sequence.
   *     Every push/pop pair must be entirely within a particular other push/pop
   *     pair, before it, or after it.
   *     There must be no push/pop/{@linkplain Event#content content}
   *     events before the first push or after
   *     its corresponding pop.
   *     There must be no LR
   *     {@linkplain Event#leftRecursionSuffixStart start} or
   *     {@linkplain Event#leftRecursionSuffixEnd end} events in events.
   *     {@linkplain Event#token token} events are ignored except when
   *     computing source positions and may appear anywhere.
   *
   */
  public static BaseNode of(
      LineStarts starts,
      Iterable<? extends Event> events) {
    Iterator<? extends Event> it = events.iterator();

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
      Iterator<? extends Event> events) {
    @SuppressWarnings("synthetic-access")
    Tier tier = new Tier();

    ImmutableList.Builder<BaseNode> nodes = null;
    event_loop:
    while (events.hasNext()) {
      Event e = events.next();
      switch (e.getKind()) {
        case POP:
          tier.sawPop = true;
          break event_loop;
        case PUSH:
          NodeVariant pushVariant = e.getNodeVariant();
          Tier nodeContent = buildTier(pushVariant, starts, events);
          if (!nodeContent.sawPop) {
            Preconditions.checkState(!events.hasNext());
            throw new IllegalArgumentException("No pop corresponding to " + e);
          }
          if (nodes == null) {
            nodes = ImmutableList.builder();
          }
          if (pushVariant.isAnon()) {
            Preconditions.checkState(nodeContent.nodes.size() == 1);
            nodes.addAll(nodeContent.nodes);
          } else {
            BaseNode.Builder<?, ?> nodeBuilder = pushVariant.nodeBuilder();
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
          break;
        case CONTENT: {
          if (tier.content != null) {
            throw new IllegalArgumentException(
                "Duplicate content `" + tier.content
                + "` and `" + e.getContent() + "`");
          }
          tier.content = e.getContent();
          SourcePosition pos = new SourcePosition(
              starts, e.getContentIndex(),
              e.getContentIndex() + e.nCharsConsumed());
          tier.contentPosition = pos;
          tier.updatePosition(pos);
          break;
        }
        case TOKEN: {
          SourcePosition pos = new SourcePosition(
              starts, e.getContentIndex(),
              e.getContentIndex() + e.nCharsConsumed());
          tier.updatePosition(pos);
          break;
        }
        case IGNORABLE: {
          SourcePosition pos = new SourcePosition(
              starts, e.getContentIndex(),
              e.getContentIndex() + e.getContent().length());
          if (variant.isIgnorable()) {
            // Treat the comment as content.
            tier.content = e.getContent();
            tier.contentPosition = pos;
          }
          tier.updatePosition(pos);
          break;
        }
        case DELAYED_CHECK:
        case LR_END:
        case LR_START:
        case POSITION_MARK:
          throw new IllegalArgumentException("Unexpected event " + e);
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


    void updatePosition(SourcePosition pos) {
      if (startPosition == null) {
        startPosition = pos.start();
      }
      lastPosition = pos.end();
    }
  }


  /**
   * A series of events that describes the tree structure, but which is missing
   * some of the information necessary to serialize the tree.
   *
   * @return A series of events which contains all necessary events except
   *     for {@link Event#token} and
   *     {@link Event#push}/{@link Event#pop} for
   *     {@link NodeVariant#isAnon @anon} variants.
   *
   * @see ParSer#unparse
   */
  public static Chain<Event> startUnparse(
      Chain<Event> beforeNode, BaseNode node) {

    String value = node.getValue();
    ImmutableList<? extends BaseNode> children = node.getChildren();


    SourcePosition pos = node.getSourcePosition();
    Chain<Event> beforeContent = maybeAppendPos(
        beforeNode, pos != null ? pos.start() : null);

    NodeVariant variant = node.getVariant();
    beforeContent = Chain.append(
        beforeContent, Event.push(node.getVariant()));

    Chain<Event> afterContent;
    if (value != null) {
      Preconditions.checkState(children.isEmpty());
      int startIndex = node.getSourcePosition().startCharInFile();
      afterContent = Chain.append(
          beforeContent,
          variant.isIgnorable()
          ? Event.ignorable(value, startIndex)
          : Event.content(value, startIndex));
    } else {
      afterContent = beforeContent;
      for (BaseNode child : children) {
        Chain<Event> afterChild = startUnparse(afterContent, child);
        afterContent = afterChild;
      }
    }

    Chain<Event> afterNode = Chain.append(afterContent, Event.pop());
    if (pos != null) {
      afterNode = maybeAppendPos(afterNode, pos.end());
    }

    return afterNode;
  }

  private static Chain<Event> maybeAppendPos(
      Chain<Event> beforePos, SourcePosition pos) {
    if (pos != null) {
      SourcePosition last = null;
      for (Chain<Event> c = beforePos; c != null; c = c.prev) {
        Event e = c.x;
        if (e.nCharsConsumed() != 0) {
          break;
        } else if (e.getKind() == Event.Kind.POSITION_MARK) {
          last = e.getSourcePosition();
          break;
        }
      }
      if (last == null || !pos.equals(last)) {
        return Chain.append(
            beforePos, Event.positionMark(pos));
      }
    }
    return beforePos;
  }
}
