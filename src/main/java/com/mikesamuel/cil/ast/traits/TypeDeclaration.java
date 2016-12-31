package com.mikesamuel.cil.ast.traits;

import com.mikesamuel.cil.ast.NodeOrBuilder;
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
public interface TypeDeclaration extends NodeOrBuilder {
  /**
   * Sets the type declared.
   */
  public void setDeclaredTypeInfo(TypeInfo newDeclaredTypeInfo);

  /**
   * The type declared, if any.
   */
  public TypeInfo getDeclaredTypeInfo();
}
