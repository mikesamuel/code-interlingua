package com.mikesamuel.cil.ast.passes;

import com.google.common.collect.ImmutableList;
import com.mikesamuel.cil.ast.meta.CallableInfo;
import com.mikesamuel.cil.ast.meta.MemberInfo;
import com.mikesamuel.cil.ast.meta.MethodDescriptor;
import com.mikesamuel.cil.ast.meta.Name;
import com.mikesamuel.cil.ast.meta.StaticType;
import com.mikesamuel.cil.ast.meta.TypeInfo;
import com.mikesamuel.cil.ast.meta.TypeSpecification;

/**
 * Some utilities for creating constructors that forward to other constructors.
 * This is used by a variety of desugaring passes that need to do tricks to
 * preserve initialization order.
 */
public final class ForwardingConstructorUtil {
  /**
   * Describes a strategy used to ensure that closed over state is
   * initialized before any super-class constructor calls to methods
   * declared in this class.
   * <p>
   * This returns a list of parameter types that can be prepended to
   * the formal parameter list and whose values may be ignored.
   * <p>
   * Calls to existing constructors in ti will not be ambiguous with
   * a constructor that has these parameters before parameters
   * that forward to one of the existing constructors.
   */
  public static ImmutableList<TypeSpecification> computeDelegateStrategy(
      TypeInfo ti) {
    ImmutableList<CallableInfo> ctors;
    {
      ImmutableList.Builder<CallableInfo> b = ImmutableList.builder();
      for (MemberInfo mi : ti.getDeclaredMembers()) {
        if (mi instanceof CallableInfo) {
          CallableInfo ci = (CallableInfo) mi;
          if (ci.isConstructor()) {
            b.add(ci);
          }
        }
      }
      ctors = b.build();
    }

    int maxLeadingBooleans = 1;  // We need at least one.
    boolean anyHasLeadingBooleanVariadicParameter = false;
    Name tboolean = StaticType.T_BOOLEAN.typeSpecification.rawName;
    Name tBoolean = StaticType.T_BOOLEAN.wrapperType;
    for (CallableInfo ctor : ctors) {
      MethodDescriptor d = ctor.getDescriptor();
      int n = d.formalTypes.size();
      for (int i = 0; i < n; ++i) {
        TypeSpecification pn = d.formalTypes.get(i);
        if (pn.rawName.equals(tboolean)|| pn.rawName.equals(tBoolean)) {
          if (pn.nDims == 0) {
            maxLeadingBooleans = Math.max(maxLeadingBooleans, i + 1);
            continue;
          }
          if (pn.nDims == 1 && i + 1 == n) {
            if (ctor.isVariadic()) {
              anyHasLeadingBooleanVariadicParameter = true;
            }
          }
        }
        break;
      }
    }
    if (anyHasLeadingBooleanVariadicParameter) {
      return ImmutableList.of(
          StaticType.T_BOOLEAN.typeSpecification,
          StaticType.T_BYTE.typeSpecification);
    }
    ImmutableList.Builder<TypeSpecification> b = ImmutableList.builder();
    for (int i = 0; i < maxLeadingBooleans; ++i) {
      b.add(StaticType.T_BOOLEAN.typeSpecification);
    }
    return b.build();
  }
}
