package com.mikesamuel.cil.ast.passes.synth;

import java.util.EnumSet;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Table;
import com.mikesamuel.cil.ast.j8.ArgumentListNode;
import com.mikesamuel.cil.ast.j8.BlockNode;
import com.mikesamuel.cil.ast.j8.BlockStatementNode;
import com.mikesamuel.cil.ast.j8.BlockStatementsNode;
import com.mikesamuel.cil.ast.j8.ExpressionAtomNode;
import com.mikesamuel.cil.ast.j8.ExpressionNode;
import com.mikesamuel.cil.ast.j8.ExpressionStatementNode;
import com.mikesamuel.cil.ast.j8.FormalParametersNode;
import com.mikesamuel.cil.ast.j8.IdentifierNode;
import com.mikesamuel.cil.ast.j8.J8BaseInnerNode;
import com.mikesamuel.cil.ast.j8.J8BaseNode;
import com.mikesamuel.cil.ast.j8.J8CallableDeclaration;
import com.mikesamuel.cil.ast.j8.J8NodeType;
import com.mikesamuel.cil.ast.j8.LocalNameNode;
import com.mikesamuel.cil.ast.j8.MethodBodyNode;
import com.mikesamuel.cil.ast.j8.MethodInvocationNode;
import com.mikesamuel.cil.ast.j8.MethodNameNode;
import com.mikesamuel.cil.ast.j8.ModifierNode;
import com.mikesamuel.cil.ast.j8.PrimaryNode;
import com.mikesamuel.cil.ast.j8.ReferenceTypeNode;
import com.mikesamuel.cil.ast.j8.ReturnStatementNode;
import com.mikesamuel.cil.ast.j8.StatementExpressionNode;
import com.mikesamuel.cil.ast.j8.StatementNode;
import com.mikesamuel.cil.ast.j8.TypeArgumentListNode;
import com.mikesamuel.cil.ast.j8.TypeArgumentNode;
import com.mikesamuel.cil.ast.j8.TypeArgumentsNode;
import com.mikesamuel.cil.ast.j8.TypeParametersNode;
import com.mikesamuel.cil.ast.j8.TypeVariableNode;
import com.mikesamuel.cil.ast.j8.VariableDeclaratorIdNode;
import com.mikesamuel.cil.ast.meta.CallableInfo;
import com.mikesamuel.cil.ast.meta.MemberInfo;
import com.mikesamuel.cil.ast.meta.Name;
import com.mikesamuel.cil.ast.meta.StaticType;

final class SuperAccessorsMiniPass {
  final Common c;

  SuperAccessorsMiniPass(Common c) {
    this.c = c;
  }

  void addSuperAccessors() {
    Table<UserDefinedType, Name, EnumSet<UseType>> grouped =
        c.grouped(c.uses);
    for (Table.Cell<UserDefinedType, Name, EnumSet<UseType>> cell
         : grouped.cellSet()) {
      if (!cell.getValue().contains(UseType.SUPER_INVOKE)) { continue; }
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
      createSuperAccessor(udt, mem);
    }
  }

  private void createSuperAccessor(
      UserDefinedType udt, AdjustableMember mem) {
    J8BaseNode decl = mem.parent.getChild(mem.index);
    J8BaseNode superAccessorDecl = decl.deepClone();

    Optional<J8CallableDeclaration> superAccessorCDeclOpt =
        superAccessorDecl.finder(J8CallableDeclaration.class)
        .exclude(MethodBodyNode.class).findOne();
    Optional<MethodBodyNode> bodyOpt = decl.finder(MethodBodyNode.class)
        .exclude(MethodBodyNode.class).findOne();
    if (!bodyOpt.isPresent() || !superAccessorCDeclOpt.isPresent()
        || bodyOpt.get().getVariant() != MethodBodyNode.Variant.Block) {
      c.error(decl, "Cannot bind super call to abstract method");
      return;
    }
    MethodBodyNode body = bodyOpt.get();
    J8BaseInnerNode superAccessorCDecl = (J8BaseInnerNode)
        superAccessorCDeclOpt.get();

    Optional<MethodNameNode> superAccessorNameNode = superAccessorDecl.finder(
        MethodNameNode.class).exclude(MethodBodyNode.class).findOne();
    IdentifierNode nameIdent =
        superAccessorNameNode.isPresent()
        ? superAccessorNameNode.get().firstChildWithType(IdentifierNode.class)
        : null;
    if (nameIdent == null) {
      c.error(decl, "Malformed accessor");
      return;
    }
    CallableInfo ci = (CallableInfo) mem.mi;
    String allocatedName = c.nameAllocator.allocateIdentifier(
        "access$sup$" + ci.canonName.identifier);
    nameIdent.setValue(allocatedName);

    // Make protected and final.
    // Protected so that it can be accessed from arbitrary sub classes.
    EnumSet<ModifierNode.Variant> mods = EnumSet.noneOf(
        ModifierNode.Variant.class);
    for (int i = 0, n = superAccessorCDecl.getNChildren(); i < n; ++i) {
      J8BaseNode child = superAccessorCDecl.getChild(i);
      if (child instanceof ModifierNode) {
        ModifierNode.Variant v = ((ModifierNode) child).getVariant();
        switch (v) {
          // This whacks @Override annotations but is probably overkill
          case Annotation:
          case Public: case Private:
            superAccessorCDecl.remove(i);
            --i;
            --n;
            break;
          default:
            mods.add(v);
            break;
        }
      }
    }
    if (!mods.contains(ModifierNode.Variant.Final)) {
      superAccessorCDecl.add(0, ModifierNode.Variant.Final.buildNode());
    }
    if (!mods.contains(ModifierNode.Variant.Protected)) {
      superAccessorCDecl.add(0, ModifierNode.Variant.Protected.buildNode());
    }
    ModifierNode annot = c.makeSyntheticAnnotationModifier();
    if (annot != null) {
      superAccessorCDecl.add(0, annot);
    }

    // Replace the body of the original with a forwarding call to the clone.
    PrimaryNode callToAccessor =
        PrimaryNode.Variant.MethodInvocation.buildNode();
    callToAccessor.add(ExpressionAtomNode.Variant.This.buildNode());

    ImmutableList.Builder<String> typeParamNames = ImmutableList.builder();
    Optional<TypeParametersNode> typeParams =
        decl.finder(TypeParametersNode.class)
        .exclude(MethodBodyNode.class).findOne();
    if (typeParams.isPresent()) {
      TypeArgumentListNode typeArgumentList =
          TypeArgumentListNode.Variant.TypeArgumentComTypeArgument.buildNode();
      for (IdentifierNode typeParamName
          : typeParams.get().finder(IdentifierNode.class)
            .exclude(J8NodeType.Modifier, J8NodeType.TypeBound)
            .find()) {
        String name = typeParamName.getValue();
        typeParamNames.add(name);
        typeArgumentList.add(
            TypeArgumentNode.Variant.ReferenceType.buildNode(
                ReferenceTypeNode.Variant.TypeVariable.buildNode(
                    TypeVariableNode.Variant.AnnotationIdentifier.buildNode(
                        IdentifierNode.Variant.Builtin.buildNode(name)))));
      }
      callToAccessor.add(
          TypeArgumentsNode.Variant.LtTypeArgumentListGt.buildNode(
              typeArgumentList));
    }

    callToAccessor.add(MethodNameNode.Variant.Identifier.buildNode(
        IdentifierNode.Variant.Builtin.buildNode(allocatedName)));

    Optional<FormalParametersNode> formals =
        decl.finder(FormalParametersNode.class)
        .exclude(MethodBodyNode.class).findOne();
    ImmutableList.Builder<String> paramNames = ImmutableList.builder();
    if (formals.isPresent()) {
      ArgumentListNode argumentList =
          ArgumentListNode.Variant.ExpressionComExpression.buildNode();
      for (VariableDeclaratorIdNode vdi
          : formals.get().finder(VariableDeclaratorIdNode.class).find()) {
        IdentifierNode id = vdi.firstChildWithType(IdentifierNode.class);
        if (id == null) {
          c.error(id, "Missing parameter name");
          continue;
        }
        String name = id.getValue();
        paramNames.add(name);
        argumentList.add(
            ExpressionNode.Variant.ConditionalExpression.buildNode(
                ExpressionAtomNode.Variant.Local.buildNode(
                    LocalNameNode.Variant.Identifier.buildNode(
                        IdentifierNode.Variant.Builtin.buildNode(
                            name)))));
      }
      callToAccessor.add(argumentList);
    }

    StatementNode bodyStatement;
    boolean isVoid = StaticType.T_VOID.typeSpecification
        .equals(ci.getReturnType());
    if (isVoid) {
      bodyStatement = StatementNode.Variant.ExpressionStatement.buildNode(
          ExpressionStatementNode.Variant.StatementExpressionSem.buildNode(
              StatementExpressionNode.Variant.MethodInvocation.buildNode(
                  MethodInvocationNode.Variant.ExplicitCallee.buildNode(
                      callToAccessor))));
    } else {
      bodyStatement = StatementNode.Variant.ReturnStatement.buildNode(
          ReturnStatementNode.Variant.ReturnExpressionSem.buildNode(
              ExpressionNode.Variant.ConditionalExpression.buildNode(
                  callToAccessor)));
    }

    body.replaceChildren(ImmutableList.of(
        BlockNode.Variant.LcBlockStatementsRc.buildNode(
            BlockStatementsNode.Variant
            .BlockStatementBlockStatementBlockTypeScope.buildNode(
                BlockStatementNode.Variant.Statement.buildNode(
                    bodyStatement)))));
    mem.insertAfter(superAccessorDecl);
    udt.allocatedAccessors.put(
        new Use(UseType.SUPER_INVOKE, ci.canonName),
        new Accessor(
            ci,
            callToAccessor,
            ci.typeParameters,
            typeParamNames.build(),
            0,
            ci.getFormalTypes(),
            paramNames.build(),
            ci.getReturnType(),
            allocatedName));
  }
}
