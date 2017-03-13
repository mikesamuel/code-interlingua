package com.mikesamuel.cil.ast.passes;

import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import javax.annotation.Nullable;

import com.mikesamuel.cil.ast.j8.ClassBodyNode;
import com.mikesamuel.cil.ast.j8.J8BaseNode;
import com.mikesamuel.cil.ast.j8.J8NodeType;
import com.mikesamuel.cil.ast.j8.PackageDeclarationNode;
import com.mikesamuel.cil.ast.j8.SimpleTypeNameNode;
import com.mikesamuel.cil.ast.j8.traits.CallableDeclaration;
import com.mikesamuel.cil.ast.j8.traits.FileNode;
import com.mikesamuel.cil.ast.j8.traits.TypeDeclaration;
import com.mikesamuel.cil.ast.j8.traits.TypeScope;
import com.mikesamuel.cil.ast.meta.Name;

/**
 * Fires for each class declaration.
 */
abstract class AbstractTypeDeclarationPass<T> extends AbstractPass<T> {
  private final Map<Name, Integer> anonClassCounters = new HashMap<>();
  private final MethodVariantPool methodVariantPool = new MethodVariantPool();
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

  private final void findClasses(
      @Nullable Name outer, @Nullable TypeScope s, J8BaseNode n) {
    TypeScope scope;
    if (n instanceof TypeScope) {
      scope = (TypeScope) n;
      scopeToParentScope.put(scope, s);
    } else {
      scope = s;
    }

    J8NodeType nt = n.getVariant().getNodeType();
    Name childOuter = outer;

    if (n instanceof CallableDeclaration) {
      CallableDeclaration cd = ((CallableDeclaration) n);
      String methodName = cd.getMethodName();
      int methodVariant = cd.getMethodVariant();
      if (methodVariant == 0) {
        childOuter = methodVariantPool.allocateVariant(
            childOuter, methodName);
        cd.setMethodVariant(childOuter.variant);
      } else {
        childOuter = childOuter.method(methodName, methodVariant);
      }
    }

    switch (nt) {
      case PackageDeclaration:
        PackageDeclarationNode pnode = (PackageDeclarationNode) n;
        Name declaredPackage = Name.DEFAULT_PACKAGE;
        for (J8BaseNode ident
             : pnode.firstChildWithType(J8NodeType.PackageName).getChildren()) {
          declaredPackage = declaredPackage.child(
              ident.getValue(), Name.Type.PACKAGE);
        }
        pkg = declaredPackage;
        break;
      case TypeParameter:
        Name typeParameterName = outer.child(
            n.firstChildWithType(J8NodeType.SimpleTypeName)
             .firstChildWithType(J8NodeType.Identifier).getValue(),
            Name.Type.TYPE_PARAMETER);
        handleTypeDeclaration(s, (TypeDeclaration) n, typeParameterName, false);
        break;
      case NormalClassDeclaration:
      case EnumDeclaration:
      case NormalInterfaceDeclaration:
      case AnnotationTypeDeclaration: {
        SimpleTypeNameNode nameNode = (SimpleTypeNameNode)
            n.firstChildWithType(J8NodeType.SimpleTypeName);
        Name declaredClassName = (outer != null ? outer : pkg)
            .child(
                nameNode.firstChildWithType(J8NodeType.Identifier)
                .getValue(),
                Name.Type.CLASS);
        handleTypeDeclaration(s, (TypeDeclaration) n, declaredClassName, false);
        childOuter = declaredClassName;
        break;
      }
      case EnumConstant:
      case UnqualifiedClassInstanceCreationExpression: {
        List<J8BaseNode> children = n.getChildren();
        int nChildren = children.size();
        J8BaseNode last = children.get(nChildren - 1);
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
          for (J8BaseNode child : children.subList(0, nChildren - 1)) {
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
    for (J8BaseNode child : n.getChildren()) {
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

  protected final MethodVariantPool getMethodVariantPool() {
    return this.methodVariantPool;
  }

  @Override
  public T run(Iterable<? extends FileNode> fileNodes) {
    for (FileNode node : fileNodes) {
      this.pkg = Name.DEFAULT_PACKAGE;
      findClasses(null, null, (J8BaseNode) node);
    }
    return getResult();
  }
}
