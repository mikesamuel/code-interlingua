package com.mikesamuel.cil.ptree;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.mikesamuel.cil.event.Event;
import com.mikesamuel.cil.parser.ForceFitState;
import com.mikesamuel.cil.parser.LeftRecursion;
import com.mikesamuel.cil.parser.MatchErrorReceiver;
import com.mikesamuel.cil.parser.MatchState;
import com.mikesamuel.cil.parser.ParseErrorReceiver;
import com.mikesamuel.cil.parser.ParseResult;
import com.mikesamuel.cil.parser.ParseState;
import com.mikesamuel.cil.parser.SerialErrorReceiver;
import com.mikesamuel.cil.parser.SerialState;

final class PatternMatch extends PTParSer {
  final Pattern p;
  final String diagnostic;

  PatternMatch(String regex, String diagnostic) {
    this.p = Pattern.compile("^(?:" + regex + ")");
    this.diagnostic = diagnostic;
  }

  @Override
  public boolean fastMatch(String input) {
    return p.matcher(input).matches();
  }

  @Override
  public ParseResult parse(
      ParseState state, LeftRecursion lr, ParseErrorReceiver err) {
    Matcher m = state.matcherAtStart(p);
    if (m.find()) {
      Preconditions.checkState(m.start() == state.index);
      Event content = Event.content(m.group(), state.index);
      ParseState stateAfter = state.advance(m.end() - m.start())
          .appendOutput(content);
      return ParseResult.success(
          stateAfter, ParseResult.NO_WRITE_BACK_RESTRICTION, ImmutableSet.of());
    } else {
      String message;
      if (state.isEmpty()) {
        message = "Expected Literal but found end of file";
      } else {
        int snippetIndex = state.index;
        int snippetEnd = snippetIndex + 10;
        CharSequence content = state.input.content();
        int contentEnd = content.length();
        boolean needsEllipsis = contentEnd > snippetEnd;
        if (!needsEllipsis) {
          snippetEnd = contentEnd;
        }
        message = "Expected Literal but found `"
            + (content.subSequence(snippetIndex, snippetEnd).toString()
                .replace("\\", "\\\\")
                .replace("\r", "\\r")
                .replace("\n", "\\n"))
            + (needsEllipsis ? "..." : "") + "`";
      }
      err.error(state, message);
      return ParseResult.failure();
    }
  }

  @Override
  public Optional<SerialState> unparse(
      SerialState state, SerialErrorReceiver err) {
    if (state.isEmpty()) {
      err.error(state, "Expected content event but found end-of-input");
      return Optional.absent();
    }
    Event e = state.structure.get(state.index);
    if (e.getKind() == Event.Kind.CONTENT) {
      String content = e.getContent();
      if (p.matcher(content).matches()) {
        return Optional.of(state.advanceWithCopy());
      } else {
        err.error(
            state, "Expected content like " + diagnostic
            + " but got `" + content + "`");
        return Optional.absent();
      }
    } else {
      err.error(state, "Expected " + diagnostic + " but got " + e);
      return Optional.absent();
    }
  }

  @Override
  public Optional<MatchState> match(MatchState state, MatchErrorReceiver err) {
    if (state.isEmpty()) {
      err.error(state, "Expected content event but found end-of-input");
      return Optional.absent();
    }
    Event e = state.events.get(state.index);
    if (e.getKind() == Event.Kind.CONTENT) {
      String content = e.getContent();
      if (p.matcher(content).matches()) {
        return Optional.of(state.advance());
      } else {
        err.error(
            state, "Expected content like " + diagnostic
            + " but got `" + content + "`");
        return Optional.absent();
      }
    } else {
      err.error(state, "Expected " + diagnostic + " but got " + e);
      return Optional.absent();
    }
  }

  @Override
  public ForceFitState forceFit(ForceFitState state) {
    return state.withFits(ImmutableSet.of());
  }

  @Override
  Kind getKind() {
    return Kind.REX;
  }

  @Override
  public void appendShallowStructure(StringBuilder sb) {
    sb.append("(/");
    sb.append(this.p.pattern());
    sb.append("/)");
  }
}
