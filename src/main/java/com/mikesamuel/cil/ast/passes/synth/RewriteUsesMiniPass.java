package com.mikesamuel.cil.ast.passes.synth;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

import javax.annotation.Nullable;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.mikesamuel.cil.ast.j8.AdditiveExpressionNode;
import com.mikesamuel.cil.ast.j8.AdditiveOperatorNode;
import com.mikesamuel.cil.ast.j8.ArgumentListNode;
import com.mikesamuel.cil.ast.j8.AssignmentNode;
import com.mikesamuel.cil.ast.j8.AssignmentOperatorNode;
import com.mikesamuel.cil.ast.j8.CastExpressionNode;
import com.mikesamuel.cil.ast.j8.CastNode;
import com.mikesamuel.cil.ast.j8.ClassBodyNode;
import com.mikesamuel.cil.ast.j8.ConvertCastNode;
import com.mikesamuel.cil.ast.j8.ExpressionAtomNode;
import com.mikesamuel.cil.ast.j8.ExpressionNode;
import com.mikesamuel.cil.ast.j8.FieldNameNode;
import com.mikesamuel.cil.ast.j8.IdentifierNode;
import com.mikesamuel.cil.ast.j8.IncrDecrOperatorNode;
import com.mikesamuel.cil.ast.j8.IntegerLiteralNode;
import com.mikesamuel.cil.ast.j8.J8BaseInnerNode;
import com.mikesamuel.cil.ast.j8.J8BaseNode;
import com.mikesamuel.cil.ast.j8.J8FileNode;
import com.mikesamuel.cil.ast.j8.J8NodeType;
import com.mikesamuel.cil.ast.j8.J8NodeVariant;
import com.mikesamuel.cil.ast.j8.J8Typed;
import com.mikesamuel.cil.ast.j8.LeftHandSideNode;
import com.mikesamuel.cil.ast.j8.LiteralNode;
import com.mikesamuel.cil.ast.j8.LocalNameNode;
import com.mikesamuel.cil.ast.j8.MethodInvocationNode;
import com.mikesamuel.cil.ast.j8.MethodNameNode;
import com.mikesamuel.cil.ast.j8.PrimaryNode;
import com.mikesamuel.cil.ast.j8.StatementExpressionNode;
import com.mikesamuel.cil.ast.j8.UnaryExpressionNode;
import com.mikesamuel.cil.ast.j8.UnqualifiedClassInstanceCreationExpressionNode;
import com.mikesamuel.cil.ast.meta.JavaLang;
import com.mikesamuel.cil.ast.meta.Name;
import com.mikesamuel.cil.ast.meta.StaticType;
import com.mikesamuel.cil.ast.meta.StaticType.PrimitiveType;
import com.mikesamuel.cil.ast.meta.TypeSpecification;
import com.mikesamuel.cil.ast.passes.AbstractRewritingPass.Parent;
import com.mikesamuel.cil.ast.passes.ComplexAssignments;
import com.mikesamuel.cil.ast.passes.Temporaries;
import com.mikesamuel.cil.ast.passes.TypeNodeFactory;
import com.mikesamuel.cil.parser.SList;

final class RewriteUsesMiniPass extends PerUseAbstractMiniPass {
  private final List<UseInContext> usesInContext = new ArrayList<>();
  private final List<Temporary> temporaries = new ArrayList<>();

  RewriteUsesMiniPass(Common c) {
    super(c);
  }

  @Override
  void processUse(
      UserDefinedType udt,
      J8BaseNode node, @Nullable SList<Parent> pathFromRoot,
      Name nm, EnumSet<UseType> ts) {
    usesInContext.add(new UseInContext(udt, node, pathFromRoot, nm, ts));
  }

  @Override
  void run(ImmutableList<J8FileNode> files) {
    this.usesInContext.clear();
    this.temporaries.clear();
    super.run(files);
    // Sort uses in context by depth so we can process the deepest ones
    // first.
    Collections.sort(usesInContext);
    for (UseInContext uic : usesInContext) {
      if (!rewriteUse(uic)) {
        c.error(uic.node, "Failed to rewrite " + uic.used + " to use accessor");
      }
    }
    new Temporaries(c.logger, c.typePool)
        .declareTemporaries(temporaries);
    this.usesInContext.clear();
    this.temporaries.clear();
  }

  private boolean rewriteUse(UseInContext u) {
    UserDefinedType declarer = c.byName.get(u.used.getContainingClass());
    if (declarer == null) {
      return false;
    }
    if (u.ts.contains(UseType.WRITE_OF_PRIVATE)) {
      Accessor wacc = declarer.allocatedAccessors.get(
          new Use(UseType.WRITE_OF_PRIVATE, u.used));
      if (wacc == null) {
        return false;
      }
      Accessor racc = declarer.allocatedAccessors.get(
          new Use(UseType.READ_OF_PRIVATE, u.used));
      return rewriteWrite(u, declarer, wacc, racc);
    } else if (u.ts.contains(UseType.READ_OF_PRIVATE)) {
      Accessor acc = declarer.allocatedAccessors.get(
          new Use(UseType.READ_OF_PRIVATE, u.used));
      if (acc == null) {
        return false;
      }
      if (u.node instanceof FieldNameNode
          && u.pathFromRoot != null
          && u.pathFromRoot.prev != null) {
        J8BaseNode parent = u.pathFromRoot.x.parent;
        J8NodeVariant parentv = parent.getVariant();
        J8BaseInnerNode replacement = null;
        if (parentv == ExpressionAtomNode.Variant.FreeField) {
          replacement = ExpressionAtomNode.Variant.MethodInvocation
              .buildNode(
                  MethodNameNode.Variant.Identifier.buildNode(
                      IdentifierNode.Variant.Builtin.buildNode(
                          acc.allocatedName)));
        } else if (parentv == PrimaryNode.Variant.FieldAccess) {
          replacement = PrimaryNode.Variant.MethodInvocation
              .buildNode(u.pathFromRoot.x.parent.getChildren());
          replacement.replace(
              u.pathFromRoot.x.indexInParent,
              MethodNameNode.Variant.Identifier.buildNode(
                  IdentifierNode.Variant.Builtin.buildNode(
                      acc.allocatedName)));
        }
        if (replacement != null) {
          SList<Parent> gp = Preconditions.checkNotNull(u.pathFromRoot.prev);
          gp.x.parent.replace(gp.x.indexInParent, replacement);
          return true;
        }
      }
    } else if (u.ts.contains(UseType.SUPER_INVOKE)) {
      if (u.node.getVariant() != PrimaryNode.Variant.MethodInvocation) {
        return false;
      }
      ExpressionAtomNode callee = u.node.firstChildWithType(
          ExpressionAtomNode.class);
      MethodNameNode nameNode = u.node
          .firstChildWithType(MethodNameNode.class);
      IdentifierNode nameIdNode = nameNode != null
          ? nameNode.firstChildWithType(IdentifierNode.class)
          : null;
      if (callee == null
          || nameIdNode == null
          || callee.getVariant() != ExpressionAtomNode.Variant.Super) {
        return false;
      }
      Accessor acc = declarer.allocatedAccessors.get(
          new Use(UseType.SUPER_INVOKE, u.used));
      if (acc == null) { return false; }
      // We have an explicit invocation of the super method.
      // Change super. to this.
      callee.setVariant(ExpressionAtomNode.Variant.This);
      nameIdNode.setValue(acc.allocatedName);
      return true;
    } else if (u.ts.contains(UseType.INVOKE_OF_PRIVATE)) {
      Accessor acc = declarer.allocatedAccessors.get(
          new Use(UseType.INVOKE_OF_PRIVATE, u.used));
      if (acc == null) { return false; }
      if (u.node instanceof MethodNameNode) {
        // Just rewrite the name.
        IdentifierNode id = u.node.firstChildWithType(IdentifierNode.class);
        if (id != null) {
          id.setValue(acc.allocatedName);
          return true;
        }
      } else if (u.node
                 instanceof UnqualifiedClassInstanceCreationExpressionNode) {
        // Add disambiguating arguments
        ArgumentListNode args = u.node.firstChildWithType(
            ArgumentListNode.class);
        if (args == null) {
          int beforeBody = u.node.finder(ClassBodyNode.class).indexOf();
          if (beforeBody < 0) { beforeBody = u.node.getNChildren(); }
          args = ArgumentListNode.Variant.ExpressionComExpression
              .buildNode();
          ((J8BaseInnerNode) u.node).add(beforeBody, args);
        }
        for (int i = 0; i < acc.disambiguatingParameters; ++i) {
          TypeSpecification ts = acc.parameterTypes.get(i);
          args.add(i, TypeNodeFactory.zeroValueFor(ts));
        }
        return true;
      }
    }
    return false;
  }


  private boolean rewriteWrite(
      UseInContext u, UserDefinedType declarer,
      Accessor writeAccessor, @Nullable Accessor readAccessor) {
    SList<Parent> anc = u.pathFromRoot;
    if (anc == null) { return false; }
    // Find the expression that specifies the object or type that contains
    // the field.
    J8Typed container = null;
    {
      J8NodeVariant v = anc.x.parent.getVariant();
      if (v == PrimaryNode.Variant.FieldAccess) {
        container = anc.x.parent.firstChildWithType(PrimaryNode.class);
        if (container == null) {
          container = anc.x.parent
              .firstChildWithType(ExpressionAtomNode.class);
        }
      } else if (v == ExpressionAtomNode.Variant.FreeField) {
        container =
            (Modifier.isStatic(writeAccessor.accessee.modifiers)
                ? ExpressionAtomNode.Variant.StaticMember
                : ExpressionAtomNode.Variant.This)
            .buildNode(c.factory.toTypeNameNode(declarer.ti));
      }
    }
    if (container == null) { return false; }

    anc = anc.prev;
    if (anc == null || anc.x.parent.getNodeType() != J8NodeType.LeftHandSide) {
      return false;
    }

    anc = anc.prev;
    if (anc == null) { return false; }

    StaticType readAccessorResultType = readAccessor != null
        ? c.typePool.type(readAccessor.resultType, u.node, c.logger)
        : null;

    J8NodeType assNt = anc.x.parent.getNodeType();
    // Any temporary we need to avoid repeated execution of container.
    ReuseStrategy rus;
    // The right hand side to assign.
    ExpressionNode rhs = null;
    // Any adjustment that needs to happen after the fact to account for
    // the difference between pre and post increment.
    // Ignored if used in a statement context.
    AdditiveOperatorNode.Variant adjustment = null;
    if (assNt == J8NodeType.PreExpression
        || assNt == J8NodeType.PostExpression) {
      boolean isPre = assNt == J8NodeType.PreExpression;
      IncrDecrOperatorNode op = anc.x.parent.firstChildWithType(
          IncrDecrOperatorNode.class);
      if (op == null || readAccessor == null) { return false; }
      AdditiveOperatorNode.Variant opv = null;
      switch (op.getVariant()) {
        case DshDsh:
          opv = AdditiveOperatorNode.Variant.Dsh;
          if (!isPre) {
            adjustment = AdditiveOperatorNode.Variant.Pls;
          }
          break;
        case PlsPls:
          opv = AdditiveOperatorNode.Variant.Pls;
          if (!isPre) {
            adjustment = AdditiveOperatorNode.Variant.Dsh;
          }
          break;
      }
      Preconditions.checkNotNull(opv);
      rus = maybeAllocateTemporary(anc, container);
      rhs = ExpressionNode.Variant.ConditionalExpression.buildNode(
          maybeAdjust(
              PrimaryNode.Variant.MethodInvocation.buildNode(
                  (J8BaseNode) rus.subsequentUseOr(container),
                  MethodNameNode.Variant.Identifier.buildNode(
                      IdentifierNode.Variant.Builtin.buildNode(
                          readAccessor.allocatedName)))
              .setStaticType(readAccessorResultType),
              opv));
    } else if (assNt == J8NodeType.Assignment) {
      AssignmentOperatorNode op = anc.x.parent.firstChildWithType(
          AssignmentOperatorNode.class);
      ExpressionNode rightOperand = anc.x.parent.firstChildWithType(
          ExpressionNode.class);
      if (op == null || rightOperand == null) { return false; }
      AssignmentOperatorNode.Variant opv = op.getVariant();
      if (opv == AssignmentOperatorNode.Variant.Eq) {
        rus = ReusePureExpression.INSTANCE;
        rhs = rightOperand;
      } else {
        if (readAccessor == null) { return false; }
        rus = maybeAllocateTemporary(anc, container);
        ComplexAssignments.OpVariants copv =
            ComplexAssignments.ASSIGNMENT_OPERATOR_TO_BINARY_OPERATOR_VARIANT
            .get(opv);
        rhs = ExpressionNode.Variant.ConditionalExpression.buildNode(
            maybeCastBack(
                copv.buildNode(
                    PrimaryNode.Variant.MethodInvocation.buildNode(
                        (J8BaseNode) rus.subsequentUseOr(container),
                        MethodNameNode.Variant.Identifier.buildNode(
                            IdentifierNode.Variant.Builtin.buildNode(
                                readAccessor.allocatedName))),
                    ExpressionAtomNode.Variant.Parenthesized.buildNode(
                        rightOperand)),
                readAccessorResultType));
      }
    } else {
      return false;
    }
    if (rhs == null) { return false; }

    PrimaryNode writeCall = PrimaryNode.Variant.MethodInvocation.buildNode(
        (J8BaseNode) rus.initialUseOr(container),
        MethodNameNode.Variant.Identifier.buildNode(
            IdentifierNode.Variant.Builtin.buildNode(
                writeAccessor.allocatedName)),
        ArgumentListNode.Variant.ExpressionComExpression.buildNode(rhs));
    writeCall.setSourcePosition(u.node.getSourcePosition());
    writeCall.setStaticType(
        c.typePool.type(writeAccessor.resultType, u.node, c.logger));

    anc = anc.prev;
    if (anc == null) { return false; }
    J8NodeVariant ancV = anc.x.parent.getVariant();
    J8NodeType ancT = ancV.getNodeType();
    if (ancT == J8NodeType.StatementExpression) {
      StatementExpressionNode stmtExpr =
          (StatementExpressionNode) anc.x.parent;
      stmtExpr.setVariant(StatementExpressionNode.Variant.MethodInvocation);
      // Intentionally ignore adjustment.
      stmtExpr.replaceChildren(ImmutableList.of(
          MethodInvocationNode.Variant.ExplicitCallee.buildNode(
              writeCall)));
      return true;
    } else if (ancT == J8NodeType.UnaryExpression
        && (ancV == UnaryExpressionNode.Variant.PostExpression
           || ancV == UnaryExpressionNode.Variant.PreExpression)) {
      SList<Parent> ancp = anc.prev;
      if (ancp == null) { return false; }
      ancp.x.parent.replace(
          ancp.x.indexInParent,
          maybeAdjust(writeCall, adjustment));
      return true;
    } else if (ancT == J8NodeType.Expression
               && ancV == ExpressionNode.Variant.Assignment) {
      ((ExpressionNode) anc.x.parent).setVariant(
          ExpressionNode.Variant.ConditionalExpression);
      anc.x.parent.replace(
          anc.x.indexInParent,
          maybeAdjust(writeCall, adjustment));
      return true;
    }
    return false;
  }

  private static J8BaseNode maybeAdjust(
      PrimaryNode expr, AdditiveOperatorNode.Variant adj) {
    if (adj == null) { return expr; }
    J8BaseNode adjusted =
        ExpressionAtomNode.Variant.Parenthesized
        .buildNode(
            ExpressionNode.Variant.ConditionalExpression.buildNode(
                AdditiveExpressionNode.Variant
                .AdditiveExpressionAdditiveOperatorMultiplicativeExpression
                .buildNode(
                    expr,
                    adj.buildNode(),
                    ExpressionAtomNode.Variant.Literal.buildNode(
                        LiteralNode.Variant.IntegerLiteral.buildNode(
                            IntegerLiteralNode.Variant.Builtin.buildNode(
                                "1"))))));
    return maybeCastBack(adjusted, expr.getStaticType());
  }

  private static J8BaseNode maybeCastBack(
      J8BaseNode exprNode, StaticType resultType) {
    J8BaseNode expr = exprNode;
    Optional<PrimitiveType> resultPrimType = StaticType.maybeUnbox(resultType);
    if (resultPrimType.isPresent()
        && PROMOTE_TO_INT.contains(resultPrimType.get())) {
      switch (expr.getNodeType()) {
        case UnaryExpression:
        case Primary:
        case ExpressionAtom:
          break;
        case Expression:
          expr = ExpressionAtomNode.Variant.Parenthesized.buildNode(expr);
          break;
        default:
          expr = ExpressionAtomNode.Variant.Parenthesized.buildNode(
              ExpressionNode.Variant.ConditionalExpression.buildNode(expr));
          break;
      }
      return UnaryExpressionNode.Variant.CastExpression.buildNode(
          CastExpressionNode.Variant.Expression.buildNode(
              CastNode.Variant.ConvertCast.buildNode(
                  ConvertCastNode.Variant.PrimitiveType.buildNode(
                      TypeNodeFactory.toPrimitiveTypeNode(
                          resultPrimType.get()))),
              expr));
    }
    return expr;
  }

  private static final ImmutableSet<PrimitiveType> PROMOTE_TO_INT
      = ImmutableSet.of(
          StaticType.T_BYTE, StaticType.T_CHAR, StaticType.T_SHORT);

  ReuseStrategy maybeAllocateTemporary(
      SList<Parent> pathFromRoot, J8Typed initializer) {
    boolean isSimpleContainer =
        initializer.getVariant() == ExpressionAtomNode.Variant.StaticMember
        || initializer.getVariant() == ExpressionAtomNode.Variant.This;
    if (isSimpleContainer) {
      return ReusePureExpression.INSTANCE;
    }

    String allocatedName = c.nameAllocator.allocateIdentifier("tmp__");
    TypeSpecification type;
    StaticType st = initializer.getStaticType();
    if (st == null) {
      type = JavaLang.JAVA_LANG_OBJECT;
      c.error(initializer, "Could not determine type for temporary");
    } else {
      type = st.typeSpecification;
    }
    Temporary t = new Temporary(pathFromRoot, type, allocatedName, initializer);
    temporaries.add(t);
    return t;
  }


  static final class UseInContext implements Comparable<UseInContext> {
    final UserDefinedType udt;
    final J8BaseNode node;
    final @Nullable SList<Parent> pathFromRoot;
    final Name used;
    final ImmutableSet<UseType> ts;
    final int depth;

    UseInContext(
        UserDefinedType udt,
        J8BaseNode node, @Nullable SList<Parent> pathFromRoot,
        Name used, Iterable<UseType> ts) {
      this.udt = udt;
      this.node = node;
      this.pathFromRoot = pathFromRoot;
      this.used = used;
      this.ts = Sets.immutableEnumSet(ts);

      int d = 0;
      for (SList<Parent> anc = pathFromRoot; anc != null; anc = anc.prev) {
        ++d;
      }
      this.depth = d;
    }

    @Override
    public int compareTo(UseInContext o) {
      int delta = this.udt.ti.canonName.compareTo(o.udt.ti.canonName);
      if (delta != 0) { return delta; }
      delta = this.depth - o.depth;
      if (delta != 0) { return -delta; }
      delta = (pathFromRoot != null ? pathFromRoot.x.indexInParent : -1)
          - (o.pathFromRoot != null ? o.pathFromRoot.x.indexInParent : -1);
      return delta;
    }
  }
}

interface ReuseStrategy {
  J8Typed initialUseOr(J8Typed node);
  J8Typed subsequentUseOr(J8Typed node);
}

final class ReusePureExpression implements ReuseStrategy {
  private ReusePureExpression() {}

  static final ReusePureExpression INSTANCE = new ReusePureExpression();

  @Override
  public J8Typed initialUseOr(J8Typed node) {
    return node;
  }

  @Override
  public J8Typed subsequentUseOr(J8Typed node) {
    return (J8Typed) node.deepClone();
  }
}

final class Temporary extends Temporaries.Temporary implements ReuseStrategy {
  final ExpressionAtomNode initialUse;
  final ExpressionAtomNode subsequentUse;

  Temporary(
      SList<Parent> scope, TypeSpecification type, String allocatedName,
      J8Typed initializer) {
    super(type, allocatedName, scope);
    this.subsequentUse = ExpressionAtomNode.Variant.Local.buildNode(
        LocalNameNode.Variant.Identifier.buildNode(
            IdentifierNode.Variant.Builtin.buildNode(
                allocatedName)));
    this.initialUse = ExpressionAtomNode.Variant.Parenthesized.buildNode(
        ExpressionNode.Variant.Assignment.buildNode(
            AssignmentNode.Variant.LeftHandSideAssignmentOperatorExpression
            .buildNode(
                LeftHandSideNode.Variant.Local.buildNode(
                    subsequentUse.deepClone()),
                AssignmentOperatorNode.Variant.Eq.buildNode(),
                ExpressionNode.Variant.ConditionalExpression.buildNode(
                    initializer.deepClone()))));
  }

  @Override
  public J8Typed initialUseOr(J8Typed node) {
    return initialUse;
  }

  @Override
  public J8Typed subsequentUseOr(J8Typed node) {
    return this.subsequentUse.deepClone();
  }
}