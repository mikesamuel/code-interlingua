package com.mikesamuel.cil.ast.passes.flatten;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.common.base.Function;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Table;
import com.mikesamuel.cil.ast.j8.AnnotationNode;
import com.mikesamuel.cil.ast.j8.ArgumentListNode;
import com.mikesamuel.cil.ast.j8.AssignmentNode;
import com.mikesamuel.cil.ast.j8.AssignmentOperatorNode;
import com.mikesamuel.cil.ast.j8.BlockNode;
import com.mikesamuel.cil.ast.j8.BlockStatementNode;
import com.mikesamuel.cil.ast.j8.BlockStatementsNode;
import com.mikesamuel.cil.ast.j8.BooleanLiteralNode;
import com.mikesamuel.cil.ast.j8.ClassBodyNode;
import com.mikesamuel.cil.ast.j8.ClassMemberDeclarationNode;
import com.mikesamuel.cil.ast.j8.ConditionalOrExpressionNode;
import com.mikesamuel.cil.ast.j8.ConstructorBodyNode;
import com.mikesamuel.cil.ast.j8.ConstructorDeclarationNode;
import com.mikesamuel.cil.ast.j8.ConstructorDeclaratorNode;
import com.mikesamuel.cil.ast.j8.ElementValueNode;
import com.mikesamuel.cil.ast.j8.EqualityExpressionNode;
import com.mikesamuel.cil.ast.j8.EqualityOperatorNode;
import com.mikesamuel.cil.ast.j8.ExplicitConstructorInvocationNode;
import com.mikesamuel.cil.ast.j8.ExpressionAtomNode;
import com.mikesamuel.cil.ast.j8.ExpressionNode;
import com.mikesamuel.cil.ast.j8.ExpressionStatementNode;
import com.mikesamuel.cil.ast.j8.FieldNameNode;
import com.mikesamuel.cil.ast.j8.FormalParameterListNode;
import com.mikesamuel.cil.ast.j8.FormalParameterNode;
import com.mikesamuel.cil.ast.j8.FormalParametersNode;
import com.mikesamuel.cil.ast.j8.IdentifierNode;
import com.mikesamuel.cil.ast.j8.IfStatementNode;
import com.mikesamuel.cil.ast.j8.J8BaseNode;
import com.mikesamuel.cil.ast.j8.LeftHandSideNode;
import com.mikesamuel.cil.ast.j8.LiteralNode;
import com.mikesamuel.cil.ast.j8.LocalNameNode;
import com.mikesamuel.cil.ast.j8.MethodBodyNode;
import com.mikesamuel.cil.ast.j8.MethodDeclarationNode;
import com.mikesamuel.cil.ast.j8.MethodDeclaratorNode;
import com.mikesamuel.cil.ast.j8.MethodHeaderNode;
import com.mikesamuel.cil.ast.j8.MethodNameNode;
import com.mikesamuel.cil.ast.j8.ModifierNode;
import com.mikesamuel.cil.ast.j8.NullLiteralNode;
import com.mikesamuel.cil.ast.j8.PrimaryNode;
import com.mikesamuel.cil.ast.j8.ReceiverParameterNode;
import com.mikesamuel.cil.ast.j8.ResultNode;
import com.mikesamuel.cil.ast.j8.ReturnStatementNode;
import com.mikesamuel.cil.ast.j8.SimpleTypeNameNode;
import com.mikesamuel.cil.ast.j8.SingleElementAnnotationNode;
import com.mikesamuel.cil.ast.j8.StatementExpressionNode;
import com.mikesamuel.cil.ast.j8.StatementNode;
import com.mikesamuel.cil.ast.j8.StringLiteralNode;
import com.mikesamuel.cil.ast.j8.ThrowStatementNode;
import com.mikesamuel.cil.ast.j8.VariableDeclaratorIdNode;
import com.mikesamuel.cil.ast.meta.CallableInfo;
import com.mikesamuel.cil.ast.meta.FieldInfo;
import com.mikesamuel.cil.ast.meta.JavaLang;
import com.mikesamuel.cil.ast.meta.MethodDescriptor;
import com.mikesamuel.cil.ast.meta.Name;
import com.mikesamuel.cil.ast.meta.StaticType;
import com.mikesamuel.cil.ast.meta.StaticType.TypePool;
import com.mikesamuel.cil.ast.meta.TypeSpecification;
import com.mikesamuel.cil.ast.passes.ForwardingConstructorUtil;
import com.mikesamuel.cil.ast.passes.TypeNodeFactory;
import com.mikesamuel.cil.ast.passes.flatten.PassState.ClosedOver;
import com.mikesamuel.cil.ast.passes.flatten.PassState.ClosedOverThisValue;
import com.mikesamuel.cil.ast.passes.flatten.PassState.FlatteningType;
import com.mikesamuel.cil.util.LogUtils;

final class InitializeClosedOverStateMiniPass {

  final Logger logger;
  final TypePool pool;
  final TypeNodeFactory factory;

  InitializeClosedOverStateMiniPass(Logger logger, TypePool pool) {
    this.logger = logger;
    this.pool = pool;
    this.factory = new TypeNodeFactory(logger, pool);
    factory.allowMethodContainers();
  }

  void run(PassState ps) {
    Table<BName, MethodDescriptor, Constructor> ctorTable =
        HashBasedTable.create();
    for (PassState.FlatteningType ft : ps.inProcessOrder) {
      if (!ft.closedOverMembers.isEmpty()) {
        ClassBodyNode cb = ft.root.firstChildWithType(ClassBodyNode.class);
        if (cb == null) {
          LogUtils.log(
              logger, Level.SEVERE, ft.root,
              "Cannot find class body for " + ft.bumpyName, null);
          continue;
        }

        ImmutableList<ClosedOver> cos = ft.closedOverInOrder;
        ImmutableList<TypeSpecification> delegateStrategy =
            computeDelegateStrategy(ft);

        List<Constructor> ctors = findConstructors(ft, cb);
        for (Constructor ctor : ctors) {
          adjustConstructor(ps, ft, cb, ctor, cos, delegateStrategy);
        }
        for (Constructor ctor : ctors) {
          ctorTable.put(ft.bumpyName, ctor.methodDescriptor, ctor);
        }
      }
    }
  }

  private List<Constructor> findConstructors(
      PassState.FlatteningType ft, ClassBodyNode cb) {
    List<Constructor> ctors = new ArrayList<>();
    for (int i = 0, n = cb.getNChildren(); i < n; ++i) {
      J8BaseNode child = cb.getChild(i);
      if (child.getVariant() ==
          ClassMemberDeclarationNode.Variant.ConstructorDeclaration) {
        ConstructorDeclarationNode cd = child.firstChildWithType(
            ConstructorDeclarationNode.class);
        CallableInfo ci = cd.getCallableInfo();
        MethodDescriptor md = ci != null ? ci.getDescriptor() : null;
        if (md == null) {
          LogUtils.log(
              logger, Level.SEVERE, cd, "Missing type info for constructor",
              null);
          continue;
        }
        ConstructorBodyNode cbn = cd.firstChildWithType(ConstructorBodyNode.class);
        ExplicitConstructorInvocationNode eci = cbn != null
            ? cbn.firstChildWithType(ExplicitConstructorInvocationNode.class)
            : null;
        boolean explicitylyPassesSuperThis =
            eci != null
            && eci.getVariant()
                == ExplicitConstructorInvocationNode.Variant
                   .PrimaryDotTypeArgumentsSuperLpArgumentListRpSem;
        ctors.add(new Constructor(i, cd, md, explicitylyPassesSuperThis));
      }
    }
    if (ctors.isEmpty()) {  // Manufacture one.
      ConstructorDeclarationNode cd =
          ConstructorDeclarationNode.Variant.Declaration
          .buildNode(
              ConstructorDeclaratorNode.Variant
              .TypeParametersSimpleTypeNameLpFormalParameterListRp.buildNode(
                  SimpleTypeNameNode.Variant.Identifier.buildNode(
                      IdentifierNode.Variant.Builtin.buildNode(
                          ft.flatName.name.identifier))),
              ConstructorBodyNode.Variant
              .LcExplicitConstructorInvocationBlockStatementsRc
              .buildNode());
      ClassMemberDeclarationNode cbd = ClassMemberDeclarationNode.Variant
          .ConstructorDeclaration.buildNode(cd);
      ctors.add(new Constructor(
          cb.getNChildren(), cd, ZERO_TO_VOID, false));
      cb.add(cbd);
    }
    return ctors;
  }

  static final class Constructor {
    final int indexIntoClassBody;
    final ConstructorDeclarationNode root;
    final MethodDescriptor methodDescriptor;
    final boolean explicitylyPassesSuperThis;

    Constructor(
        int indexIntoClassBody, ConstructorDeclarationNode root,
        MethodDescriptor methodDescriptor,
        boolean explicitylyPassesSuperThis) {
      this.indexIntoClassBody = indexIntoClassBody;
      this.root = root;
      this.methodDescriptor = methodDescriptor;
      this.explicitylyPassesSuperThis = explicitylyPassesSuperThis;
    }

    @Override public String toString() {
      return methodDescriptor.toString();
    }
  }

  private static final MethodDescriptor ZERO_TO_VOID =
      MethodDescriptor.builder()
      .withReturnType(StaticType.T_VOID.typeSpecification.rawName, 0)
      .build();

  private static ImmutableList<TypeSpecification> computeDelegateStrategy(
      FlatteningType ft) {
    boolean simpleStyle = ft.eligibleForSimpleInitialization;
    if (simpleStyle) { return ImmutableList.of(); }
    return ForwardingConstructorUtil.computeDelegateStrategy(ft.typeInfo);
  }

  private void adjustConstructor(
      PassState ps, FlatteningType ft, ClassBodyNode classBody, Constructor ctor,
      List<ClosedOver> cos, ImmutableList<TypeSpecification> delegateStrategy) {
    ConstructorDeclaratorNode cd = ctor.root.firstChildWithType(
        ConstructorDeclaratorNode.class);
    FormalParameterListNode formalList = cd.firstChildWithType(
        FormalParameterListNode.class);
    FormalParametersNode formals;
    if (formalList == null) {
      formals = FormalParametersNode.Variant
          .FormalParameterComFormalParameter.buildNode();
      formalList = FormalParameterListNode.Variant
          .FormalParametersComLastFormalParameter.buildNode(formals);
      cd.add(formalList);
    } else {
      formals = formalList.firstChildWithType(FormalParametersNode.class);
      if (formals == null) {
        formals = FormalParametersNode.Variant
            .FormalParameterComFormalParameter.buildNode();
        formalList.add(0, formals);
        formalList.setVariant(
            FormalParameterListNode.Variant
            .FormalParametersComLastFormalParameter);
      }
    }

    ConstructorBodyNode cb = ctor.root.firstChildWithType(
        ConstructorBodyNode.class);
    // If we have a constructor like
    //   Foo() {
    //     E.super();
    //   }
    // then we want to substitute (E) when computing the initial value of the
    // super-this-value.
    ExpressionNode outerThisExpr = null;
    ClosedOver outerThis = null;
    {
      Name outerName = ft.bumpyName.name.parent.getContainingClass();
      FlatteningType outerFt = outerName != null
          ? ps.byBumpyName.get(BName.of(outerName)) : null;
      if (outerFt != null) {
        outerThis = new PassState.ClosedOverThisValue(outerFt);
        ExplicitConstructorInvocationNode expInv = cb.firstChildWithType(
            ExplicitConstructorInvocationNode.class);
        if (expInv != null && expInv.getVariant()
            == ExplicitConstructorInvocationNode.Variant
            .PrimaryDotTypeArgumentsSuperLpArgumentListRpSem) {
          // Expect a primary or an atom in position 0.
          int exprIndex = expInv.finder(PrimaryNode.class).indexOf();
          if (exprIndex < 0) {
            exprIndex = expInv.finder(ExpressionAtomNode.class).indexOf();
          }
          if (exprIndex >= 0) {
            expInv.setVariant(
                ExplicitConstructorInvocationNode.Variant
                .TypeArgumentsSuperLpArgumentListRpSem);
            outerThisExpr = ExpressionNode.Variant.ConditionalExpression
                .buildNode(expInv.getChild(exprIndex));
            expInv.remove(exprIndex);
          }
        }
      }
    }

    // Since we haven't yet modified the constructor, lets clone it
    // if we'll later need a private copy to delegate to.
    ConstructorDeclarationNode delegatee = null;
    if (!delegateStrategy.isEmpty()) {
      delegatee = ctor.root.deepClone();
    }

    ImmutableList<FormalParameterNode> formalParams;
    {
      ImmutableList.Builder<FormalParameterNode> b =
          ImmutableList.builder();
      for (ClosedOver co : cos) {
        FieldInfo fi = ft.closedOverMembers.get(co);
        FormalParameterNode fpn = FormalParameterNode.Variant.Declaration
            .buildNode(
                factory.toUnannTypeNode(pool.type(
                    co.getType(pool.r), ctor.root, logger)),
                VariableDeclaratorIdNode.Variant.IdentifierDims.buildNode(
                    IdentifierNode.Variant.Builtin.buildNode(
                        fi.canonName.identifier)));
        b.add(fpn);  // Save for init method.
      }
      formalParams = b.build();
    }

    {
      int paramInsertionPt =
          formals.finder(ReceiverParameterNode.class).indexOf() + 1;
      for (FormalParameterNode fpn : formalParams) {
        formals.add(paramInsertionPt, fpn);
        ++paramInsertionPt;
      }
    }

    // We need to check that this values are not null.
    J8BaseNode nullCheck = null;
    for (ClosedOver co : cos) {
      if (co instanceof ClosedOverThisValue) {
        ClosedOverThisValue cotv = (ClosedOverThisValue) co;
        FieldInfo fi = ft.closedOverMembers.get(cotv);
        EqualityExpressionNode ee = EqualityExpressionNode.Variant
            .EqualityExpressionEqualityOperatorRelationalExpression.buildNode(
                PrimaryNode.Variant.FieldAccess.buildNode(
                    ExpressionAtomNode.Variant.This.buildNode(),
                    FieldNameNode.Variant.Identifier.buildNode(
                        IdentifierNode.Variant.Builtin.buildNode(
                            fi.canonName.identifier))),
                EqualityOperatorNode.Variant.EqEq.buildNode(),
                ExpressionAtomNode.Variant.Literal.buildNode(
                    LiteralNode.Variant.NullLiteral.buildNode(
                        NullLiteralNode.Variant.Null.buildNode())));
        if (nullCheck != null) {
          nullCheck = ConditionalOrExpressionNode.Variant
              .ConditionalOrExpressionPip2ConditionalAndExpression
              .buildNode(nullCheck, ee);
        } else {
          nullCheck = ee;
        }
      }
    }
    ImmutableList.Builder<BlockStatementNode> initializationStmts =
        ImmutableList.builder();
    for (ClosedOver co : cos) {
      FieldInfo fi = ft.closedOverMembers.get(co);
      String id = fi.canonName.identifier;
      ExpressionNode rhs;
      if (delegatee == null  // Simple initialization
          && outerThisExpr != null
          && co.equals(outerThis)) {
        rhs = outerThisExpr;
      } else {
        rhs = ExpressionNode.Variant.ConditionalExpression
            .buildNode(
                ExpressionAtomNode.Variant.Local.buildNode(
                    LocalNameNode.Variant.Identifier
                    .buildNode(
                        IdentifierNode.Variant.Builtin
                        .buildNode(id))));
      }

      // this.foo = rhs;
      initializationStmts.add(
          BlockStatementNode.Variant.Statement.buildNode(
              StatementNode.Variant.ExpressionStatement.buildNode(
                  ExpressionStatementNode.Variant.StatementExpressionSem.buildNode(
                      StatementExpressionNode.Variant.Assignment.buildNode(
                          AssignmentNode.Variant
                          .LeftHandSideAssignmentOperatorExpression.buildNode(
                              LeftHandSideNode.Variant.FieldAccess.buildNode(
                                  PrimaryNode.Variant.FieldAccess.buildNode(
                                      ExpressionAtomNode.Variant.This
                                      .buildNode(),
                                      FieldNameNode.Variant.Identifier
                                      .buildNode(
                                          IdentifierNode.Variant.Builtin
                                          .buildNode(id)))),
                              AssignmentOperatorNode.Variant.Eq.buildNode(),
                              rhs))))));
    }
    if (nullCheck != null) {
      BlockStatementNode block =
          BlockStatementNode.Variant.Statement.buildNode(
              StatementNode.Variant.ThrowStatement.buildNode(
                  ThrowStatementNode.Variant.ThrowExpressionSem
                  .buildNode(
                      ExpressionNode.Variant
                      .ConditionalExpression.buildNode(
                          ExpressionAtomNode.Variant.Literal
                          .buildNode(
                              LiteralNode.Variant.NullLiteral
                              .buildNode(
                                  NullLiteralNode.Variant.Null
                                  .buildNode()))))));
      initializationStmts.add(BlockStatementNode.Variant.Statement.buildNode(
          StatementNode.Variant.IfStatement.buildNode(
              IfStatementNode.Variant.IfLpExpressionRpStatementNotElse
              .buildNode(
                  ExpressionNode.Variant.ConditionalExpression.buildNode(
                      nullCheck),
                  StatementNode.Variant.Block.buildNode(
                      BlockNode.Variant.LcBlockStatementsRc.buildNode(
                          BlockStatementsNode.Variant
                          .BlockStatementBlockStatementBlockTypeScope
                          .buildNode(block)))))));
    }

    if (delegatee == null) {  // Simple initialization strategy
      BlockStatementsNode stmtsNode = cb.firstChildWithType(
          BlockStatementsNode.class);
      if (stmtsNode == null) {
        stmtsNode = BlockStatementsNode.Variant
            .BlockStatementBlockStatementBlockTypeScope.buildNode();
        cb.add(stmtsNode);
      } else if (stmtsNode.getVariant()
                 == BlockStatementsNode.Variant.BlockTypeScope) {
        stmtsNode.setVariant(
            BlockStatementsNode.Variant.BlockStatementBlockStatementBlockTypeScope);
      }
      stmtsNode.replaceChildren(
          ImmutableList.<J8BaseNode>builder()
          .addAll(initializationStmts.build())
          .addAll(stmtsNode.getChildren())
          .build());
    } else {
      if (ft.initMethodName == null) {
        ft.initMethodName = ft.nameAllocator.allocateIdentifier(
            "initClosedOver");
        MethodDeclarationNode initMethodDecl =
            MethodDeclarationNode.Variant.Declaration.buildNode(
                ModifierNode.Variant.Private.buildNode(),
                MethodHeaderNode.Variant
                .TypeParametersAnnotationResultMethodDeclaratorThrows.buildNode(
                    ResultNode.Variant.UnannType.buildNode(
                        factory.toUnannTypeNode(StaticType.T_BOOLEAN)),
                    MethodDeclaratorNode.Variant
                    .MethodNameLpFormalParameterListRpDims.buildNode(
                        MethodNameNode.Variant.Identifier.buildNode(
                            IdentifierNode.Variant.Builtin.buildNode(
                                ft.initMethodName)),
                        FormalParameterListNode.Variant
                        .FormalParametersComLastFormalParameter.buildNode(
                            FormalParametersNode.Variant
                            .FormalParameterComFormalParameter.buildNode(
                                Lists.transform(
                                    formalParams,
                                    new Function<J8BaseNode, J8BaseNode>() {
                                      @Override
                                      public J8BaseNode apply(J8BaseNode n) {
                                        return n.deepClone();
                                      }
                                    }))))),
                MethodBodyNode.Variant.Block.buildNode(
                    BlockNode.Variant.LcBlockStatementsRc.buildNode(
                        BlockStatementsNode.Variant
                        .BlockStatementBlockStatementBlockTypeScope
                        .buildNode(
                            ImmutableList.<J8BaseNode>builder()
                            .addAll(initializationStmts.build())
                            .add(BlockStatementNode.Variant.Statement.buildNode(
                                StatementNode.Variant.ReturnStatement.buildNode(
                                    ReturnStatementNode.Variant.ReturnExpressionSem
                                    .buildNode(
                                        ExpressionNode.Variant.ConditionalExpression
                                        .buildNode(
                                            ExpressionAtomNode.Variant.Literal
                                            .buildNode(
                                                LiteralNode.Variant.BooleanLiteral
                                                .buildNode(
                                                    BooleanLiteralNode.Variant.False
                                                    .buildNode())))))))
                            .build()))));
        classBody.add(ClassMemberDeclarationNode.Variant.MethodDeclaration
            .buildNode(initMethodDecl));
      }

      // 1. Clone ctor and make a private copy with delegate strategy parameters
      //    preceding.
      boolean isPrivate = false;
      for (int i = delegatee.getNChildren(); --i >= 0;) {
        J8BaseNode node = delegatee.getChild(i);
        if (node instanceof ModifierNode) {
          switch (((ModifierNode) node).getVariant()) {
            case Private:
              isPrivate = true;
              break;
            case Public:
            case Protected:
              delegatee.remove(i);
              break;
            default:
          }
        }
      }
      if (!isPrivate) {
        int cdIndex = delegatee.finder(ConstructorDeclaratorNode.class)
            .indexOf();
        delegatee.add(cdIndex, ModifierNode.Variant.Private.buildNode());
      }
      classBody.add(
          ClassMemberDeclarationNode.Variant.ConstructorDeclaration
          .buildNode(delegatee));
      // 2. Rewrite non-private version to delegate to private version via
      //    this(...) explicit constructor invocation.
      ImmutableList.Builder<ExpressionNode> initCallActuals
          = ImmutableList.builder();
      for (ClosedOver co : cos) {
        FieldInfo fi = ft.closedOverMembers.get(co);
        ExpressionNode rhs;
        if (outerThisExpr != null
            && co.equals(outerThis)) {
          rhs = outerThisExpr;
        } else {
          rhs = ExpressionNode.Variant.ConditionalExpression.buildNode(
              ExpressionAtomNode.Variant.Local.buildNode(
                  LocalNameNode.Variant.Identifier.buildNode(
                      IdentifierNode.Variant.Builtin.buildNode(
                      fi.canonName.identifier))));
        }
        initCallActuals.add(rhs);
      }
      ExpressionNode callToInitMethod =
          ExpressionNode.Variant.ConditionalExpression.buildNode(
              PrimaryNode.Variant.MethodInvocation.buildNode(
                  ExpressionAtomNode.Variant.This.buildNode(),
                  MethodNameNode.Variant.Identifier.buildNode(
                      IdentifierNode.Variant.Builtin.buildNode(
                          ft.initMethodName)),
                  ArgumentListNode.Variant.ExpressionComExpression.buildNode(
                      initCallActuals.build())));
      // Make sure the constructor invocation passes the right number of
      // bogus boolean parameters such that
      //  * The zero-th argument is a call to the
      //    private boolean initClosedOverState method defined below.
      //  * Other arguments are the literal zero-value for that type.
      ImmutableList<ExpressionNode> actualsForFowardingCall;
      {
        ImmutableList.Builder<ExpressionNode> b = ImmutableList.builder();
        ConstructorDeclaratorNode dcd = delegatee.firstChildWithType(
            ConstructorDeclaratorNode.class);
        FormalParameterListNode dfpl = dcd.firstChildWithType(
            FormalParameterListNode.class);
        FormalParametersNode dfps = dfpl.firstChildWithType(
            FormalParametersNode.class);
        ImmutableList<VariableDeclaratorIdNode> formalDecls = dfps
            .finder(VariableDeclaratorIdNode.class).find();
        int paramInsertionPt =
            dfps.finder(ReceiverParameterNode.class).indexOf() + 1;
        for (int i = 0, n = delegateStrategy.size(); i < n; ++i) {
          String name = ft.nameAllocator.allocateIdentifier("ign");
          TypeSpecification ts = delegateStrategy.get(i);
          b.add(i == 0 ? callToInitMethod : TypeNodeFactory.zeroValueFor(ts));
          dfps.add(
              paramInsertionPt,
              FormalParameterNode.Variant.Declaration.buildNode(
                  // @SuppressWarnings("unused")
                  ModifierNode.Variant.Annotation.buildNode(
                      AnnotationNode.Variant.SingleElementAnnotation
                      .buildNode(
                          SingleElementAnnotationNode.Variant
                          .AtTypeNameLpElementValueRp.buildNode(
                              factory.toTypeNameNode(
                                  JavaLang.JAVA_LANG_SUPPRESSWARNINGS.rawName),
                              ElementValueNode.Variant.ConditionalExpression
                              .buildNode(
                                  ExpressionAtomNode.Variant.Literal
                                  .buildNode(
                                      LiteralNode.Variant.StringLiteral
                                      .buildNode(
                                          StringLiteralNode.Variant.Builtin
                                          .buildNode("\"unused\""))))))),
                  factory.toUnannTypeNode(pool.type(ts, ft.root, logger)),
                  VariableDeclaratorIdNode.Variant.IdentifierDims.buildNode(
                      IdentifierNode.Variant.Builtin.buildNode(name))));
        }
        for (VariableDeclaratorIdNode formalDecl : formalDecls) {
          IdentifierNode formalNameId = formalDecl.firstChildWithType(
              IdentifierNode.class);
          b.add(ExpressionNode.Variant.ConditionalExpression.buildNode(
              ExpressionAtomNode.Variant.Local.buildNode(
                  LocalNameNode.Variant.Identifier.buildNode(
                      formalNameId.deepClone()))));
        }
        actualsForFowardingCall = b.build();
      }
      cb.replaceChildren(ImmutableList.of(
          ExplicitConstructorInvocationNode.Variant
          .TypeArgumentsThisLpArgumentListRpSem.buildNode(
              // TODO: forward type arguments
              ArgumentListNode.Variant.ExpressionComExpression.buildNode(
                  actualsForFowardingCall))));

    }
  }
}
