package com.mikesamuel.cil.ast.passes.flatten;

import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.mikesamuel.cil.ast.j8.IdentifierNode;
import com.mikesamuel.cil.ast.j8.J8BaseInnerNode;
import com.mikesamuel.cil.ast.j8.J8BaseNode;
import com.mikesamuel.cil.ast.j8.J8NodeType;
import com.mikesamuel.cil.ast.j8.J8TypeDeclaration;
import com.mikesamuel.cil.ast.j8.SimpleTypeNameNode;
import com.mikesamuel.cil.ast.j8.TypeBoundNode;
import com.mikesamuel.cil.ast.j8.TypeParameterListNode;
import com.mikesamuel.cil.ast.j8.TypeParameterNode;
import com.mikesamuel.cil.ast.j8.TypeParametersNode;
import com.mikesamuel.cil.ast.meta.TypeInfo;
import com.mikesamuel.cil.ast.meta.StaticType.TypePool;
import com.mikesamuel.cil.ast.passes.TypeNodeFactory;
import com.mikesamuel.cil.util.LogUtils;

final class InheritTypeParametersMiniPass {
  final Logger logger;
  final TypePool pool;

  InheritTypeParametersMiniPass(Logger logger, TypePool pool) {
    this.logger = logger;
    this.pool = pool;
  }

  void run(PassState ps) {
    TypeNodeFactory factory = new TypeNodeFactory(logger, pool);
    for (PassState.FlatteningType ft : ps.inProcessOrder) {
      if (!ft.flatParamInfo.flatParametersInOrder.isEmpty()) {
        J8TypeDeclaration decl = ft.root;
        TypeParametersNode typeParamsNode = decl.firstChildWithType(
            TypeParametersNode.class);
        if (typeParamsNode == null) {
          // Enums should all be static and do not have space for parameters.
          Preconditions.checkState(
              decl.getNodeType() != J8NodeType.EnumDeclaration);
          int simpleNameIndex = ((J8BaseNode) decl)
              .finder(SimpleTypeNameNode.class).indexOf();
          if (simpleNameIndex < 0) {
            LogUtils.log(
                logger, Level.SEVERE, decl, "Declaration is missing a name",
                null);
            continue;
          }
          typeParamsNode = TypeParametersNode.Variant.LtTypeParameterListGt
              .buildNode();
          ((J8BaseInnerNode) decl).add(simpleNameIndex + 1, typeParamsNode);
        }
        TypeParameterListNode paramListNode = typeParamsNode.firstChildWithType(
            TypeParameterListNode.class);
        if (paramListNode == null) {
          paramListNode = TypeParameterListNode.Variant
              .TypeParameterComTypeParameter.buildNode();
          typeParamsNode.add(paramListNode);
        }
        int nParams = paramListNode.finder(TypeParameterNode.class)
            .find().size();
        int nToAdd = ft.flatParamInfo.flatParametersInOrder.size() - nParams;
        // Insert at the start in reverse order so the list ends up in the
        // right order.
        for (int i = nToAdd; --i >= 0;) {
          BName bumpyName = ft.flatParamInfo.bumpyParametersInOrder.get(i);
          FName flatName = ft.flatParamInfo.flatParametersInOrder.get(i);
          TypeParameterNode newParam = TypeParameterNode.Variant.TypeParameter
              .buildNode(
                  SimpleTypeNameNode.Variant.Identifier.buildNode(
                      IdentifierNode.Variant.Builtin.buildNode(
                          flatName.name.identifier)));
          paramListNode.add(0, newParam);
          Optional<TypeInfo> tiOpt = pool.r.resolve(bumpyName.name);
          if (!tiOpt.isPresent()) {
            LogUtils.log(
                logger, Level.SEVERE, decl,
                "Missing type information for " + bumpyName.name, null);
            continue;
          }
          TypeBoundNode typeBound = factory.typeBoundFor(tiOpt.get());
          if (typeBound != null) {
            newParam.add(typeBound);
          }
        }
      }
    }
  }
}
