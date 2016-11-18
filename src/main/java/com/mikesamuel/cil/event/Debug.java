package com.mikesamuel.cil.event;

import java.io.PrintStream;

import com.google.common.annotations.VisibleForTesting;

@SuppressWarnings("javadoc")
@VisibleForTesting
public class Debug {

  public static void dumpEvents(Iterable<? extends MatchEvent> events) {
    dumpEvents("", events, System.err);
  }

  public static void dumpEvents(
      String indent, Iterable<? extends MatchEvent> events, PrintStream err) {
    StringBuilder sb = new StringBuilder();
    sb.append(indent).append(". ");
    int pushDepth = 0;
    for (MatchEvent e : events) {
      if (e instanceof MatchEvent.Pop) {
        if (pushDepth != 0) {
          --pushDepth;
          sb.setLength(sb.length() - 2);
        }
      }
      int len = sb.length();
      err.println(sb.append(e));
      sb.setLength(len);
      if (e instanceof MatchEvent.Push) {
        ++pushDepth;
        sb.append(". ");
      }
    }
  }
}
