package com.mikesamuel.cil.ast.passes;

import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.logging.Logger;

import javax.annotation.Nullable;

import com.google.common.collect.ImmutableList;
import com.mikesamuel.cil.ast.BaseNode;
import com.mikesamuel.cil.ast.ClassBodyNode;
import com.mikesamuel.cil.ast.CompilationUnitNode;
import com.mikesamuel.cil.ast.NodeType;
import com.mikesamuel.cil.ast.PackageDeclarationNode;
import com.mikesamuel.cil.ast.SimpleTypeNameNode;
import com.mikesamuel.cil.ast.meta.Name;
import com.mikesamuel.cil.ast.traits.CallableDeclaration;
import com.mikesamuel.cil.ast.traits.TypeDeclaration;
import com.mikesamuel.cil.ast.traits.TypeScope;

/**
 * Fires for each class declaration.
 */
abstract class AbstractTypeDeclarationPass<T> extends AbstractPass<T> {
  private final Map<Name, Integer> anonClassCounters = new HashMap<>();
  private final Map<Name, Integer> methodCounters = new HashMap<>();
  private final Map<TypeScope, TypeScope> scopeToParentScope =
      new IdentityHashMap<>();
  private Name pkg = Name.DEFAULT_PACKAGE;

  AbstractTypeDeclarationPass(Logger logger) {
    super(logger);
  }

  /**
   * @param name the name of the type declared.
   *    For anonymous classes, name corresponds to the JVM binary class name.
   *    For nominal classes, the dotted form of name is the JLS canonical
   *    type name.
   */
  protected abstract void handleTypeDeclaration(
      TypeScope s, TypeDeclaration d, Name name, boolean isAnonymous);

  protected void findClasses(@Nullable Name outer, TypeScope s, BaseNode n) {
    TypeScope scope;
    if (n instanceof TypeScope) {
      scope = (TypeScope) n;
      scopeToParentScope.put(scope, s);
    } else {
      scope = s;
    }

    NodeType nt = n.getVariant().getNodeType();
    Name childOuter = outer;

    if (n instanceof CallableDeclaration) {
      CallableDeclaration cd = ((CallableDeclaration) n);
      String methodName = cd.getMethodName();
      int methodVariant = cd.getMethodVariant();
      if (methodVariant == 0) {
        Name nameWithoutVariant = childOuter.method(methodName, 1);
        Integer ordinal = methodCounters.get(nameWithoutVariant);
        if (ordinal == null) { ordinal = 0; }
        ordinal += 1;
        methodCounters.put(nameWithoutVariant, ordinal);
        methodVariant = ordinal;
        cd.setMethodVariant(methodVariant);
      }
      childOuter = childOuter.method(methodName, methodVariant);
    }

    switch (nt) {
      case PackageDeclaration:
        PackageDeclarationNode pnode = (PackageDeclarationNode) n;
        Name declaredPackage = Name.DEFAULT_PACKAGE;
        for (BaseNode ident
             : pnode.firstChildWithType(NodeType.PackageName).getChildren()) {
          declaredPackage = declaredPackage.child(
              ident.getValue(), Name.Type.PACKAGE);
        }
        pkg = declaredPackage;
        break;
      case TypeParameter:
        Name typeParameterName = outer.child(
            n.firstChildWithType(NodeType.SimpleTypeName)
             .firstChildWithType(NodeType.Identifier).getValue(),
            Name.Type.TYPE_PARAMETER);
        handleTypeDeclaration(s, (TypeDeclaration) n, typeParameterName, false);
        break;
      case NormalClassDeclaration:
      case EnumDeclaration:
      case NormalInterfaceDeclaration:
      case AnnotationTypeDeclaration: {
        SimpleTypeNameNode nameNode = (SimpleTypeNameNode)
            n.firstChildWithType(NodeType.SimpleTypeName);
        Name declaredClassName = (outer != null ? outer : pkg)
            .child(
                nameNode.firstChildWithType(NodeType.Identifier)
                .getValue(),
                Name.Type.CLASS);
        handleTypeDeclaration(s, (TypeDeclaration) n, declaredClassName, false);
        childOuter = declaredClassName;
        break;
      }
      case EnumConstant:
      case UnqualifiedClassInstanceCreationExpression: {
        ImmutableList<BaseNode> children = n.getChildren();
        int nChildren = children.size();
        BaseNode last = children.get(nChildren - 1);
        Name outerClass = (outer != null ? outer : pkg);
        if (last instanceof ClassBodyNode) {
          // It is an anonymous class.
          Integer ordinal = anonClassCounters.get(outerClass);
          if (ordinal == null) {
            ordinal = 0;
          }
          ordinal += 1;
          anonClassCounters.put(outerClass, ordinal);
          Name anonymousClassName = outerClass.child(
              ordinal.toString(), Name.Type.CLASS);
          handleTypeDeclaration(
              s, (TypeDeclaration) n, anonymousClassName, true);
          // Visit the parameters without the class on the stack.
          for (BaseNode child : children.subList(0, nChildren - 1)) {
            findClasses(outer, scope, child);
          }
          findClasses(anonymousClassName, scope, last);
          return;
        }
        break;
      }
      default:
        break;
    }
    for (BaseNode child : n.getChildren()) {
      findClasses(childOuter, scope, child);
    }
  }

  protected abstract T getResult();

  protected final @Nullable TypeScope getParentOfScope(TypeScope scope) {
    return scopeToParentScope.get(scope);
  }

  protected final Map<TypeScope, TypeScope> getScopeToParentMap() {
    return Collections.unmodifiableMap(
        new IdentityHashMap<>(scopeToParentScope));
  }

  @Override
  public T run(Iterable<? extends CompilationUnitNode> compilationUnits) {
    for (CompilationUnitNode node : compilationUnits) {
      this.pkg = Name.DEFAULT_PACKAGE;
      findClasses(null, node, node);
    }
    return getResult();
  }
}
