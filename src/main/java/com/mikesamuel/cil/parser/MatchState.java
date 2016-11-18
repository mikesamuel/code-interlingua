package com.mikesamuel.cil.parser;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.mikesamuel.cil.event.MatchEvent;

/**
 * A sequence of events that describe the structure of a parse tree.
 */
public final class MatchState {
  /** The event stream to match. */
  public final ImmutableList<MatchEvent> events;
  /** The cursor of the current event in {@link #events}. */
  public final int index;

  MatchState(ImmutableList<MatchEvent> events, int index) {
    Preconditions.checkArgument(0 <= index && index <= events.size());
    this.events = events;
    this.index = index;
  }

  /** True iff the cursor is past the last event. */
  public boolean isEmpty() {
    return index == events.size();
  }

  /**
   * If the given event is at index, returns the state with the index advanced
   * past it, otherwise returns absent.
   *
   * @param err receives an error message when absent is returned.
   */
  public Optional<MatchState> expectEvent(
      MatchEvent wanted, MatchErrorReceiver err) {
    String message;
    if (index == events.size()) {
      message = "Expected " + wanted + " but no events left";
    } else {
      MatchEvent e = events.get(index);
      if (e.equals(wanted)) {
        return Optional.of(advance());
      }
      message = "Expected " + wanted + " but got " + e;
    }
    err.error(this, message);
    return Optional.absent();
  }

  /**
   * A similar state but with the index incremented.
   */
  public MatchState advance() {
    return new MatchState(events, index + 1);
  }

}
