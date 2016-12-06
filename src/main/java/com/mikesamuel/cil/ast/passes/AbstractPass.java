package com.mikesamuel.cil.ast.passes;

import com.mikesamuel.cil.ast.CompilationUnitNode;

/** A compiler pass. */
public interface AbstractPass<T> {

  /** Applies the pass to the given compilation units. */
  T run(Iterable<? extends CompilationUnitNode> compilationUnits);
}
