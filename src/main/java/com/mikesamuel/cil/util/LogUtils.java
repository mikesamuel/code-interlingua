package com.mikesamuel.cil.util;

import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Nullable;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableSet;
import com.mikesamuel.cil.ast.Trees;
import com.mikesamuel.cil.ast.j8.J8BaseNode;
import com.mikesamuel.cil.event.Event;
import com.mikesamuel.cil.parser.Positioned;
import com.mikesamuel.cil.parser.SList;
import com.mikesamuel.cil.parser.SerialErrorReceiver;
import com.mikesamuel.cil.parser.SerialState;
import com.mikesamuel.cil.parser.SourcePosition;
import com.mikesamuel.cil.parser.Unparse;
import com.mikesamuel.cil.parser.Unparse.UnparseVerificationException;
import com.mikesamuel.cil.parser.Unparse.Verified;

/**
 * Utilities for java.util.logging.
 */
public class LogUtils {

  private static final ImmutableSet<String> LOG_WRAPPER_METHOD_NAMES =
      ImmutableSet.of("log", "error", "warn");

  /**
   * Emits a log message.
   */
  public static void log(
      Logger logger, Level level, @Nullable Positioned pos, String message,
      Throwable th) {
    String fullMessage = fullMessage(pos, message);
    StackTraceElement[] trace = Thread.currentThread().getStackTrace();
    String sourceClass = null;
    String sourceMethod = null;
    for (int i = 1, n = trace.length; i < n; ++i) {
      StackTraceElement traceElement = trace[i];
      String methodName = traceElement.getMethodName();
      if (!LOG_WRAPPER_METHOD_NAMES.contains(methodName)) {
        sourceClass = traceElement.getClassName();
        sourceMethod = methodName;
        break;
      }
    }

    logger.logp(level, sourceClass, sourceMethod, fullMessage, th);
  }

  private static String fullMessage(
      @Nullable Positioned p, String message) {
    SourcePosition pos = p != null ? p.getSourcePosition() : null;
    return pos != null ? pos + ": " + message : message;
  }

  /** Best effort to format the given AST.  Output is not length limited. */
  public static String serialize(J8BaseNode node) {
    SList<Event> events = Trees.startUnparse(null, node, null);
    Optional<SerialState> sstate = node.getNodeType().getParSer().unparse(
        new SerialState(SList.forwardIterable(events)),
        SerialErrorReceiver.DEV_NULL);
    if (sstate.isPresent()) {
      Verified verified;
      try {
        verified = Unparse.verify(
            SList.forwardIterable(sstate.get().output));
      } catch (@SuppressWarnings("unused")
               UnparseVerificationException e) {
        verified = null;
      }
      if (verified != null) {
        return Unparse.format(verified).code;
      }
    }
    return "<unprintable " + node.getNodeType() + ">";
  }
}
