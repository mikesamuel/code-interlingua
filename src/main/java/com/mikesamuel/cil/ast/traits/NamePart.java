package com.mikesamuel.cil.ast.traits;

import com.mikesamuel.cil.ast.NodeI;
import com.mikesamuel.cil.ast.meta.Name;

/**
 * A part of a {@linkplain Name name}.
 */
public interface NamePart extends NodeI {
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
