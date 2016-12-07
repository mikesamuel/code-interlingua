package com.mikesamuel.cil.ast.passes;

import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;

import com.google.common.base.Optional;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Multimap;
import com.mikesamuel.cil.ast.BaseNode;
import com.mikesamuel.cil.ast.meta.Name;
import com.mikesamuel.cil.ast.meta.TypeInfo;
import com.mikesamuel.cil.ast.traits.TypeDeclaration;
import com.mikesamuel.cil.ast.traits.TypeScope;
import com.mikesamuel.cil.parser.SourcePosition;

/**
 * Sets the name meta-data for each class declaration encountered.
 */
final class ClassNamingPass
extends AbstractTypeDeclarationPass<ClassNamingPass.DeclarationsAndScopes> {
  private final Logger logger;
  private final Map<Name, UnresolvedTypeDeclaration> declaredTypes
      = new LinkedHashMap<>();

  ClassNamingPass(Logger logger) {
    this.logger = logger;
  }

  @Override
  protected void handleTypeDeclaration(
      TypeScope scope, TypeDeclaration d, Name name, boolean isAnonymous) {
    UnresolvedTypeDeclaration decl = new UnresolvedTypeDeclaration(scope, d);
    UnresolvedTypeDeclaration dupe = declaredTypes.get(name);
    if (dupe == null) {
      declaredTypes.put(name, decl);
    } else {
      SourcePosition pos = ((BaseNode) d).getSourcePosition();
      SourcePosition opos = ((BaseNode) dupe.decl).getSourcePosition();
      logger.severe(
          (pos != null ? pos + ": " : "")
          + "Duplicate definition for " + name
          + (opos != null ? " originally defined at " + opos.toString() : "")
          );
    }
    TypeInfo partialTypeInfo = new TypeInfo(
        name, 0, isAnonymous, Optional.absent(), ImmutableList.of(),
        Optional.fromNullable(name.getOuterType()), ImmutableList.of()
        );
    d.setDeclaredTypeInfo(partialTypeInfo);
  }

  @Override
  protected DeclarationsAndScopes getResult() {
    Multimap<Name, Name> innerTypes = HashMultimap.create();
    for (Name declName : declaredTypes.keySet()) {
      Name outerTypeName = declName.getOuterType();
      if (outerTypeName != null) {
        innerTypes.put(outerTypeName, declName);
      }
    }

    for (Map.Entry<Name, UnresolvedTypeDeclaration> e
         : declaredTypes.entrySet()) {
      Name declName = e.getKey();
      UnresolvedTypeDeclaration decl = e.getValue();
      if (decl.stage == Stage.UNRESOLVED) {
        Optional<Name> outerType =
            Optional.fromNullable(declName.getOuterType());
        char nameChar0 = declName.identifier.charAt(0);
        boolean isAnonymous = '0' <= nameChar0 && nameChar0 <= '9';
        // Does not include those inherited.
        ImmutableList<Name> innerTypeList = ImmutableList.copyOf(
            innerTypes.get(declName));

        decl.decl.setDeclaredTypeInfo(new TypeInfo(
            declName,
            Modifier.PUBLIC,  // Optimistic
            isAnonymous,
            Optional.<Name>absent(),  // Optimistic
            ImmutableList.<Name>of(),  // Optimistic
            outerType,
            innerTypeList));
      }
    }

    return new DeclarationsAndScopes(
        ImmutableMap.copyOf(this.declaredTypes),
        this.getScopeToParentMap());
  }


  static final class DeclarationsAndScopes {
    final ImmutableMap<Name, UnresolvedTypeDeclaration> declarations;
    final Map<TypeScope, TypeScope> scopeToParent;

    DeclarationsAndScopes(
        ImmutableMap<Name, UnresolvedTypeDeclaration> declarations,
        Map<TypeScope, TypeScope> scopeToParent) {
      this.declarations = declarations;
      this.scopeToParent = scopeToParent;
    }
  }
}
