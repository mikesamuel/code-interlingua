package com.mikesamuel.cil.ast.passes;

import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Nullable;

import com.mikesamuel.cil.ast.NodeI;
import com.mikesamuel.cil.ast.traits.FileNode;
import com.mikesamuel.cil.util.LogUtils;

/** A compiler pass. */
public abstract class AbstractPass<T> {

  protected final Logger logger;

  protected AbstractPass(Logger logger) {
    this.logger = logger;
  }

  protected void error(@Nullable NodeI node, String message) {
    LogUtils.log(logger, Level.SEVERE, node, message, null);
  }

  protected void warn(@Nullable NodeI node, String message) {
    LogUtils.log(logger, Level.WARNING, node, message, null);
  }

  /** Applies the pass to the given compilation units. */
  public abstract T run(Iterable<? extends FileNode> fileNodes);
}
