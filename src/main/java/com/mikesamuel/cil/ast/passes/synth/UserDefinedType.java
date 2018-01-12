package com.mikesamuel.cil.ast.passes.synth;

import java.util.LinkedHashMap;
import java.util.Map;

import com.google.common.base.Preconditions;
import com.mikesamuel.cil.ast.j8.ClassBodyNode;
import com.mikesamuel.cil.ast.j8.InterfaceBodyNode;
import com.mikesamuel.cil.ast.j8.J8BaseInnerNode;
import com.mikesamuel.cil.ast.j8.J8BaseNode;
import com.mikesamuel.cil.ast.j8.J8MemberDeclaration;
import com.mikesamuel.cil.ast.j8.J8NodeType;
import com.mikesamuel.cil.ast.j8.J8NodeVariant;
import com.mikesamuel.cil.ast.j8.J8TypeDeclaration;
import com.mikesamuel.cil.ast.j8.VariableDeclaratorIdNode;
import com.mikesamuel.cil.ast.j8.VariableDeclaratorListNode;
import com.mikesamuel.cil.ast.meta.MemberInfo;
import com.mikesamuel.cil.ast.meta.Name;
import com.mikesamuel.cil.ast.meta.TypeInfo;

final class UserDefinedType {
  final Name topLevelTypeContaining;
  final J8TypeDeclaration typeDecl;
  final TypeInfo ti;
  final J8BaseInnerNode bodyNode;
  final Map<Use, Accessor> allocatedAccessors = new LinkedHashMap<>();

  UserDefinedType(J8TypeDeclaration typeDecl) {
    this.typeDecl = typeDecl;
    this.ti = Preconditions.checkNotNull(typeDecl.getDeclaredTypeInfo());
    // Walk up until we run out of type names.
    this.topLevelTypeContaining = Common.topLevelTypeOf(ti.canonName);
    J8BaseInnerNode body = typeDecl.firstChildWithType(ClassBodyNode.class);
    if (body == null) {
      body = typeDecl.firstChildWithType(InterfaceBodyNode.class);
    }
    this.bodyNode = body;
  }

  AdjustableMember lookup(Name name) {
    if (bodyNode == null) { return null; }
    for (int i = 0, n = bodyNode.getNChildren(); i < n; ++i) {
      J8BaseNode member = bodyNode.getChild(i);
      J8NodeVariant wrapperType = null;
      if (member.getNChildren() == 1) {
        switch (member.getNodeType()) {
          case ClassMemberDeclaration:
          case InterfaceMemberDeclaration:
            wrapperType = member.getVariant();
            member = member.getChild(0);
            break;
          default:
        }
      }
      if (member instanceof J8MemberDeclaration) {
        J8MemberDeclaration decl = (J8MemberDeclaration) member;
        MemberInfo mi = decl.getMemberInfo();
        boolean found = mi != null && name.equals(mi.canonName);
        if (!found) {
          VariableDeclaratorListNode multiDecl = decl.firstChildWithType(
              VariableDeclaratorListNode.class);
          if (multiDecl != null) {
            for (VariableDeclaratorIdNode id
                : multiDecl.finder(VariableDeclaratorIdNode.class)
                .exclude(J8NodeType.VariableInitializer)
                .find()) {
              if (name.equals(id.getDeclaredExpressionName())) {
                mi = ti.declaredMemberNamed(name).orNull();
                found = mi != null;
                break;
              }
            }
          }
        }
        if (found) {
          return new AdjustableMember(mi, bodyNode, i, wrapperType);
        }
      }
    }
    return null;
  }
}