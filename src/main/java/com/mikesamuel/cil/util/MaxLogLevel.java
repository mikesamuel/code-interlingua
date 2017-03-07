package com.mikesamuel.cil.util;

import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

/**
 * A logger handler that keeps track of the highest log-level seen so that
 * we can check whether any fatal errors have occurred.
 */
public final class MaxLogLevel extends Handler {
  private Level maxLevelSeen = Level.OFF;

  {
    setLevel(Level.WARNING);
  }

  /** The highest level record published to this handler. */
  public synchronized Level getMaxLevelSeen() {
    return maxLevelSeen;
  }

  /**
   * True iff there was a {@link Level#SEVERE}
   * or higher level log record published.
   */
  public synchronized boolean hasFatalErrors() {
    int iv = maxLevelSeen.intValue();
    return iv != Level.OFF.intValue() && iv >= Level.SEVERE.intValue();
  }

  @Override
  public synchronized void publish(LogRecord record) {
    Level recordLevel = record.getLevel();
    if (maxLevelSeen.intValue() == Level.OFF.intValue()
        || recordLevel.intValue() > maxLevelSeen.intValue()) {
      this.maxLevelSeen = recordLevel;
    }
  }

  @Override
  public void flush() {
    // Nothing to flush.
  }

  @Override
  public void close() {
    // Nothing to close.
  }
}
