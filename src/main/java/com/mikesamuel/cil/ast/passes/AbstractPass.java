package com.mikesamuel.cil.ast.passes;

import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Nullable;

import com.mikesamuel.cil.ast.CompilationUnitNode;
import com.mikesamuel.cil.ast.NodeI;
import com.mikesamuel.cil.util.LogUtils;

/** A compiler pass. */
abstract class AbstractPass<T> {

  protected final Logger logger;

  AbstractPass(Logger logger) {
    this.logger = logger;
  }

  protected void error(@Nullable NodeI node, String message) {
    LogUtils.log(logger, Level.SEVERE, node, message, null);
  }

  protected void warn(@Nullable NodeI node, String message) {
    LogUtils.log(logger, Level.WARNING, node, message, null);
  }

  /** Applies the pass to the given compilation units. */
  abstract T run(Iterable<? extends CompilationUnitNode> compilationUnits);
}
