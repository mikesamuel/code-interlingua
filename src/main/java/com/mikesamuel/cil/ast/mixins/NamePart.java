package com.mikesamuel.cil.ast.mixins;

import com.mikesamuel.cil.ast.BaseNode;
import com.mikesamuel.cil.ast.NodeI;
import com.mikesamuel.cil.ast.NodeType;
import com.mikesamuel.cil.ast.NodeVariant;
import com.mikesamuel.cil.ast.meta.Name;

/**
 * A part of a {@linkplain Name name}.
 */
public interface NamePart<
    B extends BaseNode<B, T, V>,
    T extends Enum<T> & NodeType<B, T>,
    V extends NodeVariant<B, T>>
extends NodeI<B, T, V> {
  /**
   * The type of the name part.
   */
  public Name.Type getNamePartType();

  /**
   * Sets the type of the name part.
   * @return this
   */
  public NamePart<B, T, V> setNamePartType(Name.Type newNamePartType);
}
