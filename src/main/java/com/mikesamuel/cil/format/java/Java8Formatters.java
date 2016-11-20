package com.mikesamuel.cil.format.java;

import com.mikesamuel.cil.ast.NodeVariant;
import com.mikesamuel.cil.event.Event;
import com.mikesamuel.cil.format.CStyleGrossStructurer;
import com.mikesamuel.cil.format.Formatter;
import com.mikesamuel.cil.parser.SList;

/**
 * A formatter for a stream of tokens in the Java 8 language.
 * <p>
 * Context hints are based on a stack of {NodeVariant}s like that derived by
 * walking the push/pops in a sequence of {@link Event}s.
 */
public final class Java8Formatters {
  private Java8Formatters() {
    // static API
  }

  /**
   * A formatter for Java 8.
   */
  public static Formatter<SList<NodeVariant>> createFormatter() {
    return new Formatter<>(new CStyleGrossStructurer<>(
        new Java8TokenBreaker()));
  }
}
