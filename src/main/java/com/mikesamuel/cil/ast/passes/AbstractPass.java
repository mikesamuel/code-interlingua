package com.mikesamuel.cil.ast.passes;

import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Nullable;

import com.google.common.base.Preconditions;
import com.mikesamuel.cil.ast.j8.J8FileNode;
import com.mikesamuel.cil.parser.Positioned;
import com.mikesamuel.cil.util.LogUtils;

/** A compiler pass. */
public abstract class AbstractPass<T> {

  protected final Logger logger;
  private Level errorLevel = Level.SEVERE;

  protected AbstractPass(Logger logger) {
    this.logger = logger;
  }

  protected void error(@Nullable Positioned p, String message) {
    LogUtils.log(logger, errorLevel, p, message, null);
  }

  protected void warn(@Nullable Positioned p, String message) {
    LogUtils.log(logger, Level.WARNING, p, message, null);
  }

  /** Applies the pass to the given compilation units. */
  public abstract T run(Iterable<? extends J8FileNode> fileNodes);

  /** The logger used to log errors and warnings. */
  public Logger getLogger() {
    return logger;
  }

  /**
   * The log level used for error messages noted by this pass.
   */
  public Level getErrorLevel() {
    return errorLevel;
  }

  /**
   * Sets the log level used for error messages noted by this pass.
   */
  public AbstractPass<T> setErrorLevel(Level newErrorLevel) {
    this.errorLevel = Preconditions.checkNotNull(newErrorLevel);
    return this;
  }
}
