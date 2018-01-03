package com.mikesamuel.cil.event;

import com.google.common.base.Optional;
import com.mikesamuel.cil.parser.Unparse;

/**
 * Double checks that a prefix of a suffix of the unparsed output
 * would reparse properly.
 */
public interface DelayedCheckPredicate {
  /** The problem with the suffix if any. */
  Optional<String> problem(Unparse.Suffix suffix);
}
