package com.mikesamuel.cil.parser;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.mikesamuel.cil.ast.MatchEvent;

/**
 * The state of an unparsing operation.
 */
public final class SerialState {
  /** The events to serialize. */
  public final ImmutableList<MatchEvent> events;
  /** into events. */
  public final int index;
  /** Partial string being composed. */
  public final Chain<String> outputChain;

  /** */
  public SerialState(
      ImmutableList<MatchEvent> events, int index, Chain<String> outputChain) {
    Preconditions.checkArgument(0 <= index && index <= events.size());
    this.events = events;
    this.index = index;
    this.outputChain = outputChain;
  }

  /** A state with the given output appended. */
  public SerialState append(String text) {
    if (text.length() == 0) { return this; }
    return new SerialState(
        events, index,
        Chain.append(outputChain, text));
  }

  /**
   * If the given event is at index, returns the state with the index advanced
   * past it, otherwise returns absent.
   *
   * @param err receives an error message when absent is returned.
   */
  public Optional<SerialState> expectEvent(
      MatchEvent wanted, SerialErrorReceiver err) {
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
   * True iff there are no events past {@link #index}.
   */
  public boolean isEmpty() {
    return index == this.events.size();
  }

  /**
   * A state with the index incremented past the current event.
   */
  public SerialState advance() {
    return new SerialState(events, index + 1, outputChain);
  }

}
