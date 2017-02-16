package com.mikesamuel.cil.ast;

import java.util.Iterator;
import java.util.List;

import javax.annotation.Nullable;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.mikesamuel.cil.event.Event;
import com.mikesamuel.cil.parser.SList;
import com.mikesamuel.cil.parser.Ignorables;
import com.mikesamuel.cil.parser.Input;
import com.mikesamuel.cil.parser.ParSer;
import com.mikesamuel.cil.parser.ParseState;
import com.mikesamuel.cil.parser.SourcePosition;

/**
 * Utilities for building ASTs.
 */
public final class Trees {
  private Trees() {
    // Not instantiable
  }

  /**
   * @see #of(Input, Iterable)
   */
  public static BaseNode of(Input input, SList<Event> events) {
    return of(input, SList.forwardIterable(events));
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
      Input input, Iterable<? extends Event> events) {
    Iterator<? extends Event> it = events.iterator();

    Tier root = buildTier(null, input, it);

    if (root.content != null) {
      throw new IllegalArgumentException(
          "Content outside root: `" + root.content + "`");
    }
    Preconditions.checkState(!it.hasNext());
    if (root.sawPop) {
      throw new IllegalArgumentException("Saw orphaned pop");
    }

    return coalesce(root, input);
  }

  private static BaseNode coalesce(Tier tier, Input inp) {
    int nRoots = tier.nodes.size();
    if (nRoots == 1) {
      return tier.nodes.get(0);
    }

    int compilationUnitCount = 0;
    for (BaseNode node : tier.nodes) {
      NodeType nt = node.getNodeType();
      if (nt == NodeType.CompilationUnit) {
        ++compilationUnitCount;
      } else if (!NodeTypeTables.NONSTANDARD.contains(nt)) {
        throw new IllegalArgumentException(
            "Expected 1 root node.  " + nt
            + " cannot be coalesced into a template pseudo root");
      }
    }
    Preconditions.checkState(compilationUnitCount <= 1);
    // TODO: Check balancedness of start and end template instructions.

    TemplatePseudoRootNode root = TemplatePseudoRootNode.Variant.CompilationUnit
        .buildNode(tier.nodes);
    root.setSourcePosition(inp.getSourcePosition(0, inp.content().length()));
    return root;
  }

  /** @see #of(Input, SList) */
  public static BaseNode of(ParseState state) {
    return of(state.input, state.output);
  }

  private static Tier buildTier(
      @Nullable NodeVariant variant,
      Input input, Iterator<? extends Event> events) {
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
          Tier nodeContent = buildTier(pushVariant, input, events);
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
            BaseNode nodeBuilt;
            if (nodeContent.content != null) {
              Preconditions.checkState(nodeContent.nodes.isEmpty());
              nodeBuilt = pushVariant.buildNode(nodeContent.content);
              if (!nodeContent.nodes.isEmpty()) {
                throw new IllegalArgumentException(
                    "Leaf node " + pushVariant
                    + " parsed from " + tier.startPosition
                    + " should not have children");
              }
            } else {
              nodeBuilt = pushVariant.buildNode(nodeContent.nodes);
            }
            if (nodeContent.content != null) {
              nodeBuilt.setSourcePosition(nodeContent.contentPosition);
            } else if (nodeContent.startPosition != null) {
              nodeBuilt.setSourcePosition(SourcePosition.spanning(
                  nodeContent.startPosition, nodeContent.lastPosition));
            }
            nodes.add(nodeBuilt);
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
          SourcePosition pos = makeSourcePosition(
              e.getContentIndex(), e.nCharsConsumed(), input);
          if (pos != null) {
            tier.contentPosition = pos;
            tier.updatePosition(pos);
          }
          break;
        }
        case TOKEN: {
          SourcePosition pos = makeSourcePosition(
              e.getContentIndex(), e.nCharsConsumed(), input);
          if (pos != null) {
            tier.updatePosition(pos);
          }
          break;
        }
        case IGNORABLE: {
          SourcePosition pos = makeSourcePosition(
              e.getContentIndex(), e.getContent().length(), input);
          if (variant.isIgnorable()) {
            // Treat the comment as content.
            tier.content = e.getContent();
            tier.contentPosition = pos;
          }
          if (pos != null) {
            tier.updatePosition(pos);
          }
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

  private static SourcePosition makeSourcePosition(
      int decodedStartIndex, int nDecodedChars, Input input) {
    return input.getSourcePosition(
        decodedStartIndex, decodedStartIndex + nDecodedChars);
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
  public static SList<Event> startUnparse(
      @Nullable SList<Event> beforeNode, BaseNode node,
      @Nullable Decorator decorator) {


    String value = node.getValue();
    List<? extends BaseNode> children = node.getChildren();

    SourcePosition pos = node.getSourcePosition();
    SList<Event> beforeContent = maybeAppendPos(
        beforeNode, pos != null ? pos.start() : null);

    String decoration = decorator != null ? decorator.decorate(node) : null;
    if (decoration != null) {
      beforeContent = SList.append(
          beforeContent, Event.ignorable(decoration, -1));
    }

    NodeVariant variant = node.getVariant();
    beforeContent = SList.append(
        beforeContent, Event.push(node.getVariant()));

    SList<Event> afterContent;
    if (value != null) {
      Preconditions.checkState(children.isEmpty());
      int startIndex = pos != null ? pos.startCharInFile() : -1;
      afterContent = SList.append(
          beforeContent,
          variant.isIgnorable()
          ? Event.ignorable(value, startIndex)
          : Event.content(value, startIndex));
    } else {
      afterContent = beforeContent;
      for (BaseNode child : children) {
        SList<Event> afterChild = startUnparse(afterContent, child, decorator);
        afterContent = afterChild;
      }
    }

    SList<Event> afterNode = SList.append(afterContent, Event.pop());
    if (pos != null) {
      afterNode = maybeAppendPos(afterNode, pos.end());
    }

    return afterNode;
  }

  private static SList<Event> maybeAppendPos(
      SList<Event> beforePos, SourcePosition pos) {
    if (pos != null) {
      SourcePosition last = null;
      for (SList<Event> c = beforePos; c != null; c = c.prev) {
        Event e = c.x;
        if (e.nCharsConsumed() != 0) {
          break;
        } else if (e.getKind() == Event.Kind.POSITION_MARK) {
          last = e.getSourcePosition();
          break;
        }
      }
      if (last == null || !pos.equals(last)) {
        return SList.append(
            beforePos, Event.positionMark(pos));
      }
    }
    return beforePos;
  }


  /**
   * Can be used to add comments to the output event stream.
   */
  public interface Decorator {
    /**
     * A comment to place before node if any.
     *
     * @return null for no decoration or a well-formed comment token
     *   as defined by {@link Ignorables}.
     * @see Java8Comments
     */
    public @Nullable String decorate(BaseNode node);
  }
}
