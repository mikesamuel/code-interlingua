package com.mikesamuel.cil.ast.j8.traits;

import com.mikesamuel.cil.ast.meta.Name;

/**
 * A part of a {@linkplain Name name}.
 */
public interface NamePart extends J8Trait {
  /**
   * The type of the name part.
   */
  public Name.Type getNamePartType();

  /**
   * Sets the type of the name part.
   * @return this
   */
  public NamePart setNamePartType(Name.Type newNamePartType);
}
