package com.mikesamuel.cil.ast.passes.synth;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.mikesamuel.cil.ast.j8.J8BaseNode;
import com.mikesamuel.cil.ast.meta.MemberInfo;
import com.mikesamuel.cil.ast.meta.Name;
import com.mikesamuel.cil.ast.meta.TypeSpecification;

final class Accessor implements Comparable<Accessor> {
  /**
   * The member being accessed.
   */
  final MemberInfo accessee;
  /**
   * An expression or explicit constructor invocation that accesses
   * (possibly setting) the private API in the scope of the declaring class.
   */
  final J8BaseNode accessBody;
  final ImmutableList<Name> typeParametersToForward;
  final ImmutableList<String> typeParametersNames;
  /**
   * Count of ignored parameters used solely to disambiguate from
   * existing members.
   */
  final int disambiguatingParameters;
  final ImmutableList<TypeSpecification> parameterTypes;
  final ImmutableList<String> parameterNames;
  final TypeSpecification resultType;
  /**
   * A name which does not override or mask any other names in the
   * declaring class.
   */
  final String allocatedName;

  Accessor(
      MemberInfo accessee, J8BaseNode accessBody,
      ImmutableList<Name> typeParametersToForward,
      ImmutableList<String> typeParameterNames,
      int disambiguatingParameters,
      ImmutableList<TypeSpecification> parameterTypes,
      ImmutableList<String> parameterNames,
      TypeSpecification resultType, String allocatedName) {
    Preconditions.checkArgument(
        typeParametersToForward.size() == typeParameterNames.size());
    Preconditions.checkArgument(
        parameterTypes.size() == parameterNames.size());
    this.accessee = accessee;
    this.accessBody = accessBody;
    this.typeParametersToForward = typeParametersToForward;
    this.typeParametersNames = typeParameterNames;
    this.disambiguatingParameters = disambiguatingParameters;
    this.parameterTypes = parameterTypes;
    this.parameterNames = parameterNames;
    this.resultType = resultType;
    this.allocatedName = allocatedName;
  }

  @Override
  public int compareTo(Accessor o) {
    return allocatedName.compareTo(o.allocatedName);
  }
}