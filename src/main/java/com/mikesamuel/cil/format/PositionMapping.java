package com.mikesamuel.cil.format;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.mikesamuel.cil.parser.SourcePosition;

/**
 * Relates characters in source files to characters in a file derived from them.
 *
 * @see <a href="http://www.ibm.com/support/knowledgecenter/SS3JHP_6.1.0/com.ibm.install.raav6006.doc/topics/com12.html">Source Line Mapping File</a>
 */
public final class PositionMapping {
  /**
   * The entries in the mapping.
   * Each entry's derived file range should be
   * disjoint with each other entry's.
   */
  public final ImmutableList<Entry> entries;

  /** */
  public PositionMapping(Iterable<? extends Entry> entries) {
    this.entries = ImmutableList.copyOf(entries);
  }

  /**
   * A relationship between a contiguous sequence of characters in an input
   * and a contiguous sequence of characters in an output derived from it.
   */
  public static final class Entry {
    /** A range of characters in the source file. */
    public final SourcePosition positionInOriginal;
    /**
     * Inclusive character index into the derived output of the start of the
     * range derived from positionInOriginal.
     */
    public final int startIndexInDerived;
    /**
     * Exclusive character index into the derived output of the end of the
     * range derived from positionInOriginal.
     */
    public final int endIndexInDerived;

    /** */
    public Entry(
        SourcePosition pos, int startIndexInDerived, int endIndexInDerived) {
      Preconditions.checkPositionIndexes(
          startIndexInDerived, endIndexInDerived, Integer.MAX_VALUE);
      this.positionInOriginal = Preconditions.checkNotNull(pos);
      this.startIndexInDerived = startIndexInDerived;
      this.endIndexInDerived = endIndexInDerived;

    }
  }
}
