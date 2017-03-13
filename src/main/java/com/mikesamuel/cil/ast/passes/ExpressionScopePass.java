package com.mikesamuel.cil.ast.passes;

import java.lang.reflect.Modifier;
import java.util.logging.Logger;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.mikesamuel.cil.ast.j8.BlockNode;
import com.mikesamuel.cil.ast.j8.CompilationUnitNode;
import com.mikesamuel.cil.ast.j8.IdentifierNode;
import com.mikesamuel.cil.ast.j8.J8BaseNode;
import com.mikesamuel.cil.ast.j8.J8NodeType;
import com.mikesamuel.cil.ast.j8.PackageDeclarationNode;
import com.mikesamuel.cil.ast.j8.SingleStaticImportDeclarationNode;
import com.mikesamuel.cil.ast.j8.StaticImportOnDemandDeclarationNode;
import com.mikesamuel.cil.ast.j8.SwitchBlockNode;
import com.mikesamuel.cil.ast.j8.TemplateDirectivesNode;
import com.mikesamuel.cil.ast.j8.TypeNameNode;
import com.mikesamuel.cil.ast.j8.traits.CallableDeclaration;
import com.mikesamuel.cil.ast.j8.traits.ExpressionNameDeclaration;
import com.mikesamuel.cil.ast.j8.traits.ExpressionNameScope;
import com.mikesamuel.cil.ast.j8.traits.FileNode;
import com.mikesamuel.cil.ast.j8.traits.LimitedScopeElement;
import com.mikesamuel.cil.ast.j8.traits.TypeDeclaration;
import com.mikesamuel.cil.ast.meta.ExpressionNameResolver;
import com.mikesamuel.cil.ast.meta.ExpressionNameResolver
    .BlockExpressionNameResolver;
import com.mikesamuel.cil.ast.meta.ExpressionNameResolver
    .DeclarationPositionMarker;
import com.mikesamuel.cil.ast.meta.FieldInfo;
import com.mikesamuel.cil.ast.meta.MemberInfo;
import com.mikesamuel.cil.ast.meta.Name;
import com.mikesamuel.cil.ast.meta.TypeInfo;
import com.mikesamuel.cil.ast.meta.TypeInfoResolver;
import com.mikesamuel.cil.ast.meta.TypeNameResolver;

/**
 * Associates expression name resolvers and position markers with scopes and
 * block statements so that a later pass can resolve expression names.
 */
public final class ExpressionScopePass extends AbstractPass<Void> {
  final TypeInfoResolver typeInfoResolver;
  final TypeNameResolver qualifiedNameResolver;

  ExpressionScopePass(TypeInfoResolver typeInfoResolver, Logger logger) {
    super(logger);
    this.typeInfoResolver = typeInfoResolver;
    this.qualifiedNameResolver =
        TypeNameResolver.Resolvers.canonicalizer(typeInfoResolver);
  }

  private DeclarationPositionMarker walk(
      J8BaseNode node, ExpressionNameResolver r,
      DeclarationPositionMarker m,
      Name outer) {
    ExpressionNameResolver childResolver = r;
    DeclarationPositionMarker currentMarker = m;
    Name childOuter = outer;

    if (node instanceof TemplateDirectivesNode) {
      // The scopes introduced by directives are disjoint from those introduced
      // by Java.
      return m;
    }

    if (node instanceof TypeDeclaration) {
      TypeInfo ti = ((TypeDeclaration) node).getDeclaredTypeInfo();
      if (ti != null) {
        // Could be null for (new ...) that do not declare an
        // anonymous type.
        Preconditions.checkNotNull(ti, node);
        childResolver = ExpressionNameResolver.Resolvers.forType(
            ti, typeInfoResolver);
        currentMarker = DeclarationPositionMarker.EARLIEST;
        childOuter = ((TypeDeclaration) node).getDeclaredTypeInfo().canonName;
      }
    }

    if (node instanceof CallableDeclaration) {
      CallableDeclaration cd = (CallableDeclaration) node;
      childOuter = outer.method(cd.getMethodName(), cd.getMethodVariant());
    }

    if (node instanceof ExpressionNameScope) {
      if (node instanceof CallableDeclaration
          || node instanceof BlockNode
          || node instanceof SwitchBlockNode) {
        Preconditions.checkState(childResolver == r);
        childResolver = new BlockExpressionNameResolver();
        currentMarker = DeclarationPositionMarker.EARLIEST;
      }
      ((ExpressionNameScope) node).setExpressionNameResolver(childResolver);
    }
    if (node instanceof ExpressionNameDeclaration
        && r instanceof BlockExpressionNameResolver) {
      ExpressionNameDeclaration decl = (ExpressionNameDeclaration) node;
      // TODO: disambiguate multiple uses of the same name in a block of code.
      Name declName = outer.child(
          decl.getDeclaredExpressionIdentifier(), Name.Type.LOCAL);
      decl.setDeclaredExpressionName(declName);
      currentMarker = ((BlockExpressionNameResolver) r).declare(declName);
    }

    for (J8BaseNode child : node.getChildren()) {
      currentMarker = walk(child, childResolver, currentMarker, childOuter);
    }

    if (node instanceof LimitedScopeElement) {
      ((LimitedScopeElement) node).setDeclarationPositionMarker(currentMarker);
    }

    return childResolver != r ? m : currentMarker;
  }

  @Override
  public Void run(Iterable<? extends FileNode> fileNodes) {
    for (FileNode fn : fileNodes) {
      ExpressionNameResolver r = resolverFor(fn);
      fn.setExpressionNameResolver(r);
      walk((J8BaseNode) fn, r, DeclarationPositionMarker.LATEST, null);
    }
    return null;
  }

  private ExpressionNameResolver resolverFor(FileNode fn) {
    CompilationUnitNode cu;
    if (fn instanceof CompilationUnitNode) {
      cu = (CompilationUnitNode) fn;
    } else {
      cu = fn.firstChildWithType(CompilationUnitNode.class);
    }
    Preconditions.checkNotNull(cu, fn);

    PackageDeclarationNode pkgNode = cu.firstChildWithType(
        PackageDeclarationNode.class);
    Name packageName = Name.DEFAULT_PACKAGE;
    if (pkgNode != null) {
      for (IdentifierNode ident
          : pkgNode.finder(IdentifierNode.class)
            .exclude(J8NodeType.Annotation)
            .find()) {
        packageName = packageName.child(ident.getValue(), Name.Type.PACKAGE);
      }
    }

    ImmutableList.Builder<Name> explicit = ImmutableList.builder();
    for (SingleStaticImportDeclarationNode idecl
        : cu.finder(SingleStaticImportDeclarationNode.class)
          .exclude(J8NodeType.TypeDeclaration)
          .find()) {
      TypeNameNode typeName = idecl.firstChildWithType(TypeNameNode.class);
      if (typeName == null) {  // Maybe part of a template
        continue;
      }
      IdentifierNode fieldOrMethodName = idecl.firstChildWithType(
          IdentifierNode.class);
      if (fieldOrMethodName == null) {
        continue;
      }
      Optional<TypeInfo> tiOpt = lookupType(typeName);
      if (tiOpt.isPresent()) {
        String possibleFieldName = fieldOrMethodName.getValue();
        TypeInfo ti = tiOpt.get();
        typeName.setReferencedTypeInfo(ti);
        boolean hasAccessibleStaticFieldNamed = ti.memberMatching(
            this.typeInfoResolver,
            new Predicate<MemberInfo>() {
              @Override
              public boolean apply(MemberInfo mi) {
                int mods = mi.modifiers;
                return !Modifier.isPrivate(mods)
                    // TODO: Do we need to check package access here.
                    && Modifier.isStatic(mods)
                    && mi instanceof FieldInfo
                    && mi.canonName.identifier.equals(possibleFieldName);
              }

            })
            .isPresent();
        if (hasAccessibleStaticFieldNamed) {
          explicit.add(ti.canonName.child(
              possibleFieldName, Name.Type.FIELD));
        }
      } else {
        error(typeName, "Unknown type " + typeName.getTextContent("."));
      }
    }

    ImmutableList.Builder<TypeInfo> wildcards = ImmutableList.builder();
    for (StaticImportOnDemandDeclarationNode idecl
        : cu.finder(StaticImportOnDemandDeclarationNode.class)
          .exclude(J8NodeType.TypeDeclaration)
          .find()) {
      TypeNameNode typeName = idecl.firstChildWithType(TypeNameNode.class);
      if (typeName == null) {  // Maybe part of a template
        continue;
      }
      Optional<TypeInfo> tiOpt = lookupType(typeName);
      if (tiOpt.isPresent()) {
        TypeInfo ti = tiOpt.get();
        typeName.setReferencedTypeInfo(ti);
        wildcards.add(ti);
      } else {
        error(
            typeName,
            "Cannot resolve static import of " + typeName.getTextContent("."));
      }
    }
    return ExpressionNameResolver.Resolvers.forImports(
        explicit.build(), wildcards.build(), typeInfoResolver,
        packageName, logger);
  }

  private static Name ambiguousNameFor(TypeNameNode tn) {
    Name ambig = null;
    for (IdentifierNode ident : tn.finder(IdentifierNode.class)
        .exclude(
            J8NodeType.Annotation,
            J8NodeType.TypeArgumentsOrDiamond, J8NodeType.TypeArguments)
        .find()) {
      if (ambig == null) {
        ambig = Name.root(ident.getValue(), Name.Type.AMBIGUOUS);
      } else {
        ambig = ambig.child(ident.getValue(), Name.Type.AMBIGUOUS);
      }
    }
    return ambig;
  }

  private Optional<TypeInfo> lookupType(TypeNameNode typeName) {
    ImmutableList<Name> names = qualifiedNameResolver.lookupTypeName(
            ambiguousNameFor(typeName));
    switch (names.size()) {
      case 0:
        error(typeName, "Cannot resolve name " + typeName.getTextContent("."));
        return Optional.absent();
      case 1:
        return typeInfoResolver.resolve(names.get(0));
      default:
        error(typeName, "Ambiguous name " + typeName.getTextContent(".")
              + ":" + names);
        return Optional.absent();
    }
  }

}
