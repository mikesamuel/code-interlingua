package com.mikesamuel.cil.ast.passes;

import com.google.common.base.Preconditions;
import com.mikesamuel.cil.ast.j8.J8TypeDeclaration;
import com.mikesamuel.cil.ast.j8.J8TypeScope;

final class UnresolvedTypeDeclaration {
  Stage stage = Stage.UNRESOLVED;
  final J8TypeDeclaration decl;
  final J8TypeScope scope;

  UnresolvedTypeDeclaration(J8TypeScope scope, J8TypeDeclaration decl) {
    this.scope = Preconditions.checkNotNull(scope);
    this.decl = Preconditions.checkNotNull(decl);
  }
}
