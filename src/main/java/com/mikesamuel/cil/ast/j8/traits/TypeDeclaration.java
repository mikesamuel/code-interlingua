package com.mikesamuel.cil.ast.j8.traits;

import com.mikesamuel.cil.ast.meta.TypeInfo;

/**
 * A trait for nodes that might declare a class.
 *
 * <p>Some nodes like <code>class Foo { }</code> explicitly declare a class,
 * but some others may or may not.
 * For example, <code>(new Object())</code> does
 * not, but with an optional class body <code>(new Object() {})</code> there is
 * an anonymous class.
 */
public interface TypeDeclaration extends J8Trait {
  /**
   * Sets the type declared.
   * @return this
   */
  public TypeDeclaration setDeclaredTypeInfo(TypeInfo newDeclaredTypeInfo);

  /**
   * The type declared, if any.
   */
  public TypeInfo getDeclaredTypeInfo();
}
