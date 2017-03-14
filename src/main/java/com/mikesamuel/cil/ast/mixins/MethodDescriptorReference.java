package com.mikesamuel.cil.ast.mixins;

import com.mikesamuel.cil.ast.BaseNode;
import com.mikesamuel.cil.ast.NodeI;
import com.mikesamuel.cil.ast.NodeType;
import com.mikesamuel.cil.ast.NodeVariant;
import com.mikesamuel.cil.ast.meta.TypeSpecification;

/**
 * A reference to a specific method or constructor that can be resolved,
 * in context, to a particular method descriptor.
 */
public interface MethodDescriptorReference<
    B extends BaseNode<B, T, V>,
    T extends Enum<T> & NodeType<B, T>,
    V extends NodeVariant<B, T>>
extends NodeI<B, T, V> {

  /**
   * The method descriptor if known.  Usually set by the class member and
   * typing passes.
   */
  String getMethodDescriptor();

  /**
   * @param newMethodDescriptor a well-formed method descriptor per
   *     <a href="https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-4.html#jvms-4.3.3">JVM 4.3.3</a>
   * @return this
   */
  MethodDescriptorReference<B, T, V> setMethodDescriptor(
      String newMethodDescriptor);

  /**
   * The name of the type that declares the referenced method.
   */
  TypeSpecification getMethodDeclaringType();

  /**
   * The name of the type that declares the referenced method.
   * @return this
   */
  MethodDescriptorReference<B, T, V> setMethodDeclaringType(
      TypeSpecification newMethodDeclaringType);
}
