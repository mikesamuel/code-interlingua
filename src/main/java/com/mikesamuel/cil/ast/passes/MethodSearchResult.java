package com.mikesamuel.cil.ast.passes;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.mikesamuel.cil.ast.meta.CallableInfo;
import com.mikesamuel.cil.ast.meta.StaticType;
import com.mikesamuel.cil.ast.meta.MemberInfoPool.ParameterizedMember;
import com.mikesamuel.cil.ast.meta.StaticType.Cast;
import com.mikesamuel.cil.ast.meta.StaticType.TypePool.ArrayType;
import com.mikesamuel.cil.util.TriState;

final class MethodSearchResult {
  final ParameterizedMember<CallableInfo> m;
  final ImmutableList<StaticType> formalTypesInContext;
  final StaticType returnTypeInContext;
  final ImmutableList<Cast> actualToFormalCasts;
  final boolean constructsVariadicArray;

  MethodSearchResult(
      ParameterizedMember<CallableInfo> m,
      ImmutableList<StaticType> formalTypesInContext,
      StaticType returnTypeInContext,
      ImmutableList<Cast> actualToFormalCasts,
      boolean constructsVariadicArray) {
    this.m = m;
    this.formalTypesInContext = formalTypesInContext;
    this.returnTypeInContext = returnTypeInContext;
    this.actualToFormalCasts = actualToFormalCasts;
    this.constructsVariadicArray = constructsVariadicArray;
  }

  public boolean isStrictlyMoreSpecificThan(MethodSearchResult that) {
    // TODO: JLS S 15.12 does not use parameters after explicit replacement to
    // infer bounds.  We need to take the actual expressions too.
    // For e.g., test f(byte) vs f(int) given integral constants that fit in
    // [-128,127].
    ImmutableList<StaticType> aTypes = this.formalTypesInContext;
    ImmutableList<StaticType> bTypes = that.formalTypesInContext;

    boolean aIsVariadic = this.m.member.isVariadic();
    boolean bIsVariadic = that.m.member.isVariadic();

    int aArity = aTypes.size();
    int bArity = bTypes.size();

    StaticType aMarginalFormalType = null;
    if (aIsVariadic) {
      // The array type representing a variadic parameter should not be a
      // template variable that extends an array type since the array type
      // is manufactured.
      aMarginalFormalType = ((ArrayType) aTypes.get(aArity - 1)).elementType;
    }
    StaticType bMarginalFormalType = null;
    if (aIsVariadic) {
      // The array type representing a variadic parameter should not be a
      // template variable that extends an array type since the array type
      // is manufactured.
      bMarginalFormalType = ((ArrayType) bTypes.get(bArity - 1)).elementType;
    }

    int nonVarArgsArity = Math.min(
        aArity - (aIsVariadic ? 1 : 0),
        bArity - (bIsVariadic ? 1 : 0));

    if (TypingPass.DEBUG) {
      System.err.println(
          "Testing " + this.m.member.canonName.identifier + "\n"
          + this.m.member.getDescriptor()
          + "(" + Joiner.on(", ").join(this.formalTypesInContext) + ")\n"
          + "more specific than\n"
          + that.m.member.getDescriptor()
          + "(" + Joiner.on(", ").join(that.formalTypesInContext) + ")\n"
          + "aArity=" + aArity + ", bArity=" + bArity + "\n"
          + "aMarginalFormalType=" + aMarginalFormalType
          + ", bMarginalFormalType=" + bMarginalFormalType + "\n"
          + "nonVarArgsArity=" + nonVarArgsArity);
    }

    boolean oneMoreSpecificThan = false;
    for (int i = 0; i < nonVarArgsArity; ++i) {
      TriState s = parameterSpecificity(aTypes.get(i), bTypes.get(i));
      switch (s) {
        case OTHER:
          continue;
        case TRUE:
          oneMoreSpecificThan = true;
          break;
        case FALSE:
          if (TypingPass.DEBUG) {
            System.err.println("\tparameterSpecificity " + i + " = false");
          }
          return false;
      }
    }

    if (!(aIsVariadic || bIsVariadic)) {
      if (TypingPass.DEBUG) {
        System.err.println(
            "\tNeither variadic oneMoreSpecificThan=" + oneMoreSpecificThan
            + ", same arity=" + (aArity == bArity));
      }
      return oneMoreSpecificThan && aArity == bArity;
    }

    // These are the cases for dealing with varargs specificity where
    // a could be more specific than b.

    // B VARIADIC, A has extra non-variadic arguments whether variadic or not
    //   (A0, A1)
    //   (B...)

    //   (A0, A1, A2[])
    //   (B...)

    //   (A0, A1, A2...)
    //   (B...)
    // where A0...n are as or more specific than B

    // We deal with these three cases by running through the extra arguments
    // and then delegating to handlers for the cases below.
    int aIndex = nonVarArgsArity;
    int bIndex = nonVarArgsArity;
    for (int aNonVariadicLimit = aArity - (aIsVariadic ? 1 : 0);
         aIndex < aNonVariadicLimit; ++aIndex) {
      TriState s = parameterSpecificity(
          aTypes.get(aIndex), bMarginalFormalType);
      switch (s) {
        case OTHER:
        case TRUE:
          // This is the case because the required min arity is now more
          // specific.
          oneMoreSpecificThan = true;
          break;
        case FALSE:
          if (TypingPass.DEBUG) {
            System.err.println("\tExtra arg not more specific " + aIndex);
          }
          return false;
      }
    }
    oneMoreSpecificThan = true;

    // A has no more, b accepts more.
    //   ()
    //   (B...)
    if (aIndex == aArity && !aIsVariadic && bIndex + 1 == bArity) {
      if (TypingPass.DEBUG) {
        System.err.println("\tMore specific due to empty variadic match");
      }
      return true;
    }

    if (aIndex + 1 == aArity && bIndex + 1 == bArity) {
      Preconditions.checkState(bIsVariadic);  // Checked above.
      // BOTH VARIADIC, SAME COUNT
      //   (A...)
      //   (B...)
      // -> A is more specific than B
      TriState specificity;
      if (aIsVariadic) {
        specificity = parameterSpecificity(
            aMarginalFormalType, bMarginalFormalType);
      } else {
        // B VARIADIC, A has Array type.
        //   (A[])
        //   (B...)
        // -> A is as or more specific as B
        specificity = parameterSpecificity(
            aTypes.get(aArity - 1), bTypes.get(bArity - 1));
      }
      if (TypingPass.DEBUG) {
        System.err.println(
            "\tBoth variadic specificity=" + specificity
            + ", oneMoreSpecificThan=" + oneMoreSpecificThan);
      }
      switch (specificity) {
        case TRUE:  return true;
        case FALSE: return false;
        case OTHER: return oneMoreSpecificThan;
      }
    }

    // We can safely return false here because the cases below do not
    // establish specificity.
    //   (A...)
    //   (Object)
    //
    //   (Object...)
    //   (Serializable)
    //
    //   (Object...)
    //   (Cloneable)
    // because in these cases B has a more specific arity than A.
    if (TypingPass.DEBUG) {
      System.err.println("\tRan out of more specific cases");
    }
    return false;
  }

  /**
   * True -> a is more specific than b;
   * Other -> a is the same as b.
   * False -> a is not more specific than b.
   */
  private static TriState parameterSpecificity(
      StaticType aType, StaticType bType) {
    Cast c = bType.assignableFrom(aType);
    switch (c) {
      case CONFIRM_SAFE:
      case CONFIRM_UNCHECKED:
      case CONVERTING_LOSSLESS:
        return TriState.TRUE;
      case CONVERTING_LOSSY:
      case BOX:
      case UNBOX:
      case DISJOINT:
      case CONFIRM_CHECKED:
        return TriState.FALSE;
      case SAME:
        return TriState.OTHER;
    }
    throw new AssertionError(c);
  }
}