package com.mikesamuel.cil.ast;

import java.io.PrintStream;

import com.google.common.annotations.VisibleForTesting;

@SuppressWarnings("javadoc")
@VisibleForTesting
public class Debug {

  public static void dumpEvents(Iterable<? extends MatchEvent> events) {
    dumpEvents(events, System.err);
  }

  public static void dumpEvents(
      Iterable<? extends MatchEvent> events, PrintStream err) {
    StringBuilder sb = new StringBuilder(". ");
    for (MatchEvent e : events) {
      if (e instanceof MatchEvent.Pop) {
        if (sb.length() != 0) {
          sb.setLength(sb.length() - 2);
        }
      }
      int len = sb.length();
      err.println(sb.append(e));
      sb.setLength(len);
      if (e instanceof MatchEvent.Push) {
        sb.append(". ");
      }
    }
  }
}
