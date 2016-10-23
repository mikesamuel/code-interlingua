package com.mikesamuel.cil.ptree;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableRangeSet;
import com.google.common.collect.RangeSet;
import com.mikesamuel.cil.ast.MatchEvent;
import com.mikesamuel.cil.parser.MatchErrorReceiver;
import com.mikesamuel.cil.parser.MatchState;
import com.mikesamuel.cil.parser.ParseErrorReceiver;
import com.mikesamuel.cil.parser.ParseState;
import com.mikesamuel.cil.parser.SerialErrorReceiver;
import com.mikesamuel.cil.parser.SerialState;

final class PatternMatch extends PTParSer {
  final Pattern p;
  final String diagnostic;
  final ImmutableRangeSet<Integer> la1;

  PatternMatch(String regex, ImmutableRangeSet<Integer> la1, String diagnostic) {
    this.p = Pattern.compile("^(?:" + regex + ")");
    this.diagnostic = diagnostic;
    this.la1 = la1;
  }

  @Override
  public Optional<ParseState> parse(
      ParseState state, ParseErrorReceiver err) {
    Matcher m = state.matcherAtStart(p);
    if (m.find()) {
      Preconditions.checkState(m.start() == state.indexAfterIgnorables());
      ParseState stateAfter = state.advance(m.end() - m.start(), true)
          .appendOutput(MatchEvent.content(m.group()));
      return Optional.of(stateAfter);
    } else {
      String message;
      if (state.isEmpty()) {
        message = "Expected Literal but found end of file";
      } else {
        int snippetIndex = state.indexAfterIgnorables();
        int snippetEnd = snippetIndex + 10;
        String content = state.input.content;
        int contentEnd = content.length();
        boolean needsEllipsis = contentEnd > snippetEnd;
        if (!needsEllipsis) {
          snippetEnd = contentEnd;
        }
        message = "Expected Literal but found `"
            + (content.substring(snippetIndex, snippetEnd)
                .replace("\\", "\\\\")
                .replace("\r", "\\r")
                .replace("\n", "\\n"))
            + (needsEllipsis ? "..." : "") + "`";
      }
      err.error(state, message);
      return Optional.absent();
    }
  }

  @Override
  public Optional<SerialState> unparse(
      SerialState state, SerialErrorReceiver err) {
    if (state.isEmpty()) {
      err.error(state, "Expected content event but found end-of-input");
      return Optional.absent();
    }
    MatchEvent e = state.events.get(state.index);
    if (e instanceof MatchEvent.ContentMatchEvent) {
      String content = ((MatchEvent.ContentMatchEvent) e).content;
      if (p.matcher(content).matches()) {
        return Optional.of(state.append(content).advance());
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
    MatchEvent e = state.events.get(state.index);
    if (e instanceof MatchEvent.ContentMatchEvent) {
      String content = ((MatchEvent.ContentMatchEvent) e).content;
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
  Kind getKind() {
    return Kind.REX;
  }

  @Override
  public String toString() {
    return "(/" + this.p.pattern() + "/)";
  }

  @Override
  RangeSet<Integer> computeLookahead1() {
    return la1;
  }
}