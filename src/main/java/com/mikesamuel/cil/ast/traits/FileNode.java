package com.mikesamuel.cil.ast.traits;

/**
 * A node that corresponds to an entire file.
 */
public interface FileNode extends ExpressionNameScope, TypeScope {
  @Override
  FileNode deepClone();
}
