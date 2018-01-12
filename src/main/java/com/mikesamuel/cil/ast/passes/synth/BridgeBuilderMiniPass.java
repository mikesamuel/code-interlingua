package com.mikesamuel.cil.ast.passes.synth;

import java.util.Collection;
import java.util.logging.Level;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Table;
import com.mikesamuel.cil.ast.j8.ArgumentListNode;
import com.mikesamuel.cil.ast.j8.BlockNode;
import com.mikesamuel.cil.ast.j8.BlockStatementNode;
import com.mikesamuel.cil.ast.j8.BlockStatementsNode;
import com.mikesamuel.cil.ast.j8.CastExpressionNode;
import com.mikesamuel.cil.ast.j8.CastNode;
import com.mikesamuel.cil.ast.j8.ConfirmCastNode;
import com.mikesamuel.cil.ast.j8.ConvertCastNode;
import com.mikesamuel.cil.ast.j8.ExpressionAtomNode;
import com.mikesamuel.cil.ast.j8.ExpressionNode;
import com.mikesamuel.cil.ast.j8.ExpressionStatementNode;
import com.mikesamuel.cil.ast.j8.FormalParameterListNode;
import com.mikesamuel.cil.ast.j8.FormalParameterNode;
import com.mikesamuel.cil.ast.j8.IdentifierNode;
import com.mikesamuel.cil.ast.j8.J8BaseInnerNode;
import com.mikesamuel.cil.ast.j8.J8BaseNode;
import com.mikesamuel.cil.ast.j8.J8CallableDeclaration;
import com.mikesamuel.cil.ast.j8.LastFormalParameterNode;
import com.mikesamuel.cil.ast.j8.LocalNameNode;
import com.mikesamuel.cil.ast.j8.MethodBodyNode;
import com.mikesamuel.cil.ast.j8.MethodDeclaratorNode;
import com.mikesamuel.cil.ast.j8.MethodHeaderNode;
import com.mikesamuel.cil.ast.j8.MethodInvocationNode;
import com.mikesamuel.cil.ast.j8.MethodNameNode;
import com.mikesamuel.cil.ast.j8.ModifierNode;
import com.mikesamuel.cil.ast.j8.PrimaryNode;
import com.mikesamuel.cil.ast.j8.ResultNode;
import com.mikesamuel.cil.ast.j8.ReturnStatementNode;
import com.mikesamuel.cil.ast.j8.StatementExpressionNode;
import com.mikesamuel.cil.ast.j8.StatementNode;
import com.mikesamuel.cil.ast.j8.TypeArgumentListNode;
import com.mikesamuel.cil.ast.j8.TypeArgumentNode;
import com.mikesamuel.cil.ast.j8.TypeArgumentsNode;
import com.mikesamuel.cil.ast.j8.TypeParameterNode;
import com.mikesamuel.cil.ast.j8.TypeParametersNode;
import com.mikesamuel.cil.ast.j8.UnannTypeNode;
import com.mikesamuel.cil.ast.j8.UnaryExpressionNode;
import com.mikesamuel.cil.ast.j8.VariableDeclaratorIdNode;
import com.mikesamuel.cil.ast.j8.WildcardNode;
import com.mikesamuel.cil.ast.meta.CallableInfo;
import com.mikesamuel.cil.ast.meta.MethodDescriptor;
import com.mikesamuel.cil.ast.meta.Name;
import com.mikesamuel.cil.ast.meta.StaticType;
import com.mikesamuel.cil.ast.meta.StaticType.PrimitiveType;
import com.mikesamuel.cil.ast.meta.StaticType.TypePool.ReferenceType;
import com.mikesamuel.cil.ast.meta.TypeInfo;
import com.mikesamuel.cil.ast.meta.TypeSpecification;
import com.mikesamuel.cil.ast.passes.TypeNodeFactory;
import com.mikesamuel.cil.util.LogUtils;

final class BridgeBuilderMiniPass {
  final Common c;

  BridgeBuilderMiniPass(Common c) {
    this.c = c;
  }

  /** Add synthetic bridge methods. */
  void addBridgeMethods() {
    Table<UserDefinedType, Name, Collection<Erasure>> groupedByType =
        c.grouped(c.bridges.asMap());
    for (Table.Cell<UserDefinedType, Name, Collection<Erasure>> cell
         : groupedByType.cellSet()) {
      UserDefinedType udt = cell.getRowKey();
      Name overrider = cell.getColumnKey();
      ImmutableSet<Erasure> overridden = ImmutableSet.copyOf(
          cell.getValue());
      AdjustableMember member = udt.lookup(overrider);
      if (member == null) {
        LogUtils.log(
            c.logger, Level.SEVERE, udt.bodyNode,
            "Could not find declaration of " + overrider
            + " which needs bridge method",
            null);
      } else {
        for (Erasure e : overridden) {
          bridge(e, member);
        }
      }
    }
  }

  private void bridge(Erasure e, AdjustableMember member) {
    CallableInfo ci = (CallableInfo) member.mi;
    J8BaseNode methodDeclaration = member.parent.getChild(member.index);
    J8BaseNode bridgeDeclaration = methodDeclaration.deepClone();
    Optional<J8CallableDeclaration> declOpt = bridgeDeclaration
        .finder(J8CallableDeclaration.class)
        .exclude(MethodBodyNode.class).findOne();
    Optional<MethodHeaderNode> headerOpt =
        bridgeDeclaration.finder(MethodHeaderNode.class)
        .exclude(MethodBodyNode.class).findOne();
    Optional<MethodBodyNode> bodyOpt =
        bridgeDeclaration.finder(MethodBodyNode.class)
        .exclude(MethodBodyNode.class).findOne();
    Optional<MethodDeclaratorNode> declaratorOpt =
        bridgeDeclaration.finder(MethodDeclaratorNode.class)
        .exclude(MethodBodyNode.class).findOne();
    Optional<FormalParameterListNode> formalListOpt =
        bridgeDeclaration.finder(FormalParameterListNode.class)
        .exclude(MethodBodyNode.class).findOne();
    if (!(declOpt.isPresent() && headerOpt.isPresent() && bodyOpt.isPresent()
        && declaratorOpt.isPresent() && formalListOpt.isPresent())) {
      LogUtils.log(
          c.logger, Level.SEVERE, methodDeclaration,
          "Cannot create bridge for method with unusual structure", null);
      return;
    }
    J8CallableDeclaration decl = declOpt.get();
    MethodHeaderNode header = headerOpt.get();
    MethodBodyNode body = bodyOpt.get();
    FormalParameterListNode formalList = formalListOpt.get();
    TypeParametersNode typeParamsNode = header.firstChildWithType(
        TypeParametersNode.class);
    ImmutableList<TypeParameterNode> typeParamNodes = typeParamsNode != null
        ? typeParamsNode.finder(TypeParameterNode.class).find()
        : ImmutableList.of();
    ImmutableList<FormalParameterNode> formals =
        formalList.finder(FormalParameterNode.class).find();
    Optional<LastFormalParameterNode> variadicFormal =
        formalList.finder(LastFormalParameterNode.class).findOne();
    ImmutableList<VariableDeclaratorIdNode> paramNames = formalList
        .finder(VariableDeclaratorIdNode.class).find();

    int formalCount = formals.size() + (variadicFormal.isPresent() ? 1 : 0);
    if (e.erasedFormalTypes.size() != formalCount) {
      LogUtils.log(
          c.logger, Level.SEVERE, formalList,
          "Expected " + e.erasedFormalTypes.size() + " parameters but found "
          + formalCount, null);
      return;
    }

    // Rewrite formal parameters to use proper erased type.
    {
      int i = 0, n = formals.size();
      for (; i < n; ++i) {
        rewriteUnannTypeTo(formals.get(i), e.erasedFormalTypes.get(i));
      }
      if (i < n) {
        Preconditions.checkState(variadicFormal.isPresent());
        TypeSpecification ft = e.erasedFormalTypes.get(n);
        if (ft.nDims == 0) {
          LogUtils.log(
              c.logger, Level.SEVERE, formalList,
              "Expected array type for variadic last formal parameter"
              + " but found " + ft, null);
          return;
        }
        rewriteUnannTypeTo(
            variadicFormal.get(), ft.withNDims(ft.nDims - 1));
      }
    }

    // Rewrite body to forward.
    PrimaryNode forwardingCall;
    {
      ImmutableList.Builder<J8BaseNode> b = ImmutableList.builder();
      b.add(ExpressionAtomNode.Variant.This.buildNode());
      if (!typeParamNodes.isEmpty()) {
        b.add(TypeArgumentsNode.Variant.LtTypeArgumentListGt.buildNode(
            TypeArgumentListNode.Variant.TypeArgumentComTypeArgument.buildNode(
                Lists.transform(
                    typeParamNodes,
                    new Function<TypeParameterNode, TypeArgumentNode>() {
                      @Override
                      public TypeArgumentNode apply(TypeParameterNode p) {
                        TypeInfo pti = p.getDeclaredTypeInfo();
                        if (pti != null) {
                          StaticType st = c.typePool.type(
                              TypeSpecification.unparameterized(pti.canonName),
                              p, c.logger);
                          if (st instanceof ReferenceType) {
                            return TypeArgumentNode.Variant.ReferenceType.buildNode(
                                c.factory.toReferenceTypeNode((ReferenceType) st));
                          }
                        }
                        return TypeArgumentNode.Variant.Wildcard.buildNode(
                           WildcardNode.Variant.AnnotationQmWildcardBounds
                           .buildNode());
                      }
                    }))));
      }
      b.add(MethodNameNode.Variant.Identifier.buildNode(
          TypeNodeFactory.toIdentifierNode(ci.canonName))
          .setCallableInfo(ci));
      {
        int n = paramNames.size();
        MethodDescriptor md = ci.getDescriptor();
        if (md == null || md.formalTypes.size() != n) {
          LogUtils.log(
              c.logger, Level.SEVERE, methodDeclaration,
              "Parameter count mismatch, got " + n
              + (md != null ? ", expected " + md.formalTypes.size() : ""),
              null);
          n = Math.min(md != null ? md.formalTypes.size() : 0, n);
        }
        boolean effectiveCast = false;
        ImmutableList.Builder<ExpressionNode> actuals =
            ImmutableList.builder();
        for (int i = 0; i < n; ++i) {
          VariableDeclaratorIdNode dn = paramNames.get(i);
          TypeSpecification fts = e.erasedFormalTypes.get(i);
          TypeSpecification tts = Preconditions.checkNotNull(md)
              .formalTypes.get(i);
          StaticType fromType = c.typePool.type(fts, dn, c.logger);
          StaticType toType = c.typePool.type(tts, dn, c.logger);
          StaticType.Cast castType = toType.assignableFrom(fromType);

          IdentifierNode id = dn.firstChildWithType(IdentifierNode.class);
          ExpressionAtomNode actualAtom =
              ExpressionAtomNode.Variant.Local
              .buildNode(
                  LocalNameNode.Variant.Identifier
                  .buildNode(id.deepClone()));
          actualAtom.setSourcePosition(dn.getSourcePosition());
          ExpressionNode actual = null;
          switch (castType) {
            case BOX:
            case CONFIRM_CHECKED:
            case CONVERTING_LOSSLESS:
            case CONVERTING_LOSSY:
            case UNBOX:
              effectiveCast = true;
              //$FALL-THROUGH$
            case CONFIRM_UNCHECKED:
              actual = castTo(toType, actualAtom);
              break;
            case DISJOINT:
              LogUtils.log(
                  c.logger, Level.SEVERE, dn,
                  "Cannot bridge " + fromType + " to " + toType, null);
              //$FALL-THROUGH$
            case CONFIRM_SAFE:
            case SAME:
              actual = ExpressionNode.Variant.ConditionalExpression
                  .buildNode(actualAtom);
              break;
          }
          Preconditions.checkNotNull(actual);
          actuals.add(actual);
        }
        if (!effectiveCast) {
          LogUtils.log(
              c.logger, Level.SEVERE, methodDeclaration,
              "Expected to have to cast in bridge method when converting "
              + e.erasedFormalTypes
              + ( md != null ? " to " + md.formalTypes : ""),
              null);
        }
        b.add(ArgumentListNode.Variant.ExpressionComExpression
            .buildNode(actuals.build()));
      }
      forwardingCall = PrimaryNode.Variant.MethodInvocation
        .buildNode(b.build());
    }

    ResultNode rn = header.firstChildWithType(ResultNode.class);
    boolean isVoid = rn != null && rn.getVariant() == ResultNode.Variant.Void;
    StatementNode forwardingCallStatement =
        isVoid
        ? StatementNode.Variant.ExpressionStatement.buildNode(
            ExpressionStatementNode.Variant
            .StatementExpressionSem.buildNode(
                StatementExpressionNode.Variant.MethodInvocation
                .buildNode(
                    MethodInvocationNode.Variant.ExplicitCallee.buildNode(
                        forwardingCall))))
        : StatementNode.Variant.ReturnStatement.buildNode(
            ReturnStatementNode.Variant.ReturnExpressionSem.buildNode(
                ExpressionNode.Variant.ConditionalExpression.buildNode(
                    forwardingCall)));
    BlockNode forwardingBlock = BlockNode.Variant.LcBlockStatementsRc
        .buildNode(
            BlockStatementsNode.Variant
            .BlockStatementBlockStatementBlockTypeScope.buildNode(
                BlockStatementNode.Variant.Statement.buildNode(
                    forwardingCallStatement)));
    body.replaceChildren(ImmutableList.of(forwardingBlock));
    ModifierNode annot = c.makeSyntheticAnnotationModifier();
    if (annot != null) {
      ((J8BaseInnerNode) decl).add(0, annot);
    }

    member.insertAfter(bridgeDeclaration);
  }

  private void rewriteUnannTypeTo(
      J8BaseInnerNode paramNode, TypeSpecification repl) {
    int typeIndex = paramNode.finder(UnannTypeNode.class).indexOf();
    if (typeIndex >= 0) {
      StaticType st = c.typePool.type(repl, paramNode, c.logger);
      paramNode.replace(typeIndex, c.factory.toUnannTypeNode(st));
    }
  }

  private ExpressionNode castTo(StaticType st, ExpressionAtomNode atom) {
    CastNode cast;
    if (st instanceof ReferenceType) {
      cast = CastNode.Variant.ConfirmCast.buildNode(
          ConfirmCastNode.Variant.ReferenceTypeAdditionalBound.buildNode(
              c.factory.toReferenceTypeNode((ReferenceType) st)));
    } else if (st instanceof PrimitiveType) {
      cast = CastNode.Variant.ConvertCast.buildNode(
          ConvertCastNode.Variant.PrimitiveType.buildNode(
              TypeNodeFactory.toPrimitiveTypeNode((PrimitiveType) st)));
    } else {
      LogUtils.log(c.logger, Level.SEVERE, atom, "Cannot cast to " + st, null);
      return ExpressionNode.Variant.ConditionalExpression.buildNode(atom);
    }
    return ExpressionNode.Variant.ConditionalExpression
        .buildNode(
            UnaryExpressionNode.Variant.CastExpression.buildNode(
                CastExpressionNode.Variant.Expression.buildNode(
                    cast, atom)));

  }
}
