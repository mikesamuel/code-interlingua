package com.mikesamuel.cil.parser;

import java.io.PrintStream;
import java.util.Map;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.mikesamuel.cil.ast.NodeType;
// TODO: Move the checks into Grammar
import com.mikesamuel.cil.ast.j8.J8NodeType;
import com.mikesamuel.cil.event.Event;

/**
 * Memoizes partial parses by mapping (NodeType, indexIntoContent) to
 * a ParseState with that index whose output ends with a {@link Event#pop}
 * of a {@link Event#push} of that NodeType, so that the events from
 * parsing that production at that index can be reused.
 */
public final class RatPack {

  private final Map<RatDropping, ParseCacheEntry> parseCache =
      Maps.newHashMap();


  /**
   * Cache the fact that a parse failed at the given index.
   */
  public void cacheFailure(int index, NodeType<?, ?> nodeType) {
    RatDropping d = new RatDropping(nodeType, index);
    parseCache.put(d, ParseFailure.INSTANCE);
  }

  /**
   * @param output An output event list after parsing a production that starts
   *     at index.
   */
  public void cacheSuccess(
      int indexBeforeParse, int indexAfterParse,
      NodeType<?, ?> nodeType, SList<Event> output) {
    Preconditions.checkArgument(
        output !=  null && output.x.getKind() == Event.Kind.POP);

    int popCount = 0;
    cache_loop:
    for (SList<? extends Event> o = output; o != null; o = o.prev) {
      Event e = o.x;
      switch (e.getKind()) {
        case POP:
          ++popCount;
          break;
        case PUSH:
          // popCount should not go negative (module underflow) because of the
          // argument check above.
          --popCount;
          if (popCount == 0) {
            NodeType<?, ?> eNodeType = e.getNodeType();
            Preconditions.checkState(
                // A template element can substitute for any other node type.
                eNodeType == nodeType
                || eNodeType == J8NodeType.TemplateInterpolation
                || eNodeType == J8NodeType.TemplateDirectives);
            break cache_loop;
          }
          break;
        case CONTENT:
        case DELAYED_CHECK:
        case IGNORABLE:
        case LR_END:
        case LR_START:
        case POSITION_MARK:
        case TOKEN:
          break;
      }
    }
    Preconditions.checkState(popCount == 0);

    RatDropping d = new RatDropping(nodeType, indexBeforeParse);
    parseCache.put(d, new ParseSuccess(nodeType, indexAfterParse, output));
  }

  /**
   * Appends events to beforeParse based on a cached parse of the given
   * production at the given index.
   *
   * @return absent if nothing in the cache.
   */
  public ParseCacheEntry getCachedParse(NodeType<?, ?> nodeType, int index) {
    RatDropping d = new RatDropping(nodeType, index);
    ParseCacheEntry e = parseCache.get(d);
    if (e == null) { e = ParseUncached.INSTANCE; }
    return e;
  }


  private static final class RatDropping {
    final NodeType<?, ?> nodeType;
    final int index;

    RatDropping(NodeType<?, ?> nodeType, int index) {
      this.nodeType = nodeType;
      this.index = index;
    }

    @Override
    public int hashCode() {
      final int prime = 31;
      int result = 1;
      result = prime * result + index;
      result = prime * result + nodeType.hashCode();
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
      RatDropping other = (RatDropping) obj;
      if (index != other.index) {
        return false;
      }
      if (nodeType != other.nodeType) {
        return false;
      }
      return true;
    }
  }


  /**
   * Dumps cache content for debugging.
   */
  @VisibleForTesting
  public void dump(PrintStream out) {
    out.println("RAT PACK");
    for (Map.Entry<RatDropping, ParseCacheEntry> e
        : this.parseCache.entrySet()) {
      RatDropping k = e.getKey();
      ParseCacheEntry v = e.getValue();
      out.println(
          ". " + k.nodeType + " @ " + k.index + "  =>  " + v.toString());
    }
  }



  /**
   * A result from the parse cache.
   */
  public interface ParseCacheEntry {
    /**
     * True if the specified parse has been attempted and the result is still
     * available via {@link #apply}.
     */
    boolean wasTried();
    /**
     * True if the parse was tried and the result is available.
     */
    boolean passed();
    /**
     * Appends the events produced by the previously tried, passing parse
     * and updates the parse position.
     * @throws UnsupportedOperationException if not {@link #passed}.
     */
    ParseState apply(ParseState state) throws UnsupportedOperationException;
  }

  /**
   * An entry in the parse cache indicating the parse failed.
   */
  static final class ParseFailure implements ParseCacheEntry {

    static final ParseFailure INSTANCE = new ParseFailure();

    private ParseFailure() {
      // singleton
    }

    @Override
    public boolean wasTried() {
      return true;
    }

    @Override
    public boolean passed() {
      return false;
    }

    @Override
    public ParseState apply(ParseState state)
    throws UnsupportedOperationException{
      throw new UnsupportedOperationException();
    }

    @Override
    public String toString() {
      return "ParseFailure";
    }
  }

  static final class ParseSuccess implements ParseCacheEntry {
    final NodeType<?, ?> nodeType;
    final int indexAfterParse;
    final SList<Event> output;

    ParseSuccess(
        NodeType<?, ?> nodeType, int indexAfterParse, SList<Event> output) {
      this.nodeType = nodeType;
      this.indexAfterParse = indexAfterParse;
      this.output = output;
    }

    @Override
    public boolean wasTried() {
      return true;
    }

    @Override
    public boolean passed() {
      return true;
    }

    @Override
    public ParseState apply(ParseState state)
    throws UnsupportedOperationException{
      // Snip off the part of the parse that relates to the last event
      SList<Event> resultReverse = null;
      // Consume inputs as we see tokens and content events.
      {
        int popCount = 0;
        apply_loop:
        for (SList<? extends Event> o = output; o != null; o = o.prev) {
          Event e = o.x;
          resultReverse = SList.append(resultReverse, e);
          switch (e.getKind()) {
            case POP:
              ++popCount;
              break;
            case PUSH:
              // popCount should not go negative (module underflow) because of
              // the argument check above.
              --popCount;
              if (popCount == 0) {
                NodeType<?, ?> ent = e.getNodeType();
                Preconditions.checkState(
                    nodeType == ent
                    || ent == J8NodeType.TemplateInterpolation,
                    "%s != %s", nodeType, ent);
                break apply_loop;
              }
              break;
            case CONTENT:
            case DELAYED_CHECK:
            case IGNORABLE:
            case LR_END:
            case LR_START:
            case POSITION_MARK:
            case TOKEN:
              break;
          }
        }
      }

      SList<Event> beforeParse = state.output;
      SList<Event> afterParse = beforeParse;
      for (SList<? extends Event> r = resultReverse; r != null; r = r.prev) {
        afterParse = SList.append(afterParse, r.x);
      }
      return state.withOutput(afterParse).withIndex(indexAfterParse);
    }

    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder(".");
      while (sb.length() < this.indexAfterParse) {
        sb.append(sb);
      }
      ParseState state = apply(
          new ParseState(Input.builder().source("empty").code(sb).build()));

      return "ParseSuccess("
          + ImmutableList.copyOf(SList.forwardIterable(state.output))
          + ", index<-" + state.index + ")";
    }
  }

  static final class ParseUncached implements ParseCacheEntry {
    static final ParseUncached INSTANCE = new ParseUncached();

    private ParseUncached() {
      // singleton
    }

    @Override
    public boolean wasTried() {
      return false;
    }

    @Override
    public boolean passed() {
      return false;
    }

    @Override
    public ParseState apply(ParseState state)
    throws UnsupportedOperationException{
      throw new UnsupportedOperationException();
    }

    @Override
    public String toString() {
      return "ParseUncached";
    }
  }
}
