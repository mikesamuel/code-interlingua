package com.mikesamuel.cil.ast.j8.ti;

import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.mikesamuel.cil.ast.j8.ConditionalExpressionNode;
import com.mikesamuel.cil.ast.j8.ExpressionAtomNode;
import com.mikesamuel.cil.ast.j8.ExpressionNode;
import com.mikesamuel.cil.ast.j8.J8BaseNode;
import com.mikesamuel.cil.ast.j8.J8Chapter;
import com.mikesamuel.cil.ast.j8.J8NodeType;
import com.mikesamuel.cil.ast.j8.PrimaryNode;
import com.mikesamuel.cil.ast.meta.CallableInfo;
import com.mikesamuel.cil.ast.meta.JavaLang;
import com.mikesamuel.cil.ast.meta.Name;
import com.mikesamuel.cil.ast.meta.StaticType;
import com.mikesamuel.cil.ast.meta.StaticType.TypePool;
import com.mikesamuel.cil.ast.meta.StaticType.TypePool.ReferenceType;
import com.mikesamuel.cil.ast.meta.TypeInfo;
import com.mikesamuel.cil.ast.meta.TypeSpecification;
import com.mikesamuel.cil.ast.passes.AbstractRewritingPass.Parent;
import com.mikesamuel.cil.parser.SList;
import com.mikesamuel.cil.parser.SourcePosition;
import com.mikesamuel.cil.util.LogUtils;

/**
 * Infers type parameters for generic method invocations that lack them and for
 * constructor invocations that use the diamond operator.
 *
 * @see
 * <a href="https://docs.oracle.com/javase/specs/jls/se8/html/jls-18.html#jls-18.5.2">
 *   18.5.2 Invocation Type Inference
 * </a>
 */
public final class InvocationTypeInference {
  final Logger logger;
  final StaticType.TypePool typePool;
  final CallableInfo callee;
  final SourcePosition callPosition;
  final ImmutableList<ExpressionNode> actuals;
  final ImmutableList<SList<Parent>> pathsToActuals;
  final boolean isInvocationPolyExpression;

  /** */
  public InvocationTypeInference(
      Logger logger,
      StaticType.TypePool typePool,
      CallableInfo callee,
      SourcePosition callPosition,
      ImmutableList<SList<Parent>> pathsToActuals,
      boolean isInvocationPolyExpression) {
    this.logger = logger;
    this.typePool = typePool;
    this.callee = callee;
    this.callPosition = callPosition;
    this.pathsToActuals = pathsToActuals;
    this.isInvocationPolyExpression = isInvocationPolyExpression;

    ImmutableList.Builder<ExpressionNode> b = ImmutableList.builder();
    for (SList<Parent> pathToActual : this.pathsToActuals) {
      b.add((ExpressionNode) pathToActual.x.get());
    }
    this.actuals = b.build();
  }

  // These names are specified in 18.5.2
  /** An initial loose name assignment of parameter names to . */
  private Theta theta;
  /**
   * For each actual, ei, formalTypes[i] is the formal type corresponding to
   * that actual.
   */
  private ImmutableList<StaticType> formalTypes;
  /**
   * For each actual, ei, formalTypesCrossTheta[i] is the formal type
   * corresponding to that actual after replacing type parameters per theta.
   * If the actual is implicitly included into an array as part of a varargs
   * call, the formal type is the element type of the constructed array.
   */
  private ImmutableList<StaticType> formalTypesCrossTheta;
  /**
   * isActualPertinentToApplicability[i] is true when ei is pertinent to
   * applicability.
   */
  private boolean[] isActualPertinentToApplicability;
  /**
   * A type pool that resolves inference variables as bounded by the
   * type parameters they stand in for.
   */
  private TypePool thetaTypePool;
  /** Bounds inferred when computing applicability. */
  private BoundSet b2;
  /** Takes into account poly-expression context if any */
  private BoundSet b3;
  /** Computed from C and b3. */
  private BoundSet b4;
  /** True if an unchecked conversion occurred when checking applicability. */
  private boolean applicabilityRequiredUncheckedConversion;

  boolean isInvocationApplicable() {
    this.b2 = null;  // Set on success.

    // Let theta be the substitution [P1:=alpha_1, ..., Pp:=alpha_p] defined in
    // section 18.5.1 to replace the type parameters of m with inference
    // variables.
    {
      ImmutableMap.Builder<Name, InferenceVariable> b = ImmutableMap.builder();
      for (int i = 0, n = callee.typeParameters.size(); i < n; ++i) {
        Name typeParameter = callee.typeParameters.get(i);
        InferenceVariable iv = new InferenceVariable(i);
        b.put(typeParameter, iv);
      }
      theta = new Theta(b.build());
    }
    this.thetaTypePool = new TypePool(new ThetaTypeInfoResolver(
        theta, typePool.r));
    System.err.println("theta=" + theta);

    // An initial bound set, B0, is constructed from the declared bounds of
    // P1, ..., Pp, as described in section 18.1.3.
    BoundSet b0;
    {
      ImmutableSet.Builder<SimpleBound> simpleBounds =
          ImmutableSet.builder();
      for (Name typeParameter : callee.typeParameters) {
        Optional<TypeInfo> tiOpt = typePool.r.resolve(typeParameter);
        List<SyntheticType> upperTypeBounds = Lists.newArrayList();
        if (tiOpt.isPresent()) {
          TypeInfo ti = tiOpt.get();
          if (ti.superType.isPresent()) {
            upperTypeBounds.add(NominalType.from(
                thetaTypePool.type(ti.superType.get(), null, logger)));
          }
          for (TypeSpecification iface : ti.interfaces) {
            upperTypeBounds.add(NominalType.from(
                thetaTypePool.type(iface, null, logger)));
          }
        } else {
          LogUtils.log(
              logger, Level.SEVERE, null,
              "Missing type info for " + typeParameter, null);
        }
        if (upperTypeBounds.isEmpty()) {
          upperTypeBounds.add(NominalType.from(
              thetaTypePool.type(JavaLang.JAVA_LANG_OBJECT, null, logger)));
        }
        simpleBounds.add(new SimpleBound(
            theta.theta.get(typeParameter),
            SimpleBound.Operator.UPPER,
            TypeIntersection.of(upperTypeBounds)));
      }
      b0 = new BoundSet(
        simpleBounds.build(), true, ImmutableSet.of());
    }
    System.err.println("b0=" + b0);

    // For all i (1 <= i <= p), if Pi appears in the throws clause of m, then
    // the bound throws alpha_i is implied.
    // These bounds, if any, are incorporated with B0 to produce a new bound
    // set, B1.
    BoundSet b1;
    {
      ImmutableSet.Builder<InferenceVariable> b = ImmutableSet.builder();
      for (TypeSpecification thrown : callee.getThrownTypes()) {
        if (thrown.rawName.type == Name.Type.TYPE_PARAMETER
            && thrown.rawName.parent.equals(this.callee.canonName)) {
          b.add(Preconditions.checkNotNull(theta.theta.get(thrown.rawName)));
        }
      }
      ImmutableSet<InferenceVariable> typeParametersThrown = b.build();
      b1 = b0.withThrown(typeParametersThrown);
    }
    System.err.println("b1=" + b1);

    // Pertinent to applicability checks for all actuals occur multiple times.
    // Cache them.
    this.isActualPertinentToApplicability = new boolean[actuals.size()];
    for (int i = 0, n = actuals.size(); i < n; ++i) {
      this.isActualPertinentToApplicability[i] = isPertinentToApplicability(
          actuals.get(i));
    }

    System.err.println("Trying by strict");
    Optional<ImmutableList<TypeSpecification>> formalTypesOpt;
    formalTypesOpt = applicableByStrictInvocation(
        callee.getFormalTypes(), actuals);
    if (!formalTypesOpt.isPresent()) {
      System.err.println("Trying by loose");
      formalTypesOpt = applicableByLooseInvocation(
          callee.getFormalTypes(), actuals);
    }
    if (!formalTypesOpt.isPresent()) {
      System.err.println("Trying by variable arity");
      formalTypesOpt = applicableByVariableArityInvocation(
          callee.getFormalTypes(), actuals);
    }
    ConstraintFormulaSet c = null;
    if (formalTypesOpt.isPresent()) {
      ImmutableList<TypeSpecification> f = formalTypesOpt.get();
      ImmutableList.Builder<StaticType> ft = ImmutableList.builder();
      ImmutableList.Builder<StaticType> fCrossTheta =
          ImmutableList.builder();
      ImmutableList.Builder<ConstraintFormula> formulae =
          ImmutableList.builder();
      for (int i = 0, n = f.size(); i < n; ++i) {
        ExpressionNode ei = actuals.get(i);
        TypeSpecification fi = f.get(i);
        SourcePosition pos = ei.getSourcePosition();
        TypeSpecification fiCrossTheta = theta.cross(fi);
        ft.add(typePool.type(fi, pos, logger));
        fCrossTheta.add(thetaTypePool.type(fiCrossTheta, pos, logger));
        if (this.isActualPertinentToApplicability[i]) {
          ArgConstraintFormula formula = new ArgConstraintFormula(
              pathsToActuals.get(i), ei, fiCrossTheta);
          formulae.add(formula);
        }
      }
      c = new ConstraintFormulaSet(formulae.build());
      this.formalTypes = ft.build();
      this.formalTypesCrossTheta = fCrossTheta.build();
    }
    System.err.println("c=" + c);

    if (c != null) {
      // applicability checks populate this for later use
      Preconditions.checkState(
          this.formalTypesCrossTheta != null
          && this.formalTypesCrossTheta.size() == actuals.size()
          && !this.formalTypesCrossTheta.contains(null));

      final class UncheckedConversionCallbackImpl
      implements UncheckedConversionCallback {
        final List<TypeSpecification> convs = Lists.newArrayList();

        @Override
        public void uncheckedConversionOccurred(
            TypeSpecification s, TypeSpecification t) {
          convs.add(s);
          convs.add(t);
        }
      }

      UncheckedConversionCallbackImpl ucc =
          new UncheckedConversionCallbackImpl();
      BoundSet cBounds = c.reduce(thetaTypePool, logger, ucc);
      System.err.println("cBounds=" + cBounds);
      this.b2 = b1.merge(cBounds);
      System.err.println("b2=" + b2);
      if (b2.isBoundable) {
        this.applicabilityRequiredUncheckedConversion = !ucc.convs.isEmpty();
        if (this.applicabilityRequiredUncheckedConversion) {
          StringBuilder message = new StringBuilder();
          message.append("Type argument inference required unchecked conversion");
          String sep = ": ";
          for (int i = 0, n = ucc.convs.size(); i < n; i += 2) {
            message.append(sep)
            .append(ucc.convs.get(i))
            .append(" -> ")
            .append(ucc.convs.get(i + 1));
            sep = ", ";
          }
          LogUtils.log(
              logger, Level.WARNING, callPosition,
              message.toString(), null);
        }

        return true;
      }
    }
    this.b2 = null;
    return false;
  }

  boolean isPertinentToApplicability(J8BaseNode exprNode) {
    J8BaseNode expr = exprNode;
    if (exprNode.getNChildren() == 1
        && exprNode.getNodeType() == J8NodeType.Expression) {
      expr = exprNode.getChild(0);
    }

    // https://docs.oracle.com/javase/specs/jls/se8/html/jls-15.html#jls-15.12.2.2
    // An argument expression is considered pertinent to applicability for a
    // potentially applicable method m unless it has one of the following
    // forms:
    //   * An implicitly typed lambda expression (Sec 15.27.1).
    if (exprNode.getVariant() == ExpressionNode.Variant.LambdaExpression) {
      return false;  // TODO implicitly typed or alternatives below.
      // TODO: a lambda is implicitly typed when its LambdaParameters node has
      // variant Identifier or InferredFormalParameterList.
    }
    //   * An inexact method reference expression (Sec 15.13.1).
    if (expr.getVariant() == PrimaryNode.Variant.MethodReference) {
      return false;  // TODO inexact or alternatives below
    }
    //   * If m is a generic method and the method invocation does not provide
    //     explicit type arguments, an explicitly typed lambda expression or
    //     an exact method reference expression for which the corresponding
    //     target type (as derived from the signature of m) is a type parameter
    //     of m.
    // TODO: See first two cases above.
    //   * An explicitly typed lambda expression whose body is an expression
    //     that is not pertinent to applicability.
    // TODO: See first case above.
    //   * An explicitly typed lambda expression whose body is a block, where
    //     at least one result expression is not pertinent to applicability.
    // TODO: See first case above.
    //   * A parenthesized expression (§15.8.5) whose contained expression is
    //     not pertinent to applicability.
    if (expr.getVariant() == ExpressionAtomNode.Variant.Parenthesized) {
      ExpressionNode nested = expr.firstChildWithType(ExpressionNode.class);
      return nested == null || isPertinentToApplicability(nested);
    }

    //   * A conditional expression (§15.25) whose second or third operand is
    //     not pertinent to applicability.
    if (expr instanceof ConditionalExpressionNode && 3 == expr.getNChildren()) {
      switch (((ConditionalExpressionNode) expr).getVariant()) {
        case ConditionalOrExpressionQmExpressionClnConditionalExpression:
        case ConditionalOrExpressionQmExpressionClnLambdaExpression:
          return isPertinentToApplicability(expr.getChild(1))
              || isPertinentToApplicability(expr.getChild(2));
        case ConditionalOrExpression:
          break;
      }
    }
    return true;  // See "unless" above.
  }


  Optional<ImmutableList<TypeSpecification>> applicableByStrictInvocation(
      ImmutableList<TypeSpecification> f, ImmutableList<ExpressionNode> e) {
    int n = f.size();
    if (n != e.size()) { return Optional.absent(); }

    ImmutableList.Builder<TypeSpecification> fs = ImmutableList.builder();

    //  If there exists an i (1 <= i <= n) such that ei is pertinent to
    // applicability (S15.12.2.2) and either
    //   i)  ei is a standalone expression of a primitive type but Fi is a
    //       reference type, or
    //   ii) Fi is a primitive type but ei is not a standalone expression
    //       of a primitive type;
    // then the method is not applicable and there is no need to proceed with
    // inference.
    for (int i = 0; i < n; ++i) {
      System.err.println("CP0 i=" + i);
      ExpressionNode ei = e.get(i);
      StaticType fi = typePool.type(f.get(i), null, logger);
      if (this.isActualPertinentToApplicability[i]) {
        System.err.println("\tei=" + ei);
        System.err.println("\tpertinent");
        System.err.println("\tei type=" + ei.getStaticType());
        System.err.println(
            "\tstandalone(ei)="
            + Polyexpressions.isStandaloneExpression(
                pathsToActuals.get(i)));
        System.err.println("\tfi=" + fi);
        boolean eIsStandalonePrimitive =
            ei.getStaticType() instanceof StaticType.PrimitiveType
            && Polyexpressions.isStandaloneExpression(pathsToActuals.get(i));
        System.err.println(
            "\teIsStandalonePrimitive=" + eIsStandalonePrimitive);
        boolean fIsReference = fi instanceof StaticType.TypePool.ReferenceType;
        System.err.println("\tfIsReference=" + fIsReference);
        if (eIsStandalonePrimitive == fIsReference) {
          System.err.println("\tnot strictly applicable");
          return Optional.absent();
        }

        // Otherwise, C includes, for all i (1 <= i <= k) where ei is pertinent to applicability,
        // <ei -> Fi theta>.
        // Handled below.  Later code filters by applicability check.
      }
      fs.add(fi.typeSpecification);
    }
    System.err.println("Returning strict applicability formulae");
    return Optional.of(fs.build());
  }

  Optional<ImmutableList<TypeSpecification>> applicableByLooseInvocation(
      ImmutableList<TypeSpecification> f, ImmutableList<ExpressionNode> e) {
    int n = f.size();
    if (n != e.size()) { return Optional.absent(); }

    ImmutableList.Builder<TypeSpecification> fs = ImmutableList.builder();
    for (int i = 0; i < n; ++i) {
      TypeSpecification fi = f.get(i);
      if (this.isActualPertinentToApplicability[i]) {
        // Otherwise, C includes, for all i (1 <= i <= k) where ei is pertinent to applicability,
        // <ei -> Fi theta>.
        // Handled later.
      }
      fs.add(fi);
    }
    return Optional.of(fs.build());
  }

  Optional<ImmutableList<TypeSpecification>>
  applicableByVariableArityInvocation(
      ImmutableList<TypeSpecification> f, ImmutableList<ExpressionNode> e) {
    int n = f.size();
    int k = e.size();
    if (!callee.isVariadic() || k < n - 1) { return Optional.absent(); }

    TypeSpecification restTypeSpec = f.get(n - 1);
    Preconditions.checkState(restTypeSpec.nDims > 0);
    restTypeSpec = restTypeSpec.withNDims(restTypeSpec.nDims - 1);

    ImmutableList.Builder<TypeSpecification> fs = ImmutableList.builder();
    for (int i = 0; i < k; ++i) {
      TypeSpecification fi = i < n - 1
          ? f.get(i)
          : restTypeSpec;
      fs.add(fi);
    }
    return Optional.of(fs.build());
  }

  /**
   * https://docs.oracle.com/javase/specs/jls/se8/html/jls-18.html#jls-18.5.2
   *
   * Returns a mapping from type parameters to resolved types.
   */
  Inferences inferTypeVariables() {
    // Make sure b2 is populated.
    Preconditions.checkState(isInvocationApplicable());
    if (!isInvocationPolyExpression) {
      // If the invocation is not a poly expression, let the bound set B3 be
      // the same as B2.
      this.b3 = b2;
    } else {
      this.b3 = computePolyB3();
    }

    // A set of constraint formulas, C, is constructed as follows.

    // Let e1, ..., ek be the actual argument expressions of the invocation.
    // If m is applicable by strict or loose invocation, let F1, ..., Fk be
    // the formal parameter types of m; if m is applicable by variable arity
    // invocation, let F1, ..., Fk the first k variable arity parameter types
    // of m (S15.12.2.4). Then:
    ImmutableList.Builder<ConstraintFormula> formulae =
        ImmutableList.builder();
    // For all i (1 ≤ i ≤ k), if ei is not pertinent to applicability,
    // C contains ‹ei → Fi θ›.
    for (int i = 0, n = actuals.size(); i < n; ++i) {
      if (!isActualPertinentToApplicability[i]) {
        formulae.add(new ArgConstraintFormula(
            pathsToActuals.get(i), actuals.get(i),
            formalTypesCrossTheta.get(i).typeSpecification));
      }
    }

    // For all i (1 ≤ i ≤ k), additional constraints may be included,
    // depending on the form of ei:
    for (int i = 0, n = actuals.size(); i < n; ++i) {
      addAdditionalPolyConstraints(pathsToActuals.get(i), formulae);
    }
    ConstraintFormulaSet c = new ConstraintFormulaSet(formulae.build());

    System.err.println("b3=" + b3);
    System.err.println("c=" + c);

    // While C is not empty, the following process is repeated, starting with
    // the bound set B3 and accumulating new bounds into a "current" bound set,
    // ultimately producing a new bound set, B4:
    BoundSet current = b3;
    while (!c.formulae.isEmpty()) {
      BoundSet fromC = null;
      // A subset of constraints is selected in C, satisfying the property
      // that, for each
      // ...
      UncheckedConversionCallback ignUcc = new UncheckedConversionCallback() {
        @Override
        public void uncheckedConversionOccurred(
            TypeSpecification s, TypeSpecification t) {
          // Ignore
        }
      };
      fromC = c.reduce(thetaTypePool, logger, ignUcc);
      current = current.merge(fromC);
      throw new Error("TODO");
    }

    this.b4 = current;
    System.err.println("b4=" + b4);
    if (b4.isBoundable) {
      // Finally, if B4 does not contain the bound false, the inference
      // variables in B4 are resolved.

      // If resolution succeeds with instantiations T1, ..., Tp for inference
      // variables α1, ..., αp, let θ' be the substitution
      // [P1:=T1, ..., Pp:=Tp].

      b4 = b4.resolve(thetaTypePool);
      Optional<ImmutableMap<InferenceVariable, ReferenceType>> resolutions =
          b4.getResolutions();
      // TODO: check that we have resolutions for all variables we care
      // about.
      if (resolutions.isPresent()) {
        ImmutableMap<InferenceVariable, ReferenceType> thetaPrime =
            resolutions.get();

        StaticType resultType;
        ImmutableList.Builder<StaticType> thrownTypes =
            ImmutableList.builder();
        ImmutableMap.Builder<Name, StaticType> exportableResolutions =
            ImmutableMap.builder();

        // If unchecked conversion was necessary for the method to be
        // applicable during constraint set reduction in §18.5.1, then the
        // parameter types of the invocation type of m are obtained by
        // applying θ' to the parameter types of m's type, and the return
        // type and thrown types of the invocation type of m are given by the
        // erasure of the return type and thrown types of m's type.
        // Then:
        boolean erase = this.applicabilityRequiredUncheckedConversion;

        // If unchecked conversion was not necessary for the method to be
        // applicable, then the invocation type of m is obtained by applying
        // θ' to the type of m.
        resultType = exportable(
            ((NominalType) NominalType
                .from(thetaTypePool.type(
                    theta.cross(callee.getReturnType()), null, null))
                .subst(thetaPrime))
            .t);
        if (erase) { resultType = resultType.toErasedType(); }
        for (TypeSpecification thrown : callee.getThrownTypes()) {
          StaticType resolvedThrown = ((NominalType) NominalType
              .from(thetaTypePool.type(
                  theta.cross(thrown), null, null))
              .subst(thetaPrime))
              .t;
          if (erase) { resolvedThrown = resolvedThrown.toErasedType(); }
          thrownTypes.add(exportable(resolvedThrown));
        }

        for (Map.Entry<InferenceVariable, ReferenceType> e
             : thetaPrime.entrySet()) {
          exportableResolutions.put(
              theta.reverse.get(e.getKey()),
              exportable(e.getValue()));
        }

        return new Inferences(
            this.applicabilityRequiredUncheckedConversion,
            exportableResolutions.build(),
            resultType,
            thrownTypes.build()
            );
      }
    }

    // If B4 contains the bound false, or if resolution fails, then a
    // compile-time error occurs.
    LogUtils.log(
        logger, Level.SEVERE, callPosition, "Failed to infer types", null);
    ImmutableMap.Builder<Name, StaticType> b =
        ImmutableMap.<Name, StaticType>builder();
    for (Name typeParameter : theta.theta.keySet()) {
      b.put(typeParameter, StaticType.ERROR_TYPE);
    }
    return new Inferences(
        false,
        b.build(),
        StaticType.ERROR_TYPE,
        ImmutableList.<StaticType>of());
  }

  private BoundSet computePolyB3() {
    // https://docs.oracle.com/javase/specs/jls/se8/html/jls-18.html#jls-18.5.2
    // If the invocation is a poly expression, let the bound set B3 be derived
    // from B2 as follows.
    // ...
    System.err.println(b2);
    throw new Error("TODO");
  }

  private void addAdditionalPolyConstraints(
      SList<Parent> toEi,
      ImmutableList.Builder<? super ConstraintFormula> out) {
    J8BaseNode ei = toEi.x.get();
    Preconditions.checkArgument(
        ei.getNodeType().getChapter() == J8Chapter.Expression);

    if (ei.getVariant() == ExpressionNode.Variant.ConditionalExpression
        && ei.getNChildren() == 0) {
      J8BaseNode child = ei.getChild(0);
      J8NodeType nt = child.getNodeType();
      if (nt == J8NodeType.Primary || nt == J8NodeType.ExpressionAtom) {
        addAdditionalPolyConstraints(
            SList.append(toEi, new Parent(0, (ExpressionNode) ei)),
            out);
        return;
      }
    }

    // If ei is a LambdaExpression, C contains ‹LambdaExpression →throws Fi θ›.
    if (ei.getVariant() == ExpressionNode.Variant.LambdaExpression) {
      // PUNT for now
      throw new Error("TODO " + ei);
    }

    // If ei is a MethodReference, C contains ‹MethodReference →throws Fi θ›.
    if (ei.getVariant() == PrimaryNode.Variant.MethodReference) {
      // PUNT for now
      throw new Error("TODO " + ei);
    }

    // If ei is a poly class instance creation expression (§15.9) or a poly
    // method invocation expression (§15.12), C contains all the constraint
    // formulas that would appear in the set C generated by §18.5.2 when
    // inferring the poly expression's invocation type.
    if ((ei.getVariant() == PrimaryNode.Variant.MethodInvocation
        || ei.getVariant() == PrimaryNode.Variant.InnerClassCreation
        || ei.getVariant() == ExpressionAtomNode.Variant.MethodInvocation
        || ei.getVariant() == ExpressionAtomNode.Variant
            .UnqualifiedClassInstanceCreationExpression)
        && Polyexpressions.isPolyExpression(toEi)) {
      throw new Error("TODO " + ei);
    }

    // If ei is a parenthesized expression, these rules are applied recursively
    // to the contained expression.
    if (ei.getVariant() == ExpressionAtomNode.Variant.Parenthesized) {
      addAdditionalPolyConstraints(
          SList.append(
              toEi, new Parent(0, (ExpressionAtomNode) ei)),
          out);
      return;
    }

    // If ei is a conditional expression, these rules are applied recursively
    // to the second and third operands.
    if (ei.getNodeType() == J8NodeType.ConditionalExpression
        && ei.getNChildren() == 3) {
      ConditionalExpressionNode cei = (ConditionalExpressionNode) ei;
      SList<Parent> toOperand1 = SList.append(
          toEi, new Parent(1, cei));
      SList<Parent> toOperand2 = SList.append(
          toEi, new Parent(2, cei));
      addAdditionalPolyConstraints(toOperand1, out);
      addAdditionalPolyConstraints(toOperand2, out);
      return;
    }
  }

  /** Make sure that we export types that use typePool, not thetaTypePool. */
  private StaticType exportable(StaticType t) {
    return typePool.type(t.typeSpecification, null, logger);
  }
}
