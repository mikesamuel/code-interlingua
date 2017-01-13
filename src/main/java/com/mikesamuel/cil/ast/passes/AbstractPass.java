package com.mikesamuel.cil.ast.passes;

import java.util.logging.Logger;

import javax.annotation.Nullable;

import com.mikesamuel.cil.ast.CompilationUnitNode;
import com.mikesamuel.cil.ast.NodeI;
import com.mikesamuel.cil.parser.SourcePosition;

/** A compiler pass. */
abstract class AbstractPass<T> {

  protected final Logger logger;

  AbstractPass(Logger logger) {
    this.logger = logger;
  }

  private static String fullMessage(@Nullable NodeI node, String message) {
    SourcePosition pos = node != null ? node.getSourcePosition() : null;
    return pos != null ? pos + ": " + message : message;
  }

  protected void error(@Nullable NodeI node, String message) {
    logger.severe(fullMessage(node, message));
  }

  protected void warn(@Nullable NodeI node, String message) {
    logger.warning(fullMessage(node, message));
  }

  /** Applies the pass to the given compilation units. */
  abstract T run(Iterable<? extends CompilationUnitNode> compilationUnits);
}
