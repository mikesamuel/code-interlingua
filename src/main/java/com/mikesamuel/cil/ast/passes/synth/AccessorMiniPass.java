package com.mikesamuel.cil.ast.passes.synth;

import java.lang.reflect.Modifier;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Table;
import com.mikesamuel.cil.ast.j8.ArgumentListNode;
import com.mikesamuel.cil.ast.j8.AssignmentNode;
import com.mikesamuel.cil.ast.j8.AssignmentOperatorNode;
import com.mikesamuel.cil.ast.j8.BlockNode;
import com.mikesamuel.cil.ast.j8.BlockStatementNode;
import com.mikesamuel.cil.ast.j8.BlockStatementsNode;
import com.mikesamuel.cil.ast.j8.ClassInstanceCreationExpressionNode;
import com.mikesamuel.cil.ast.j8.ClassMemberDeclarationNode;
import com.mikesamuel.cil.ast.j8.ConstructorBodyNode;
import com.mikesamuel.cil.ast.j8.ConstructorDeclarationNode;
import com.mikesamuel.cil.ast.j8.ConstructorDeclaratorNode;
import com.mikesamuel.cil.ast.j8.ExceptionTypeListNode;
import com.mikesamuel.cil.ast.j8.ExplicitConstructorInvocationNode;
import com.mikesamuel.cil.ast.j8.ExpressionAtomNode;
import com.mikesamuel.cil.ast.j8.ExpressionNode;
import com.mikesamuel.cil.ast.j8.ExpressionStatementNode;
import com.mikesamuel.cil.ast.j8.FieldNameNode;
import com.mikesamuel.cil.ast.j8.FormalParameterListNode;
import com.mikesamuel.cil.ast.j8.FormalParameterNode;
import com.mikesamuel.cil.ast.j8.FormalParametersNode;
import com.mikesamuel.cil.ast.j8.IdentifierNode;
import com.mikesamuel.cil.ast.j8.J8BaseInnerNode;
import com.mikesamuel.cil.ast.j8.J8BaseNode;
import com.mikesamuel.cil.ast.j8.LastFormalParameterNode;
import com.mikesamuel.cil.ast.j8.LeftHandSideNode;
import com.mikesamuel.cil.ast.j8.LocalNameNode;
import com.mikesamuel.cil.ast.j8.MethodBodyNode;
import com.mikesamuel.cil.ast.j8.MethodDeclarationNode;
import com.mikesamuel.cil.ast.j8.MethodDeclaratorNode;
import com.mikesamuel.cil.ast.j8.MethodHeaderNode;
import com.mikesamuel.cil.ast.j8.MethodInvocationNode;
import com.mikesamuel.cil.ast.j8.MethodNameNode;
import com.mikesamuel.cil.ast.j8.ModifierNode;
import com.mikesamuel.cil.ast.j8.PrimaryNode;
import com.mikesamuel.cil.ast.j8.ReferenceTypeNode;
import com.mikesamuel.cil.ast.j8.ResultNode;
import com.mikesamuel.cil.ast.j8.ReturnStatementNode;
import com.mikesamuel.cil.ast.j8.SimpleTypeNameNode;
import com.mikesamuel.cil.ast.j8.StatementExpressionNode;
import com.mikesamuel.cil.ast.j8.StatementNode;
import com.mikesamuel.cil.ast.j8.ThrowsNode;
import com.mikesamuel.cil.ast.j8.TypeArgumentListNode;
import com.mikesamuel.cil.ast.j8.TypeArgumentNode;
import com.mikesamuel.cil.ast.j8.TypeArgumentsNode;
import com.mikesamuel.cil.ast.j8.TypeBoundNode;
import com.mikesamuel.cil.ast.j8.TypeParameterListNode;
import com.mikesamuel.cil.ast.j8.TypeParameterNode;
import com.mikesamuel.cil.ast.j8.TypeParametersNode;
import com.mikesamuel.cil.ast.j8.TypeVariableNode;
import com.mikesamuel.cil.ast.j8.VariableDeclaratorIdNode;
import com.mikesamuel.cil.ast.meta.CallableInfo;
import com.mikesamuel.cil.ast.meta.FieldInfo;
import com.mikesamuel.cil.ast.meta.MemberInfo;
import com.mikesamuel.cil.ast.meta.Name;
import com.mikesamuel.cil.ast.meta.StaticType;
import com.mikesamuel.cil.ast.meta.TypeInfo;
import com.mikesamuel.cil.ast.meta.TypeSpecification;
import com.mikesamuel.cil.ast.passes.ForwardingConstructorUtil;
import com.mikesamuel.cil.ast.passes.TypeNodeFactory;

final class AccessorMiniPass {
  final Common c;

  AccessorMiniPass(Common c) { this.c = c; }

  void addPrivateAccessors() {
    Table<UserDefinedType, Name, EnumSet<UseType>> grouped =
        c.grouped(c.uses);
    for (Table.Cell<UserDefinedType, Name, EnumSet<UseType>> cell
         : grouped.cellSet()) {
      UserDefinedType udt = cell.getRowKey();
      Name accesseeName = cell.getColumnKey();
      Optional<MemberInfo> accesseeOpt =
          udt.ti.declaredMemberNamed(accesseeName);
      if (!accesseeOpt.isPresent()) {
        c.error(udt.bodyNode, "Missing member info for " + accesseeName);
        continue;
      }
      MemberInfo accessee = accesseeOpt.get();
      AdjustableMember mem = udt.lookup(accessee.canonName);
      if (mem == null) {
        c.error(
            udt.bodyNode, "Cannot find declaration of " + accessee.canonName);
        continue;
      }
      TypeSpecification resultType =
          accessee instanceof CallableInfo
          ? ((CallableInfo) accessee).getReturnType()
          : ((FieldInfo) accessee).getValueType();
      boolean isConstructor = accessee instanceof CallableInfo
          && ((CallableInfo) accessee).isConstructor();
      for (UseType t : cell.getValue()) {
        ImmutableList<TypeSpecification> parameterTypes = ImmutableList.of();
        ImmutableList<String> parameterNames = ImmutableList.of();
        int nDisambig = 0;
        ImmutableList<Name> typeParametersToForward = ImmutableList.of();
        ImmutableList<String> typeParameterNames = ImmutableList.copyOf(
            Lists.transform(
                typeParametersToForward,
                new Function<Name, String>() {
                  @Override public String apply(Name nm) {
                    return nm.identifier;
                  }
                }));
        J8BaseNode accessBody = null;
        ExpressionAtomNode selfRef = Modifier.isStatic(accessee.modifiers)
            ? ExpressionAtomNode.Variant.StaticMember.buildNode(
                c.factory.toTypeNameNode(udt.ti))
            : ExpressionAtomNode.Variant.This.buildNode();
        switch (t) {
          case INVOKE_OF_PRIVATE: {
            CallableInfo ci = (CallableInfo) accessee;
            J8BaseInnerNode invoc;
            ImmutableList<TypeSpecification> disambiguating =
                ImmutableList.of();
            if (isConstructor) {
              invoc = ExplicitConstructorInvocationNode.Variant
                  .TypeArgumentsThisLpArgumentListRpSem.buildNode();
              disambiguating = ForwardingConstructorUtil
                  .computeDelegateStrategy(udt.ti);
              parameterTypes = ImmutableList.<TypeSpecification>builder()
                  .addAll(disambiguating)
                  .addAll(ci.getFormalTypes())
                  .build();
            } else {
              invoc = PrimaryNode.Variant.MethodInvocation
                  .buildNode(selfRef);
              parameterTypes = ci.getFormalTypes();
            }
            nDisambig = disambiguating.size();

            typeParametersToForward = ci.typeParameters;
            if (!typeParametersToForward.isEmpty()) {
              ImmutableList.Builder<TypeArgumentNode> b
                  = ImmutableList.builder();
              for (Name nm : typeParametersToForward) {
                b.add(TypeArgumentNode.Variant.ReferenceType.buildNode(
                    ReferenceTypeNode.Variant.TypeVariable.buildNode(
                        TypeVariableNode.Variant.AnnotationIdentifier
                        .buildNode(
                            IdentifierNode.Variant.Builtin.buildNode(
                                nm.identifier)))));
              }
              invoc.add(TypeArgumentsNode.Variant.LtTypeArgumentListGt
                  .buildNode(TypeArgumentListNode.Variant
                      .TypeArgumentComTypeArgument.buildNode(
                          b.build())));
            }
            if (!isConstructor) {
              invoc.add(MethodNameNode.Variant.Identifier.buildNode(
                  TypeNodeFactory.toIdentifierNode(accessee.canonName)));
            }
            ImmutableList.Builder<String> params = ImmutableList.builder();
            if (!parameterTypes.isEmpty()) {
              ImmutableList.Builder<ExpressionNode> actualsBuilder =
                  ImmutableList.builder();
              for (int i = 0, n = parameterTypes.size(); i < n; ++i) {
                String name = "a" + i;
                params.add(name);
                if (i >= nDisambig) {
                  actualsBuilder.add(
                      ExpressionNode.Variant.ConditionalExpression.buildNode(
                          ExpressionAtomNode.Variant.Local.buildNode(
                              LocalNameNode.Variant.Identifier.buildNode(
                                  IdentifierNode.Variant.Builtin.buildNode(
                                      name)))));
                }
              }
              ImmutableList<ExpressionNode> actuals = actualsBuilder.build();
              if (!actuals.isEmpty()) {
                invoc.add(
                    ArgumentListNode.Variant.ExpressionComExpression.buildNode(
                        actuals));
              }
            }
            parameterNames = params.build();
            accessBody = isConstructor
                ? invoc
                : ExpressionNode.Variant.ConditionalExpression
                  .buildNode(invoc);
            break;
          }
          case READ_OF_PRIVATE:
            accessBody =
                ExpressionNode.Variant.ConditionalExpression.buildNode(
                    PrimaryNode.Variant.FieldAccess.buildNode(
                        selfRef,
                        FieldNameNode.Variant.Identifier.buildNode(
                            TypeNodeFactory.toIdentifierNode(
                                accessee.canonName))));
            break;
          case SUPER_INVOKE:
            // Handled separately.
            continue;
          case WRITE_OF_PRIVATE:
            String paramName = c.nameAllocator.allocateIdentifier(
                "new$" + accessee.canonName.identifier);
            parameterNames = ImmutableList.of(paramName);
            parameterTypes = ImmutableList.of(resultType);
            accessBody =
                ExpressionNode.Variant.Assignment.buildNode(
                    AssignmentNode.Variant
                    .LeftHandSideAssignmentOperatorExpression.buildNode(
                        LeftHandSideNode.Variant.FieldAccess.buildNode(
                            PrimaryNode.Variant.FieldAccess.buildNode(
                                selfRef,
                                FieldNameNode.Variant.Identifier.buildNode(
                                    TypeNodeFactory.toIdentifierNode(
                                        accessee.canonName)))),
                        AssignmentOperatorNode.Variant.Eq.buildNode(),
                        ExpressionNode.Variant.ConditionalExpression
                        .buildNode(
                            ExpressionAtomNode.Variant.Local.buildNode(
                                LocalNameNode.Variant.Identifier.buildNode(
                                    IdentifierNode.Variant.Builtin
                                    .buildNode(paramName))))));

            break;
        }
        String allocatedName = isConstructor
            ? Name.CTOR_INSTANCE_INITIALIZER_SPECIAL_NAME
            : c.nameAllocator.allocateIdentifier(
                "access$" + t.abbrev + "$" + accessee.canonName.identifier);
        Preconditions.checkNotNull(accessBody);
        Accessor acc = new Accessor(
            accessee, accessBody, typeParametersToForward, typeParameterNames,
            nDisambig, parameterTypes, parameterNames,
            resultType, allocatedName);
        defineAccessor(udt, mem, acc, new Use(t, accessee.canonName));
      }
    }
  }

  private void defineAccessor(
      UserDefinedType udt, AdjustableMember mem, Accessor acc, Use use) {
    boolean isConstructor = acc.accessee instanceof CallableInfo
        && ((CallableInfo) acc.accessee).isConstructor();

    // If we have forwarding parameters, we need to use them consistently
    // in our callable signature.
    final class ParameterForwarder extends TypeSpecification.Mapper{
      final Map<Name, Name> paramMap = new LinkedHashMap<>();
      {
        Name methodName = udt.ti.canonName.method(acc.allocatedName, 1);
        for (int i = 0, n = acc.typeParametersNames.size(); i < n; ++i) {
          paramMap.put(
              acc.typeParametersToForward.get(i),
              methodName.child(
                  acc.typeParametersNames.get(i), Name.Type.TYPE_PARAMETER));
        }
      }

      @Override
      public Name map(Name nm) {
        if (nm.type == Name.Type.TYPE_PARAMETER) {
          Name repl = paramMap.get(nm);
          if (repl != null) { return repl; }
        }
        return nm;
      }
    }
    ParameterForwarder pf = new ParameterForwarder();

    // Children for main nodes.
    ImmutableList.Builder<J8BaseNode> b = ImmutableList.builder();

    // Add modifiers
    ModifierNode annot = c.makeSyntheticAnnotationModifier();
    if (annot != null) { b.add(annot); }
    if (!isConstructor && Modifier.isStatic(mem.mi.modifiers)) {
      b.add(ModifierNode.Variant.Static.buildNode());
    }

    // Generate any type parameter list.
    TypeParametersNode tps = null;
    if (!acc.typeParametersToForward.isEmpty()) {
      ImmutableList.Builder<J8BaseNode> tp = ImmutableList.builder();
      for (int i = 0, n = acc.typeParametersToForward.size(); i < n; ++i) {
        Optional<TypeInfo> ti = c.typePool.r.resolve(
            acc.typeParametersToForward.get(i));
        String name = acc.typeParametersNames.get(i);
        TypeParameterNode pn = TypeParameterNode.Variant.TypeParameter
            .buildNode(
                SimpleTypeNameNode.Variant.Identifier.buildNode(name));
        TypeBoundNode bound = ti.isPresent()
            ? c.factory.typeBoundFor(ti.get()) : null;
        if (bound != null) {
          pn.add(bound);
        }
        tp.add(pn);
      }
      tps = TypeParametersNode.Variant.LtTypeParameterListGt.buildNode(
          TypeParameterListNode.Variant.TypeParameterComTypeParameter
          .buildNode(tp.build()));
    }

    // Generate a result type for a plain method call.
    ResultNode rs = null;
    if (!isConstructor) {
      rs = c.factory.toResultNode(
          c.typePool.type(pf.map(acc.resultType), udt.bodyNode, c.logger));
    }

    // Generate a callee name.
    J8BaseNode nameNode;
    if (isConstructor) {
      nameNode = SimpleTypeNameNode.Variant.Identifier.buildNode(
          IdentifierNode.Variant.Builtin.buildNode(
              udt.ti.canonName.identifier));
    } else {
      nameNode = MethodNameNode.Variant.Identifier.buildNode(
          IdentifierNode.Variant.Builtin.buildNode(acc.allocatedName));
    }

    // Generate a formal parameter list
    FormalParameterListNode pl = null;
    if (!acc.parameterNames.isEmpty()) {
      boolean hasLastParam = acc.accessee instanceof CallableInfo
          && ((CallableInfo) acc.accessee).isVariadic();
      int nNormalParams = acc.parameterNames.size() - (hasLastParam ? 1 : 0);
      pl = (nNormalParams == 0
          ? FormalParameterListNode.Variant.LastFormalParameter
          : FormalParameterListNode.Variant
            .FormalParametersComLastFormalParameter)
          .buildNode();
      if (nNormalParams != 0) {
        FormalParametersNode ps = FormalParametersNode.Variant
            .FormalParameterComFormalParameter.buildNode();
        pl.add(ps);
        for (int i = 0; i < nNormalParams; ++i) {
          ps.add(FormalParameterNode.Variant.Declaration.buildNode(
              c.factory.toUnannTypeNode(c.typePool.type(
                  pf.map(acc.parameterTypes.get(i)), udt.bodyNode, c.logger)),
              VariableDeclaratorIdNode.Variant.IdentifierDims.buildNode(
                  IdentifierNode.Variant.Builtin.buildNode(
                      acc.parameterNames.get(i)))));
        }
      }
      if (hasLastParam) {
        TypeSpecification ts = pf.map(acc.parameterTypes.get(nNormalParams));
        ts = ts.withNDims(ts.nDims - 1);
        pl.add(LastFormalParameterNode.Variant.Variadic.buildNode(
            c.factory.toUnannTypeNode(c.typePool.type(
                ts, udt.bodyNode, c.logger)),
            VariableDeclaratorIdNode.Variant.IdentifierDims.buildNode(
                IdentifierNode.Variant.Builtin.buildNode(
                    acc.parameterNames.get(nNormalParams)))));
      }
    }

    // Generate any throws clause.
    ThrowsNode throwsNode = null;
    if (mem.mi instanceof CallableInfo) {
      CallableInfo ci = (CallableInfo) mem.mi;
      if (!ci.getThrownTypes().isEmpty()) {
        ExceptionTypeListNode etl = ExceptionTypeListNode.Variant
            .ExceptionTypeComExceptionType.buildNode();
        throwsNode = ThrowsNode.Variant.ThrowsExceptionTypeList
            .buildNode(etl);
        for (TypeSpecification thrown : ci.getThrownTypes()) {
          etl.add(c.factory.toExceptionType(pf.map(thrown)));
        }
      }
    }

    // Assemble the bits above into a coherent declaration, leaving
    // the child nodes on b.
    if (isConstructor) {
      ConstructorDeclaratorNode cdn = ConstructorDeclaratorNode.Variant
          .TypeParametersSimpleTypeNameLpFormalParameterListRp.buildNode();
      if (tps != null) { cdn.add(tps); }
      cdn.add(nameNode);
      if (pl != null) { cdn.add(pl); }
      b.add(cdn);
      if (throwsNode != null) { b.add(throwsNode); }
    } else {
      MethodDeclaratorNode declarator = MethodDeclaratorNode.Variant
          .MethodNameLpFormalParameterListRpDims
          .buildNode();
      declarator.add(nameNode);
      if (pl != null) { declarator.add(pl); }
      MethodHeaderNode hdr = MethodHeaderNode.Variant
          .TypeParametersAnnotationResultMethodDeclaratorThrows
          .buildNode();
      if (tps != null) { hdr.add(tps); }
      hdr.add(rs);
      hdr.add(declarator);
      if (throwsNode != null) { hdr.add(throwsNode); }
      b.add(hdr);
    }

    // Generate a method or constructor body as appropriate.
    J8BaseNode body;
    if (isConstructor) {
      Preconditions.checkState(
          acc.accessBody instanceof ExplicitConstructorInvocationNode);
      body = ConstructorBodyNode.Variant
          .LcExplicitConstructorInvocationBlockStatementsRc.buildNode(
              acc.accessBody);
    } else {
      Preconditions.checkState(acc.accessBody instanceof ExpressionNode);
      StatementNode bodyStmt;
      if (acc.resultType.equals(StaticType.T_VOID.typeSpecification)) {
        StatementExpressionNode stmtExpr;
        if (acc.accessBody.getVariant()
            == ExpressionNode.Variant.Assignment) {
          stmtExpr = StatementExpressionNode.Variant.Assignment
              .buildNode(acc.accessBody.getChildren());
        } else if (
            acc.accessBody.getChild(0).getVariant()
            == PrimaryNode.Variant.MethodInvocation) {
          stmtExpr = StatementExpressionNode.Variant.MethodInvocation
              .buildNode(
                  MethodInvocationNode.Variant.ExplicitCallee.buildNode(
                      acc.accessBody.getChildren()));
        } else {
          Preconditions.checkState(
              acc.accessBody.getChild(0).getVariant()
              == ExpressionAtomNode.Variant
              .UnqualifiedClassInstanceCreationExpression);
          stmtExpr = StatementExpressionNode.Variant
              .ClassInstanceCreationExpression.buildNode(
                  ClassInstanceCreationExpressionNode.Variant
                  .UnqualifiedClassInstanceCreationExpression.buildNode(
                      acc.accessBody.getChildren()));
        }
        bodyStmt = StatementNode.Variant.ExpressionStatement
            .buildNode(ExpressionStatementNode.Variant.StatementExpressionSem
                .buildNode(stmtExpr));
      } else {
        bodyStmt = StatementNode.Variant.ReturnStatement.buildNode(
            ReturnStatementNode.Variant.ReturnExpressionSem.buildNode(
                acc.accessBody));
      }
      body = MethodBodyNode.Variant.Block.buildNode(
          BlockNode.Variant.LcBlockStatementsRc.buildNode(
              BlockStatementsNode.Variant
              .BlockStatementBlockStatementBlockTypeScope.buildNode(
                  BlockStatementNode.Variant.Statement.buildNode(
                      bodyStmt))));
    }
    b.add(body);

    // Wrap the result in a member declaration.
    ClassMemberDeclarationNode d;
    if (isConstructor) {
      d = ClassMemberDeclarationNode.Variant.ConstructorDeclaration.buildNode(
          ConstructorDeclarationNode.Variant.Declaration.buildNode(b.build()));
    } else {
      d = ClassMemberDeclarationNode.Variant.MethodDeclaration.buildNode(
          MethodDeclarationNode.Variant.Declaration.buildNode(b.build()));
    }

    // Add it to the type declaration and register it so that
    // when we rewrite uses, we can find enough meta-information to
    // generate a call.
    mem.insertAfter(d);
    udt.allocatedAccessors.put(use, acc);
  }

}
