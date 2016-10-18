package com.mikesamuel.cil.parser;

/**
 * Base type for something that can be associated with a subset of a
 * language.
 * <p>
 * This is simply a supplier for {@link ParSer}.
 */
public interface ParSerable {
  /**
   * A ParSer that can parse/serialize a superset of the language that
   * describes the ParSerable class's textual form.
   */
  ParSer getParSer();
}
