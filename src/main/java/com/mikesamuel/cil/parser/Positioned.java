package com.mikesamuel.cil.parser;

import javax.annotation.Nullable;

/**
 * Something that may occur at a {@link SourcePosition}.
 */
public interface Positioned {
  /***/
  @Nullable SourcePosition getSourcePosition();
}
