package com.mikesamuel.cil.util;

import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Nullable;

import com.google.common.collect.ImmutableSet;
import com.mikesamuel.cil.ast.NodeI;
import com.mikesamuel.cil.parser.SourcePosition;

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
      Logger logger, Level level, NodeI node, String msg, Throwable th) {
    log(logger, level, node != null ? node.getSourcePosition() : null, msg, th);
  }

  /**
   * Emits a log message.
   */
  public static void log(
      Logger logger, Level level, SourcePosition pos, String message,
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
      @Nullable SourcePosition pos, String message) {
    return pos != null ? pos + ": " + message : message;
  }
}
