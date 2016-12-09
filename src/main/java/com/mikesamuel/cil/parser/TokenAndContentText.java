package com.mikesamuel.cil.parser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.mikesamuel.cil.event.Event;

final class TokenAndContentText implements CharSequence {
  private final ImmutableList<Event> events;
  private final String text;
  /**
   * Indicies into text of the start of the event indexed by the corresponding
   * element of eventIndices.
   * <p>
   * In other words, for all i in [0, textIndices.length), <pre>
   *   text.charAt(textIndices[i]) ==
   *   events.get(eventIndices[i]).content().charAt(0)
   * </pre>
   */
  private final int[] textIndices;
  private final int[] eventIndices;

  TokenAndContentText(ImmutableList<Event> events) {
    this.events = events;
    List<Integer> textAndEventIndices = new ArrayList<>();
    StringBuilder textBuffer = new StringBuilder();
    for (int i = 0, n = this.events.size(); i < n; ++i) {
      Event e = this.events.get(i);
      switch (e.getKind()) {
        case CONTENT:
        case IGNORABLE:
        case TOKEN:
          textAndEventIndices.add(textBuffer.length());
          textAndEventIndices.add(i);
          textBuffer.append(e.getContent());
          // Token break.
          textBuffer.append(' ');
          continue;
        case DELAYED_CHECK:
        case LR_END:
        case LR_START:
          // These shouldn't normally occur here, but left-recursion push-back
          // doesn't alter the order of content events so they're safe.
          // Re-parsing an un-checked stream might not be safe, but
          // doing so won't affect the accuracy of the text.
          continue;
        case POP:
        case POSITION_MARK:
        case PUSH:
          continue;
      }
      throw new AssertionError(e);
    }
    textAndEventIndices.add(textBuffer.length());
    textAndEventIndices.add(events.size());

    this.text = textBuffer.toString();
    int nTextTokens = textAndEventIndices.size() / 2;
    this.textIndices = new int[nTextTokens];
    this.eventIndices = new int[nTextTokens];
    for (int i = 0, k = 0; i < nTextTokens; i += 1, k += 2) {
      textIndices[i] = textAndEventIndices.get(k);
      eventIndices[i] = textAndEventIndices.get(k + 1);
    }
  }

  @Override
  public int length() {
    return text.length();
  }

  @Override
  public char charAt(int index) {
    return text.charAt(index);
  }

  @Override
  public CharSequence subSequence(int start, int end) {
    return text.substring(start, end);
  }

  @Override
  public String toString() {
    return text;
  }

  public SourcePosition getSourcePosition(int start, int end) {
    SourcePosition spos = getSourcePosition(start);
    SourcePosition epos = getSourcePosition(end);
    return spos == null ? epos
        : epos == null ? spos
        : SourcePosition.spanning(spos, epos);
  }

  public SourcePosition getSourcePosition(int index) {
    Preconditions.checkArgument(index >= 0);
    int tokenIndex = Arrays.binarySearch(textIndices, index);
    int offsetWithinToken;
    if (tokenIndex >= 0) {
      offsetWithinToken = 0;
    } else {
      // The first token should start at position 0, so we don't have to worry
      // about the insertion point falling negative.
      tokenIndex = ~tokenIndex - 1;
      offsetWithinToken = index - textIndices[tokenIndex];
    }
    int eventIndex = eventIndices[tokenIndex];

    // Now we know where we are in a token, look left for a position marker.
    for (int i = eventIndex; --i >= 0;) {
      Event e = events.get(i);
      if (e.getKind() == Event.Kind.POSITION_MARK) {
        SourcePosition pos = e.getSourcePosition();
        return pos.shift(offsetWithinToken);
      }
    }

    return null;
  }

}
