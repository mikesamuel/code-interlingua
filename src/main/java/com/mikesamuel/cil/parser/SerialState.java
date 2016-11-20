package com.mikesamuel.cil.parser;

import javax.annotation.Nullable;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.mikesamuel.cil.event.MatchEvent;

/**
 * The state of an unparsing operation.
 */
public final class SerialState {
  /** Events that describe the structure of the parse tree to serialize. */
  public final ImmutableList<MatchEvent> structure;
  /** Cursor into {@link #structure}. */
  public final int index;
  /**
   * {@code structure.subList(0, index)} but augmented with missing tokens and
   * other events.
   */
  public final @Nullable Chain<MatchEvent> output;

  /** */
  public SerialState(Iterable<? extends MatchEvent> structure) {
    this(ImmutableList.copyOf(structure), 0, null);
  }

  /** */
  public SerialState(
      ImmutableList<MatchEvent> structure, int index,
      @Nullable Chain<MatchEvent> output) {
    Preconditions.checkArgument(0 <= index && index <= structure.size());
    this.structure = structure;

    // Silently skip over source position marks, and copy them to the output.
    // This greatly simplifies ParSer implementations that check the event at
    // the cursor and advance it by 1.
    int indexAfterCopyOver = index;
    Chain<MatchEvent> outputAfterCopyOver = output;

    int n = structure.size();
    for (MatchEvent e;
        indexAfterCopyOver < n;
         outputAfterCopyOver = Chain.append(outputAfterCopyOver, e),
         ++indexAfterCopyOver) {
      e = structure.get(indexAfterCopyOver);
      if (e.getKind() != MatchEvent.Kind.POSITION_MARK) {
        break;
      }
    }

    this.index = indexAfterCopyOver;
    this.output = outputAfterCopyOver;
  }

  /** A state with the given output appended. */
  public SerialState append(MatchEvent event) {
    return new SerialState(structure, index, Chain.append(output, event));
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
    if (index == structure.size()) {
      message = "Expected " + wanted + " but no events left";
    } else {
      MatchEvent e = structure.get(index);
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
    return index == this.structure.size();
  }

  /**
   * A state with the index incremented past the current event.
   */
  public SerialState advance() {
    return new SerialState(structure, index + 1, output);
  }

  /**
   * Like {@link #advance} but also copies the event that was skipped over to
   * the output.
   */
  public SerialState advanceWithCopy() {
    return new SerialState(
        structure, index + 1, Chain.append(output, structure.get(index)));
  }
}
