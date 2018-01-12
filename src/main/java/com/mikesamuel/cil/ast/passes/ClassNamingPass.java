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
import com.mikesamuel.cil.ast.j8.ClassBodyNode;
import com.mikesamuel.cil.ast.j8.ClassMemberDeclarationNode;
import com.mikesamuel.cil.ast.j8.ConstructorBodyNode;
import com.mikesamuel.cil.ast.j8.ConstructorDeclarationNode;
import com.mikesamuel.cil.ast.j8.ConstructorDeclaratorNode;
import com.mikesamuel.cil.ast.j8.EnumConstantNameNode;
import com.mikesamuel.cil.ast.j8.EnumConstantNode;
import com.mikesamuel.cil.ast.j8.EnumDeclarationNode;
import com.mikesamuel.cil.ast.j8.IdentifierNode;
import com.mikesamuel.cil.ast.j8.J8BaseNode;
import com.mikesamuel.cil.ast.j8.J8CallableDeclaration;
import com.mikesamuel.cil.ast.j8.J8NodeType;
import com.mikesamuel.cil.ast.j8.J8TypeDeclaration;
import com.mikesamuel.cil.ast.j8.J8TypeScope;
import com.mikesamuel.cil.ast.j8.MethodBodyNode;
import com.mikesamuel.cil.ast.j8.MethodHeaderNode;
import com.mikesamuel.cil.ast.j8.Mixins;
import com.mikesamuel.cil.ast.j8.ModifierNode;
import com.mikesamuel.cil.ast.j8.NormalClassDeclarationNode;
import com.mikesamuel.cil.ast.j8.SimpleTypeNameNode;
import com.mikesamuel.cil.ast.j8.TypeParameterNode;
import com.mikesamuel.cil.ast.j8.TypeParametersNode;
import com.mikesamuel.cil.ast.j8.VariableDeclaratorIdNode;
import com.mikesamuel.cil.ast.meta.CallableInfo;
import com.mikesamuel.cil.ast.meta.FieldInfo;
import com.mikesamuel.cil.ast.meta.JavaLang;
import com.mikesamuel.cil.ast.meta.MemberInfo;
import com.mikesamuel.cil.ast.meta.MethodDescriptor;
import com.mikesamuel.cil.ast.meta.Name;
import com.mikesamuel.cil.ast.meta.TypeInfo;
import com.mikesamuel.cil.ast.meta.TypeSpecification;
import com.mikesamuel.cil.parser.SourcePosition;

/**
 * Sets the name meta-data for each class declaration encountered.
 */
final class ClassNamingPass
extends AbstractTypeDeclarationPass<ClassNamingPass.DeclarationsAndScopes> {
  private final Map<Name, UnresolvedTypeDeclaration> declaredTypes
      = new LinkedHashMap<>();

  ClassNamingPass(Logger logger) {
    super(logger);
  }

  @Override
  protected void handleTypeDeclaration(
      J8TypeScope scope, J8TypeDeclaration d, Name name, boolean isAnonymous) {
    UnresolvedTypeDeclaration decl = new UnresolvedTypeDeclaration(scope, d);
    UnresolvedTypeDeclaration dupe = declaredTypes.get(name);
    if (dupe == null) {
      declaredTypes.put(name, decl);
    } else {
      SourcePosition opos = ((J8BaseNode) dupe.decl).getSourcePosition();
      error(
          d,
          "Duplicate definition for " + name
          + (opos != null ? " originally defined at " + opos.toString() : ""));
    }
    TypeInfo partialTypeInfo = TypeInfo.builder(name)
        .isAnonymous(isAnonymous)
        .outerClass(Optional.fromNullable(name.getOuterType()))
        .build();
    d.setDeclaredTypeInfo(partialTypeInfo);
    if (d instanceof NormalClassDeclarationNode) {
      ClassBodyNode body = d.firstChildWithType(ClassBodyNode.class);
      boolean hasConstructor = false;
      for (J8BaseNode child : body.getChildren()) {
        if (child instanceof ClassMemberDeclarationNode
            && child.firstChildWithType(ConstructorDeclarationNode.class)
               != null) {
          hasConstructor = true;
          break;
        }
      }
      // Insert a zero argument public constructor for any class that lacks one.
      if (!hasConstructor) {
        // TODO: Move this into a separate pass that runs just before the
        // typing pass to introduce all synthetics including:
        //   1. implied constructors in normal classes and enum classes
        //   2. bridge methods for generic overrides a la example 15.12.4.5-1
        //   docs.oracle.com/javase/specs/jls/se8/html/jls-15.html#jls-15.12.4.5
        //   3. static accessors for private access between inner and outer
        //   classes.
        // This will require moving the code that allocates method variants to
        // a common type that persists across multiple passes.
        ConstructorDeclarationNode noopPublicCtor =
            ConstructorDeclarationNode.Variant.Declaration.buildNode(
                ModifierNode.Variant.Public.buildNode(),
                ConstructorDeclaratorNode.Variant
                .TypeParametersSimpleTypeNameLpFormalParameterListRp
                    .buildNode(
                        SimpleTypeNameNode.Variant.Identifier.buildNode(
                            IdentifierNode.Variant.Builtin.buildNode(
                                name.identifier))),
                ConstructorBodyNode.Variant
                .LcExplicitConstructorInvocationBlockStatementsRc.buildNode());

        body.add(
            ClassMemberDeclarationNode.Variant.ConstructorDeclaration.buildNode(
                noopPublicCtor));
      }
    }
  }


  private ImmutableList<MemberInfo> findMembers(
      Name declaringClass, J8TypeDeclaration declaration) {
    J8BaseNode body = declaration.getChild(declaration.getNChildren() - 1);

    ImmutableList.Builder<MemberInfo> b = ImmutableList.builder();

    for (J8BaseNode bodyElement : body.getChildren()) {
      processMembers(declaringClass, declaration, bodyElement, b);
    }

    if (declaration instanceof EnumDeclarationNode) {
      // Add methods specified in
      // https://docs.oracle.com/javase/specs/jls/se8/html/jls-8.html#jls-8.9.3
      MethodVariantPool methodVariantPool = this.getMethodVariantPool();

      // public static E[] values();
      CallableInfo values = new CallableInfo(
          Modifier.PUBLIC | Modifier.STATIC,
          methodVariantPool.allocateVariant(declaringClass, "values"),
          ImmutableList.of(), false);
      values.setDescriptor(
          MethodDescriptor.builder()
              .withReturnType(declaringClass, 1)
              .build());
      values.setFormalTypes(ImmutableList.of());
      values.setThrownTypes(ImmutableList.of());
      values.setReturnType(
          TypeSpecification.unparameterized(declaringClass).withNDims(1));

      // public static E valueOf(String name);
      Name valueOfName = methodVariantPool.allocateVariant(
          declaringClass, "valueOf");
      CallableInfo valueOf = new CallableInfo(
          Modifier.PUBLIC | Modifier.STATIC,
          valueOfName, ImmutableList.of(),
          false);
      valueOf.setDescriptor(
          MethodDescriptor.builder()
             .addFormalParameter(JavaLang.JAVA_LANG_STRING.rawName, 0)
             .withReturnType(declaringClass, 0)
             .build());
      valueOf.setFormalTypes(ImmutableList.of(JavaLang.JAVA_LANG_STRING));
      valueOf.setThrownTypes(ImmutableList.of());
      valueOf.setReturnType(TypeSpecification.unparameterized(declaringClass));

      b.add(values);
      b.add(valueOf);
    }

    return b.build();
  }

  private static void processMembers(
      Name declaringClass, J8TypeDeclaration declaration,
      J8BaseNode bodyElement, ImmutableList.Builder<MemberInfo> b) {
    J8NodeType nt = bodyElement.getNodeType();
    switch (nt) {
      case ConstructorDeclaration:
      case MethodDeclaration:
      case InterfaceMethodDeclaration:
      case InstanceInitializer:
      case StaticInitializer: {
        J8CallableDeclaration cd = (J8CallableDeclaration) bodyElement;
        int mods = 0;
        if (nt == J8NodeType.StaticInitializer) {
          mods |= Modifier.STATIC | Modifier.PRIVATE;
        } else if (nt == J8NodeType.InstanceInitializer) {
          mods |= Modifier.PRIVATE;
        } else if (nt == J8NodeType.ConstructorDeclaration
                   && declaration instanceof EnumDeclarationNode) {
          mods |= Modifier.PRIVATE;
        } else if (nt == J8NodeType.InterfaceMethodDeclaration) {
          mods |= Modifier.PUBLIC;
          MethodBodyNode mb = cd.firstChildWithType(MethodBodyNode.class);
          if (mb != null && mb.getVariant() == MethodBodyNode.Variant.Sem) {
            // If the method is not default, then it is abstract.
            mods |= Modifier.ABSTRACT;
          }
        }
        TypeParametersNode typeParameters = null;
        for (J8BaseNode child : bodyElement.getChildren()) {
          if (child instanceof ModifierNode) {
            mods |= ModifierNodes.modifierBits(
                ((ModifierNode) child).getVariant());
          } else if (child instanceof MethodHeaderNode) {
            typeParameters = child.firstChildWithType(TypeParametersNode.class);
          } else if (child instanceof TypeParametersNode) {
            typeParameters = (TypeParametersNode) child;
          }
        }
        ImmutableList.Builder<Name> typeParametersList =
            ImmutableList.builder();
        if (typeParameters != null) {
          for (TypeParameterNode param
              : typeParameters.finder(TypeParameterNode.class)
                .exclude(J8NodeType.TypeBound, J8NodeType.Annotation)
                .find()) {
            typeParametersList.add(
                param.getDeclaredTypeInfo().canonName);
          }
        }
        b.add(new CallableInfo(
            mods,
            declaringClass.method(
                Mixins.getMethodName(cd), cd.getMethodVariant()),
            typeParametersList.build(),
            nt == J8NodeType.InstanceInitializer
            || nt == J8NodeType.StaticInitializer));
        break;
      }
      case FieldDeclaration:
      case ConstantDeclaration: {
        int mods = 0;
        for (J8BaseNode child : bodyElement.getChildren()) {
          if (child instanceof ModifierNode) {
            mods |= ModifierNodes.modifierBits(
                ((ModifierNode) child).getVariant());
          }
        }
        for (VariableDeclaratorIdNode declId
             : bodyElement.finder(VariableDeclaratorIdNode.class)
                  .exclude(
                     J8NodeType.Type, J8NodeType.Modifier,
                     J8NodeType.VariableInitializer)
                  .find()) {
          IdentifierNode id =
              (IdentifierNode) declId.firstChildWithType(J8NodeType.Identifier);
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
      case ClassMemberDeclaration:
      case EnumBodyDeclarations:
      case EnumConstantList:
      case InterfaceMemberDeclaration:
        for (J8BaseNode child : bodyElement.getChildren()) {
          processMembers(declaringClass, declaration, child, b);
        }
        break;
      case ClassDeclaration:
      case InterfaceDeclaration:
        break;
      case TemplateDirectives:
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
      if (outerTypeName != null) {
        switch (declName.type) {
          case CLASS:
            innerTypes.put(outerTypeName, declName);
            continue;
          case TYPE_PARAMETER:
            if (declName.parent == outerTypeName) {
              parameters.put(outerTypeName, declName);
            }
            continue;
          case AMBIGUOUS:
          case FIELD:
          case LOCAL:
          case METHOD:
          case PACKAGE:
            break;
        }
        throw new AssertionError(declName);
      }
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
    final Map<J8TypeScope, J8TypeScope> scopeToParent;

    DeclarationsAndScopes(
        ImmutableMap<Name, UnresolvedTypeDeclaration> declarations,
        Map<J8TypeScope, J8TypeScope> scopeToParent) {
      this.declarations = declarations;
      this.scopeToParent = scopeToParent;
    }
  }
}
