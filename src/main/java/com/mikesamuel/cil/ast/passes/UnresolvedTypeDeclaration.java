package com.mikesamuel.cil.ast.passes;

import com.google.common.base.Preconditions;
import com.mikesamuel.cil.ast.traits.TypeDeclaration;
import com.mikesamuel.cil.ast.traits.TypeScope;

final class UnresolvedTypeDeclaration {
  Stage stage = Stage.UNRESOLVED;
  final TypeDeclaration decl;
  final TypeScope scope;

  UnresolvedTypeDeclaration(TypeScope scope, TypeDeclaration decl) {
    this.scope = Preconditions.checkNotNull(scope);
    this.decl = Preconditions.checkNotNull(decl);
  }
}
