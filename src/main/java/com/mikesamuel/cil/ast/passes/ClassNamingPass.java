package com.mikesamuel.cil.ast.passes;

import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Multimap;
import com.mikesamuel.cil.ast.BaseNode;
import com.mikesamuel.cil.ast.EnumConstantNameNode;
import com.mikesamuel.cil.ast.EnumConstantNode;
import com.mikesamuel.cil.ast.EnumDeclarationNode;
import com.mikesamuel.cil.ast.IdentifierNode;
import com.mikesamuel.cil.ast.ModifierNode;
import com.mikesamuel.cil.ast.NodeType;
import com.mikesamuel.cil.ast.VariableDeclaratorIdNode;
import com.mikesamuel.cil.ast.meta.CallableInfo;
import com.mikesamuel.cil.ast.meta.FieldInfo;
import com.mikesamuel.cil.ast.meta.MemberInfo;
import com.mikesamuel.cil.ast.meta.Name;
import com.mikesamuel.cil.ast.meta.TypeInfo;
import com.mikesamuel.cil.ast.traits.CallableDeclaration;
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
    TypeInfo partialTypeInfo = TypeInfo.builder(name)
        .isAnonymous(isAnonymous)
        .outerClass(Optional.fromNullable(name.getOuterType()))
        .build();
    d.setDeclaredTypeInfo(partialTypeInfo);
  }


  private static ImmutableList<MemberInfo> findMembers(
      Name declaringClass, TypeDeclaration declaration) {
    ImmutableList<BaseNode> declarationChildren = ((BaseNode) declaration)
        .getChildren();
    BaseNode body = declarationChildren.get(declarationChildren.size() - 1);

    ImmutableList.Builder<MemberInfo> b = ImmutableList.builder();

    for (BaseNode bodyElement : body.getChildren()) {
      processMembers(declaringClass, declaration, bodyElement, b);
    }
    return b.build();
  }

  private static void processMembers(
      Name declaringClass, TypeDeclaration declaration, BaseNode bodyElement,
      ImmutableList.Builder<MemberInfo> b) {
    NodeType nt = bodyElement.getNodeType();
    switch (nt) {
      case ConstructorDeclaration:
      case MethodDeclaration:
      case InterfaceMethodDeclaration:
      case InstanceInitializer:
      case StaticInitializer: {
        CallableDeclaration cd = (CallableDeclaration) bodyElement;
        int mods = 0;
        if (nt == NodeType.StaticInitializer) {
          mods |= Modifier.STATIC | Modifier.PRIVATE;
        } else if (nt == NodeType.InstanceInitializer) {
          mods |= Modifier.PRIVATE;
        } else if (nt == NodeType.ConstructorDeclaration
                   && declaration instanceof EnumDeclarationNode) {
          mods |= Modifier.PRIVATE;
        }
        for (BaseNode child : bodyElement.getChildren()) {
          if (child instanceof ModifierNode) {
            mods |= ModifierNodes.modifierBits(
                ((ModifierNode) child).getVariant());
          }
        }
        b.add(new CallableInfo(
            mods,
            declaringClass.method(cd.getMethodName(), cd.getMethodDescriptor())
            ));
        break;
      }
      case FieldDeclaration:
      case ConstantDeclaration: {
        int mods = 0;
        for (BaseNode child : bodyElement.getChildren()) {
          if (child instanceof ModifierNode) {
            mods |= ModifierNodes.modifierBits(
                ((ModifierNode) child).getVariant());
          }
        }
        for (VariableDeclaratorIdNode declId
             : bodyElement.finder(VariableDeclaratorIdNode.class)
                  .exclude(
                     NodeType.Type, NodeType.Modifier,
                     NodeType.VariableInitializer)
                  .find()) {
          IdentifierNode id =
              (IdentifierNode) declId.firstChildWithType(NodeType.Identifier);
          if (id != null) {
            Name name = declaringClass.child(id.getValue(), Name.Type.FIELD);
            declId.setDeclaredExpressionName(name);
            b.add(new FieldInfo(mods, name));
          }
        }
        break;
      }
      case EnumConstant: {
        EnumConstantNameNode nameNode = bodyElement.firstChildWithType(
            EnumConstantNameNode.class);
        if (nameNode != null) {
          int mods = Modifier.PUBLIC | Modifier.STATIC | Modifier.FINAL;
          ImmutableList<IdentifierNode> idents =
              nameNode.finder(IdentifierNode.class).find();
          if (!idents.isEmpty()) {
            Preconditions.checkState(idents.size() == 1);
            String ident = idents.get(0).getValue();
            Name name = declaringClass.child(ident, Name.Type.FIELD);
            ((EnumConstantNode) bodyElement).setDeclaredExpressionName(name);
            b.add(new FieldInfo(mods, name));
          }
        }
        break;
      }
      case AnnotationTypeMemberDeclaration:
      case ClassBodyDeclaration:
      case ClassMemberDeclaration:
      case EnumBodyDeclarations:
      case EnumConstantList:
      case InterfaceMemberDeclaration:
        for (BaseNode child : bodyElement.getChildren()) {
          processMembers(declaringClass, declaration, child, b);
        }
        break;
      case ClassDeclaration:
      case InterfaceDeclaration:
        break;
      default:
        throw new AssertionError(bodyElement);
    }
  }

  @Override
  protected DeclarationsAndScopes getResult() {
    Multimap<Name, Name> innerTypes = ArrayListMultimap.create();
    Multimap<Name, Name> parameters = ArrayListMultimap.create();
    for (Name declName : declaredTypes.keySet()) {
      Name outerTypeName = declName.getOuterType();
      (declName.type == Name.Type.CLASS ? innerTypes : parameters)
          .put(outerTypeName, declName);
    }

    for (Map.Entry<Name, UnresolvedTypeDeclaration> e
         : declaredTypes.entrySet()) {
      Name declName = e.getKey();
      UnresolvedTypeDeclaration decl = e.getValue();
      if (decl.stage == Stage.UNRESOLVED) {
        Optional<Name> outerType = declName.type == Name.Type.CLASS
            ? Optional.fromNullable(declName.getOuterType())
            : Optional.absent();
        char nameChar0 = declName.identifier.charAt(0);
        boolean isAnonymous = '0' <= nameChar0 && nameChar0 <= '9';
        // Does not include those inherited.
        ImmutableList<Name> innerTypeList = ImmutableList.copyOf(
            innerTypes.get(declName));
        ImmutableList<Name> parameterList = ImmutableList.copyOf(
            parameters.get(declName));

        ImmutableList<MemberInfo> members;
        if (declName.type == Name.Type.TYPE_PARAMETER) {
          members = ImmutableList.of();  // Parameters do not declare members.
        } else {
          members = findMembers(declName, decl.decl);
        }

        decl.decl.setDeclaredTypeInfo(
            TypeInfo.builder(declName)
               .modifiers(Modifier.PUBLIC)  // OPTIMISTIC
               .isAnonymous(isAnonymous)
               .outerClass(outerType)
               .innerClasses(innerTypeList)
               .parameters(parameterList)
               .declaredMembers(members)
               .build());
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
