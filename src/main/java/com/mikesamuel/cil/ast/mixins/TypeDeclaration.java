package com.mikesamuel.cil.ast.mixins;

import com.mikesamuel.cil.ast.BaseNode;
import com.mikesamuel.cil.ast.NodeI;
import com.mikesamuel.cil.ast.NodeType;
import com.mikesamuel.cil.ast.NodeVariant;
import com.mikesamuel.cil.ast.meta.TypeInfo;

/**
 * A mixin for nodes that might declare a class.
 *
 * <p>Some nodes like <code>class Foo { }</code> explicitly declare a class,
 * but some others may or may not.
 * For example, <code>(new Object())</code> does
 * not, but with an optional class body <code>(new Object() {})</code> there is
 * an anonymous class.
 */
public interface TypeDeclaration<
    B extends BaseNode<B, T, V>,
    T extends Enum<T> & NodeType<B, T>,
    V extends NodeVariant<B, T>>
extends NodeI<B, T, V> {
  /**
   * Sets the type declared.
   * @return this
   */
  public TypeDeclaration<B, T, V> setDeclaredTypeInfo(
      TypeInfo newDeclaredTypeInfo);

  /**
   * The type declared, if any.
   */
  public TypeInfo getDeclaredTypeInfo();
}
