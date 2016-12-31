package com.mikesamuel.cil.ast.traits;

import com.mikesamuel.cil.ast.NodeOrBuilder;
import com.mikesamuel.cil.ast.meta.Name;

/**
 * A part of a {@linkplain Name name}.
 */
public interface NamePart extends NodeOrBuilder {
  /**
   * The type of the name part.
   */
  public Name.Type getNamePartType();

  /**
   * Sets the type of the name part.
   */
  public void setNamePartType(Name.Type newNamePartType);
}
