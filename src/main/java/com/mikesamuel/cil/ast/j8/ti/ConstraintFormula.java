package com.mikesamuel.cil.ast.j8.ti;

import java.util.Collection;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Nullable;

import com.mikesamuel.cil.ast.j8.ExpressionNode;
import com.mikesamuel.cil.ast.meta.Name;
import com.mikesamuel.cil.ast.meta.StaticType;
import com.mikesamuel.cil.ast.meta.StaticType.Cast;
import com.mikesamuel.cil.ast.meta.StaticType.PrimitiveType;
import com.mikesamuel.cil.ast.meta.StaticType.TypePool;
import com.mikesamuel.cil.ast.meta.TypeSpecification;
import com.mikesamuel.cil.ast.passes.AbstractRewritingPass.Parent;
import com.mikesamuel.cil.parser.SList;
import com.mikesamuel.cil.util.LogUtils;

abstract class ConstraintFormula {
  @Override public abstract String toString();
  @Override public abstract int hashCode();
  @Override public abstract boolean equals(@Nullable Object obj);
  abstract boolean inSimplestForm();
  abstract void reduce(
      TypePool thetaPool, Logger logger,
      UncheckedConversionCallback ucc,
      Collection<? super ConstraintFormula> out);

  enum Operand {
    IS_SUBTYPE_OF,
    IS_SUPERTYPE_OF,
    ;
  }

  static boolean compatibleInLooseInvocationContext(
      StaticType s, StaticType t, UncheckedConversionCallback ucc) {
    Cast c = t.assignableFrom(s);
    switch (c) {
      case CONFIRM_UNCHECKED:
        ucc.uncheckedConversionOccurred(
            s.typeSpecification, t.typeSpecification);
        // $FALL-THROUGH$
      case BOX:
      case UNBOX:
      case CONFIRM_CHECKED:
      case CONFIRM_SAFE:
      case CONVERTING_LOSSLESS:
      case SAME:
        return true;
      case CONVERTING_LOSSY:
      case DISJOINT:
        return false;
    }
    throw new AssertionError(c);
  }

  static SyntheticType maybeAsInferenceVariable(SyntheticType t) {
    if (t instanceof NominalType) {
      StaticType tt = ((NominalType) t).t;
      if (InferenceVariable.ALPHA_CONTAINER.equals(
              tt.typeSpecification.rawName.parent)) {
        return new InferenceVariable(tt.typeSpecification.rawName);
      }
    }
    return t;
  }
}

final class ConstConstraintFormula extends ConstraintFormula {
  final boolean boundable;

  static final ConstConstraintFormula
      FALSE = new ConstConstraintFormula(false),
      TRUE = new ConstConstraintFormula(true);

  private ConstConstraintFormula(boolean boundable) {
    this.boundable = boundable;
  }

  @Override
  public String toString() {
    return String.valueOf(boundable);
  }

  @Override
  public int hashCode() {
    return boundable ? 1 : 0;
  }

  @Override
  public boolean equals(Object obj) {
    return obj == this;
  }

  @Override
  boolean inSimplestForm() {
    return true;
  }

  @Override
  void reduce(
      TypePool thetaPool, Logger logger, UncheckedConversionCallback ucc,
      Collection<? super ConstraintFormula> out) {
    out.add(this);
  }
}

/**
 * A relationship between an actual argument and a formal parameter
 * that is pertinent to applicability.
 */
final class ArgConstraintFormula extends ConstraintFormula {
  final SList<Parent> pathToActual;
  final ExpressionNode actual;
  final TypeSpecification formalTypeCrossTheta;

  ArgConstraintFormula(
      SList<Parent> pathToActual, ExpressionNode actual,
      TypeSpecification formalTypeCrossTheta) {
    this.pathToActual = pathToActual;
    this.actual = actual;
    this.formalTypeCrossTheta = formalTypeCrossTheta;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((actual == null) ? 0 : actual.hashCode());
    result = prime * result + ((formalTypeCrossTheta == null) ? 0
        : formalTypeCrossTheta.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    ArgConstraintFormula other = (ArgConstraintFormula) obj;
    if (!actual.equals(other.actual)) {
      return false;
    }
    if (!formalTypeCrossTheta.equals(other.formalTypeCrossTheta)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "<" + actual + " → " + formalTypeCrossTheta + ">";
  }

  @Override
  boolean inSimplestForm() {
    return false;
  }

  @Override
  void reduce(
      TypePool thetaPool, Logger logger, UncheckedConversionCallback ucc,
      Collection<? super ConstraintFormula> out) {
    // https://docs.oracle.com/javase/specs/jls/se8/html/jls-18.html#jls-18.2.1
    // A constraint formula of the form ‹Expression → T› is reduced as follows:

    // If T is a proper type, the constraint reduces to true if the expression
    // is compatible in a loose invocation context with T (S5.3), and false
    // otherwise.
    if (InferenceVariable.isProperType(formalTypeCrossTheta)) {
      StaticType s = actual.getStaticType();
      if (s == null) {
        LogUtils.log(
            logger, Level.SEVERE, actual.getSourcePosition(),
            "Missing type information", null);
        out.add(ConstConstraintFormula.FALSE);
        return;
      }
      StaticType t = thetaPool.type(
          formalTypeCrossTheta, actual.getSourcePosition(), logger);
      out.add(
          compatibleInLooseInvocationContext(s, t, ucc)
          ? ConstConstraintFormula.TRUE
          : ConstConstraintFormula.FALSE);
      return;
    }

    // Otherwise, if the expression is a standalone expression (S15.2) of type
    // S, the constraint reduces to ‹S → T›.
    if (Polyexpressions.isStandaloneExpression(this.pathToActual)) {
      StaticType s = actual.getStaticType();
      if (s == null) {
        LogUtils.log(
            logger, Level.SEVERE, actual.getSourcePosition(),
            "Missing type information", null);
        out.add(ConstConstraintFormula.FALSE);
        return;
      }
      s = thetaPool.type(
          s.typeSpecification, actual.getSourcePosition(), logger);
      StaticType t = thetaPool.type(
          formalTypeCrossTheta, actual.getSourcePosition(), logger);
      // TODO: If this is a recursive call, then do we need to use s x theta?
      out.add(new TypeConstraintFormula(s, t));
      return;
    }

    // Otherwise, the expression is a poly expression (§15.2). The result
    // depends on the form of the expression:

    // If the expression is a parenthesized expression of the form
    // ( Expression' ), the constraint reduces to ‹Expression' → T›.
    // TODO

    // If the expression is a class instance creation expression or a method
    // invocation expression, the constraint reduces to the bound set B3 which
    // would be used to determine the expression's invocation type when
    // targeting T, as defined in §18.5.2.
    // (For a class instance creation expression, the corresponding "method"
    // used for inference is defined in §15.9.3).
    // TODO

    // This bound set may contain new inference variables, as well as
    // dependencies between these new variables and the inference variables in
    // T.
    // TODO

    // If the expression is a conditional expression of the form e1 ? e2 : e3,
    // the constraint reduces to two constraint formulas,
    // ‹e2 → T› and ‹e3 → T›.
    // TODO

    // If the expression is a lambda expression or a method reference
    // expression, the result is specified below.
    // TODO
    throw new Error("TODO " + this);
  }
}

final class TypeConstraintFormula extends ConstraintFormula {
  final StaticType s;
  final StaticType t;

  TypeConstraintFormula(StaticType s, StaticType t) {
    this.s = s;
    this.t = t;
  }

  @Override
  public String toString() {
    return "<" + s + " -> " + t + ">";
  }

  @Override
  public int hashCode() {
    return (31 * s.hashCode()) + t.hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof TypeConstraintFormula)) {
      return false;
    }
    TypeConstraintFormula that = (TypeConstraintFormula) obj;
    return this.s.equals(that.s) && this.t.equals(that.t);
  }

  @Override
  boolean inSimplestForm() {
    return false;
  }

  @Override
  void reduce(
      TypePool thetaPool, Logger logger, UncheckedConversionCallback ucc,
      Collection<? super ConstraintFormula> out) {
    // https://docs.oracle.com/javase/specs/jls/se8/html/jls-18.html#jls-18.2.2
    // A constraint formula of the form ‹S → T› is reduced as follows:

    // If S and T are proper types, the constraint reduces to true if S is
    // compatible in a loose invocation context with T (§5.3), and false
    // otherwise.
    if (InferenceVariable.isProperType(s.typeSpecification)
        && InferenceVariable.isProperType(t.typeSpecification)) {
      out.add(
          compatibleInLooseInvocationContext(s, t, ucc)
          ? ConstConstraintFormula.TRUE
          : ConstConstraintFormula.FALSE);
      return;
    }

    // Otherwise, if S is a primitive type, let S' be the result of applying
    // boxing conversion (§5.1.7) to S.
    // Then the constraint reduces to ‹S' → T›.
    if (s instanceof PrimitiveType) {
      TypePool.ReferenceType sp = thetaPool.box((PrimitiveType) s);
      out.add(new TypeConstraintFormula(sp, t));
      return;
    }

    // Otherwise, if T is a primitive type, let T' be the result of applying
    // boxing conversion (§5.1.7) to T.
    // Then the constraint reduces to ‹S = T'›.
    if (t instanceof PrimitiveType) {
      TypePool.ReferenceType tp = thetaPool.box((PrimitiveType) t);
      out.add(new TypeConstraintFormula(s, tp));
      return;
    }

    // Otherwise, if T is a parameterized type of the form G<T1, ..., Tn>,
    // and there exists no type of the form G<...> that is a supertype of S,
    // but the raw type G is a supertype of S, then the constraint reduces
    // to true.
    if (!t.typeSpecification.bindings.isEmpty()
        && s.typeSpecification.rawName.type == Name.Type.CLASS
        && reparameterizeWithBounds(t).assignableFrom(s) == Cast.DISJOINT
        && t.toErasedType().assignableFrom(s) == Cast.CONFIRM_SAFE) {
      ucc.uncheckedConversionOccurred(
          s.typeSpecification, t.typeSpecification);
      out.add(ConstConstraintFormula.TRUE);
      return;
    }

    // Otherwise, if T is an array type of the form G<T1, ..., Tn>[]k, and
    // there exists no type of the form G<...>[]k that is a supertype of S,
    // but the raw type G[]k is a supertype of S, then the constraint reduces
    // to true. (The notation []k indicates an array type of k dimensions.)
    if (!t.typeSpecification.bindings.isEmpty()
        && s.typeSpecification.nDims != 0
        && s.typeSpecification.nDims == t.typeSpecification.nDims
        && reparameterizeWithBounds(t).assignableFrom(s) == Cast.DISJOINT
        && t.toErasedType().assignableFrom(s) == Cast.CONFIRM_SAFE) {
      out.add(ConstConstraintFormula.TRUE);
      return;
    }

    // Otherwise, the constraint reduces to ‹S <: T›.
    out.add(new RelConstraintFormula(
        NominalType.from(s), NominalType.from(t)));
  }

  private static StaticType reparameterizeWithBounds(StaticType t) {
    throw new Error("TODO " + t);
  }
}

final class RelConstraintFormula extends ConstraintFormula {
  final SyntheticType s;
  final SyntheticType t;

  RelConstraintFormula(SyntheticType s, SyntheticType t) {
    this.s = s;
    this.t = t;
  }

  @Override
  public String toString() {
    return "<" + s + " <: " + t + ")";
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((s == null) ? 0 : s.hashCode());
    result = prime * result + ((t == null) ? 0 : t.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (getClass() != obj.getClass()) {
      return false;
    }
    RelConstraintFormula that = (RelConstraintFormula) obj;
    return this.s.equals(that.s)
        && this.t.equals(that.t);
  }


  @Override
  boolean inSimplestForm() {
    return false;
  }


  @Override
  void reduce(
      TypePool thetaPool, Logger logger, UncheckedConversionCallback ucc,
      Collection<? super ConstraintFormula> out) {
    // https://docs.oracle.com/javase/specs/jls/se8/html/jls-18.html#jls-18.2.3
    // A constraint formula of the form ‹S <: T› is reduced as follows:

    if (s instanceof NominalType && t instanceof NominalType) {
      StaticType st = ((NominalType) s).t;
      StaticType tt = ((NominalType) t).t;
      // If S and T are proper types, the constraint reduces to true if S is a
      // subtype of T (S4.10), and false otherwise.
      if (InferenceVariable.isProperType(st)
          && InferenceVariable.isProperType(tt)) {
        Cast c = tt.assignableFrom(st);
        if (c == Cast.CONFIRM_UNCHECKED) {
          ucc.uncheckedConversionOccurred(
              st.typeSpecification, tt.typeSpecification);
        }
        out.add(
            (c == Cast.CONFIRM_SAFE || c == Cast.SAME
            || c == Cast.CONFIRM_UNCHECKED)
            ? ConstConstraintFormula.TRUE
            : ConstConstraintFormula.FALSE);
        return;
      }

      // Otherwise, if S is the null type, the constraint reduces to true.
      if (thetaPool.T_NULL.equals(st)) {
        out.add(ConstConstraintFormula.TRUE);
        return;
      }

      // Otherwise, if T is the null type, the constraint reduces to false.
      if (thetaPool.T_NULL.equals(tt)) {
        out.add(ConstConstraintFormula.FALSE);
        return;
      }
    }

    SyntheticType sv = maybeAsInferenceVariable(s);
    // Otherwise, if S is an inference variable, α, the constraint reduces to
    // the bound α <: T.
    if (sv instanceof InferenceVariable) {
      out.add(new SimpleBound(
          sv, SimpleBound.Operator.UPPER, maybeAsInferenceVariable(t)));
      return;
    }

    SyntheticType tv = maybeAsInferenceVariable(t);
    // Otherwise, if T is an inference variable, α, the constraint reduces to
    // the bound S <: α.
    if (tv instanceof InferenceVariable) {
      out.add(new SimpleBound(s, SimpleBound.Operator.UPPER, tv));
      return;
    }

    // Otherwise, the constraint is reduced according to the form of T:

    // If T is a parameterized class or interface type, or an inner class type
    // of a parameterized class or interface type (directly or indirectly),
    // let A1, ..., An be the type arguments of T. Among the supertypes of S,
    // a corresponding class or interface type is identified, with type
    // arguments B1, ..., Bn. If no such type exists, the constraint reduces
    // to false. Otherwise, the constraint reduces to the following new
    // constraints: for all i (1 ≤ i ≤ n), ‹Bi <= Ai›.

    // If T is any other class or interface type, then the constraint reduces
    // to true if T is among the supertypes of S, and false otherwise.

    // If T is an array type, T'[], then among the supertypes of S that are
    // array types, a most specific type is identified, S'[] (this may be S
    // itself). If no such array type exists, the constraint reduces to false.
    // Otherwise:

    //   If neither S' nor T' is a primitive type, the constraint reduces to
    //   ‹S' <: T'›.

    //   Otherwise, the constraint reduces to true if S' and T' are the same
    //   primitive type, and false otherwise.

    // If T is a type variable, there are three cases:

    //   If S is an intersection type of which T is an element, the constraint
    //   reduces to true.

    //   Otherwise, if T has a lower bound, B, the constraint reduces to
    //   ‹S <: B›.

    //   Otherwise, the constraint reduces to false.

    // If T is an intersection type, I1 & ... & In, the constraint reduces to
    // the following new constraints: for all i (1 ≤ i ≤ n), ‹S <: Ii›.


    // TODO
    System.err.println("s=" + s + " : " + s.getClass());
    System.err.println("t=" + t + " : " + t.getClass());
    throw new Error(toString());
  }
}
