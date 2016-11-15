package com.mikesamuel.cil.format;

/**
 * Formatted source code bundled with a source line mapping.
 */
public final class FormattedSource {
  /** The formatted code. */
  public final String code;
  /** A position mapping where derived indices index {@link #code}. */
  public final PositionMapping positionMapping;

  /** */
  public FormattedSource(String code, PositionMapping positionMapping) {
    this.code = code;
    this.positionMapping = positionMapping;
  }
}
