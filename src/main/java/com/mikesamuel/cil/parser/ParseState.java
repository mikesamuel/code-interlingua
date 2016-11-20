package com.mikesamuel.cil.parser;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.mikesamuel.cil.event.Event;

/**
 * State of a parse.
 */
public final class ParseState {
  /** The input to parse. */
  public final Input input;
  /** The position of the parse cursor in the index. */
  public final int index;
  /** The output which can be replayed for a tree builder. */
  public final @Nullable SList<Event> output;

  /** A parse state at the beginning of input with no output. */
  public ParseState(Input input) {
    this(input, input.indexAfterIgnorables(0), null);
  }

  private ParseState(
      Input input, int index, @Nullable SList<Event> output) {
    Preconditions.checkState(0 <= index && index <= input.content.length());
    this.input = input;
    this.index = index;
    this.output = output != null ? output : null;
  }

  /** True if no unprocessed input except for ignorable tokens. */
  public boolean isEmpty() {
    return index == input.content.length();
  }

  /**
   * A state whose parse point is at the beginning of the leftmost token that
   * starts at least n chars forward of the current parse position or at the
   * end of input if there are no tokens that start n or more characters from
   * the current parse position.
   */
  public ParseState advance(int n) {
    Preconditions.checkArgument(n >= 0);
    int newIndex = input.indexAfterIgnorables(index + n);
    if (newIndex == index) {
      return this;
    }
    Preconditions.checkState(newIndex <= input.content.length());
    return withIndex(newIndex);
  }

  /** A state like this but with the given event appended. */
  public ParseState appendOutput(Event e) {
    return withOutput(SList.append(output, e));
  }

  /**
   * A state like this but with the given output.
   */
  public ParseState withOutput(SList<Event> newOutput) {
    ParseState ps = new ParseState(input, index, newOutput);
    return ps;
  }

  /**
   * A state like this but with the given input index.
   */
  public ParseState withIndex(int newIndex) {
    Preconditions.checkArgument(
        input.indexAfterIgnorables(newIndex) == newIndex,
        "Ignorable tokens not skipped");
    if (index == newIndex) {
      return this;
    }
    return new ParseState(input, newIndex, output);
  }

  /**
   * True iff there is a token at the current parse position with the given text.
   * Since this parser is scannerless, this will return true if the given text
   * is a prefix of the next token unless a merge guard is provided.
   *
   * @param hazardDetector if present, receives the input and bounds of the
   *    matched token so it can check for token merging hazards.
   */
  public boolean startsWith(
      String text, Optional<TokenMergeGuard> hazardDetector) {
    if (input.content.regionMatches(index, text, 0, text.length())) {
      if (!hazardDetector.isPresent()) {
        return true;
      }
      TokenMergeGuard hazard = hazardDetector.get();
      if (!hazard.isHazard(input.content, index, index + text.length())) {
        return true;
      }
    }
    return false;
  }

  /**
   * A matcher for the given pattern at the current parse position.
   */
  public Matcher matcherAtStart(Pattern p) {
    Matcher m = p.matcher(input.content);
    m.region(index, input.content.length());
    m.useTransparentBounds(false);
    m.useAnchoringBounds(true);
    return m;
  }

  @Override
  public String toString() {
    String content = input.content;
    String inputFragment;
    int fragmentEnd = index + 10;
    if (fragmentEnd >= content.length()) {
      inputFragment = content.substring(
          index, Math.min(fragmentEnd, content.length()));
    } else {
      inputFragment = content.substring(index, fragmentEnd) + "...";
    }
    return "(ParseState index=" + index + ", input=`" + inputFragment + "`)";
  }
}
