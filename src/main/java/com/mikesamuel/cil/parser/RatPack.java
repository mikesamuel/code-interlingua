package com.mikesamuel.cil.parser;

import com.google.common.base.Preconditions;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.mikesamuel.cil.ast.MatchEvent;
import com.mikesamuel.cil.ast.NodeType;

/**
 * Memoizes partial parses by mapping (NodeType, indexIntoContent) to
 * a ParseState with that index whose output ends with a {@link MatchEvent#pop}
 * of a {@link MatchEvent#push} of that NodeType, so that the events from
 * parsing that production at that index can be reused.
 */
public final class RatPack {

  private final Cache<RatDropping, ParseCacheEntry> parseCache =
      CacheBuilder.newBuilder()
      .maximumSize(128)
      .build();

  /**
   * Cache the fact that a parse failed at the given index.
   */
  public void cacheFailure(int index, NodeType nodeType, Kind kind) {
    RatDropping d = new RatDropping(nodeType, index, kind);
    parseCache.put(d, ParseFailure.INSTANCE);
  }

  /**
   * @param output An output event chain after parsing a production that starts
   *     at index.
   */
  public void cache(int indexBeforeParse, int indexAfterParse, Kind kind,
                    Chain<MatchEvent> output) {
    Preconditions.checkArgument(
        output !=  null && output.x instanceof MatchEvent.Pop);

    NodeType nodeType = null;
    int popCount = 0;
    for (Chain<? extends MatchEvent> o = output; o != null; o = o.prev) {
      MatchEvent e = o.x;
      if (e instanceof MatchEvent.Pop) {
        ++popCount;
      } else if (e instanceof MatchEvent.Push) {
        // popCount should not go negative (module underflow) because of the
        // argument check above.
        --popCount;
        if (popCount == 0 && kind == Kind.WHOLE) {
          nodeType = ((MatchEvent.Push) e).variant.getNodeType();
          break;
        }
      } else if (e instanceof MatchEvent.LRStart
                 && popCount == 0 && kind == Kind.SEED) {
        nodeType = ((MatchEvent.LRStart) e).nodeType;
        break;
      }
    }
    Preconditions.checkNotNull(nodeType);

    RatDropping d = new RatDropping(nodeType, indexBeforeParse, kind);
    parseCache.put(d, new ParseSuccess(
        nodeType, indexAfterParse, kind, output));
  }

  /**
   * Appends events to beforeParse based on a cached parse of the given
   * production at the given index.
   *
   * @return absent if nothing in the cache.
   */
  public ParseCacheEntry getCachedParse(
      NodeType nodeType, int index, Kind kind) {
    RatDropping d = new RatDropping(nodeType, index, kind);
    ParseCacheEntry e = parseCache.getIfPresent(d);
    if (e == null) { e = ParseUncached.INSTANCE; }
    return e;
  }

  /** The type of parse that is being cached. */
  public enum Kind {
    /** A parse of the whole production. */
    WHOLE,
    /** A parse of just the seed. */
    SEED,
    ;
  }


  private static final class RatDropping {
    final NodeType nodeType;
    final int index;
    final Kind kind;

    RatDropping(NodeType nodeType, int index, Kind kind) {
      this.nodeType = nodeType;
      this.index = index;
      this.kind = kind;
    }

    @Override
    public int hashCode() {
      final int prime = 31;
      int result = 1;
      result = prime * result + index;
      result = prime * result + nodeType.hashCode();
      result = prime * result + kind.hashCode();
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
      if (kind != other.kind) {
        return false;
      }
      return true;
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
  }

  static final class ParseSuccess implements ParseCacheEntry {
    final NodeType nodeType;
    final int indexAfterParse;
    final Kind kind;
    final Chain<MatchEvent> output;

    ParseSuccess(
        NodeType nodeType, int indexAfterParse, Kind kind,
        Chain<MatchEvent> output) {
      this.nodeType = nodeType;
      this.indexAfterParse = indexAfterParse;
      this.kind = kind;
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
      Chain<MatchEvent> resultReverse = null;
      // Consume inputs as we see tokens and content events.
      {
        int popCount = 0;
        for (Chain<? extends MatchEvent> o = output; o != null; o = o.prev) {
          MatchEvent e = o.x;
          resultReverse = Chain.append(resultReverse, e);
          if (e instanceof MatchEvent.Pop) {
            ++popCount;
          } else if (e instanceof MatchEvent.Push) {
            // popCount should not go negative (module underflow) because of the
            // argument check above.
            --popCount;
            if (popCount == 0 && kind == Kind.WHOLE) {
              Preconditions.checkState(
                  nodeType
                  == ((MatchEvent.Push) e).variant.getNodeType());
              break;
            }
          } else if (popCount == 0
                     && e instanceof MatchEvent.LRStart && kind == Kind.SEED) {
            Preconditions.checkState(
                nodeType == ((MatchEvent.LRStart) e).nodeType);
            break;
          }
        }
      }

      Chain<MatchEvent> beforeParse = state.output;
      Chain<MatchEvent> afterParse = beforeParse;
      for (Chain<? extends MatchEvent> r = resultReverse; r != null; r = r.prev) {
        afterParse = Chain.append(afterParse, r.x);
      }
      return state.withOutput(afterParse).withIndex(indexAfterParse);
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
  }

}
