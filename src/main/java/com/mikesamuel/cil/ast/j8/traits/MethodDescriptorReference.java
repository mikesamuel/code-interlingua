package com.mikesamuel.cil.ast.j8.traits;

import com.mikesamuel.cil.ast.meta.TypeSpecification;

/**
 * A reference to a specific method or constructor that can be resolved,
 * in context, to a particular method descriptor.
 */
public interface MethodDescriptorReference extends J8Trait {

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
  MethodDescriptorReference setMethodDescriptor(String newMethodDescriptor);

  /**
   * The name of the type that declares the referenced method.
   */
  TypeSpecification getMethodDeclaringType();

  /**
   * The name of the type that declares the referenced method.
   * @return this
   */
  MethodDescriptorReference setMethodDeclaringType(
      TypeSpecification newMethodDeclaringType);
}
