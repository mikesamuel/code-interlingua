package com.mikesamuel.cil.util;

/** Boolean or a context dependent other. */
public enum TriState {
  /** */
  FALSE,
  /** */
  TRUE,
  /** */
  OTHER,
  ;

  /** */
  public static TriState of(boolean b) {
    return b ? TRUE : FALSE;
  }
}
