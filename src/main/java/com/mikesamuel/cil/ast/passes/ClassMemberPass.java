package com.mikesamuel.cil.ast.passes;

import java.util.logging.Logger;

import javax.annotation.Nullable;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.mikesamuel.cil.ast.j8.ArrayTypeNode;
import com.mikesamuel.cil.ast.j8.ClassOrInterfaceTypeNode;
import com.mikesamuel.cil.ast.j8.ClassOrInterfaceTypeToInstantiateNode;
import com.mikesamuel.cil.ast.j8.ClassTypeNode;
import com.mikesamuel.cil.ast.j8.ConstantDeclarationNode;
import com.mikesamuel.cil.ast.j8.EnumConstantNameNode;
import com.mikesamuel.cil.ast.j8.EnumConstantNode;
import com.mikesamuel.cil.ast.j8.FieldDeclarationNode;
import com.mikesamuel.cil.ast.j8.FloatingPointTypeNode;
import com.mikesamuel.cil.ast.j8.FormalParameterListNode;
import com.mikesamuel.cil.ast.j8.FormalParameterNode;
import com.mikesamuel.cil.ast.j8.IdentifierNode;
import com.mikesamuel.cil.ast.j8.IntegralTypeNode;
import com.mikesamuel.cil.ast.j8.InterfaceTypeNode;
import com.mikesamuel.cil.ast.j8.J8BaseNode;
import com.mikesamuel.cil.ast.j8.J8CallableDeclaration;
import com.mikesamuel.cil.ast.j8.J8FileNode;
import com.mikesamuel.cil.ast.j8.J8MemberDeclaration;
import com.mikesamuel.cil.ast.j8.J8NodeType;
import com.mikesamuel.cil.ast.j8.J8NodeVariant;
import com.mikesamuel.cil.ast.j8.J8TypeDeclaration;
import com.mikesamuel.cil.ast.j8.J8TypeScope;
import com.mikesamuel.cil.ast.j8.J8WholeType;
import com.mikesamuel.cil.ast.j8.LastFormalParameterNode;
import com.mikesamuel.cil.ast.j8.Mixins;
import com.mikesamuel.cil.ast.j8.NumericTypeNode;
import com.mikesamuel.cil.ast.j8.PrimaryNode;
import com.mikesamuel.cil.ast.j8.PrimitiveTypeNode;
import com.mikesamuel.cil.ast.j8.ReferenceTypeNode;
import com.mikesamuel.cil.ast.j8.ResultNode;
import com.mikesamuel.cil.ast.j8.TypeArgumentsNode;
import com.mikesamuel.cil.ast.j8.TypeNode;
import com.mikesamuel.cil.ast.j8.TypeVariableNode;
import com.mikesamuel.cil.ast.j8.UnannTypeNode;
import com.mikesamuel.cil.ast.j8.VariableDeclaratorIdNode;
import com.mikesamuel.cil.ast.meta.CallableInfo;
import com.mikesamuel.cil.ast.meta.FieldInfo;
import com.mikesamuel.cil.ast.meta.MemberInfo;
import com.mikesamuel.cil.ast.meta.MethodDescriptor;
import com.mikesamuel.cil.ast.meta.Name;
import com.mikesamuel.cil.ast.meta.StaticType;
import com.mikesamuel.cil.ast.meta.StaticType.TypePool;
import com.mikesamuel.cil.ast.meta.TypeInfo;
import com.mikesamuel.cil.ast.meta.TypeNameResolver;
import com.mikesamuel.cil.ast.meta.TypeSpecification;
import com.mikesamuel.cil.parser.SList;

/**
 * Attaches descriptor and return type info to callables and attaches type
 * info to fields.
 */
final class ClassMemberPass extends AbstractPass<Void> {

  final TypePool typePool;

  ClassMemberPass(Logger logger, TypePool typePool) {
    super(logger);
    this.typePool = typePool;
  }

  void run(
      J8BaseNode node, @Nullable TypeInfo typeInfo,
      @Nullable TypeNameResolver nr,
      @Nullable SList<J8NodeVariant> ancestors) {
    TypeNameResolver childResolver = nr;
    TypeInfo childTypeInfo = typeInfo;
    if (node instanceof J8TypeScope) {
      childResolver = ((J8TypeScope) node).getTypeNameResolver();
    }
    if (node instanceof J8WholeType
        // Skip partial types like those in (expr).new Partial.Inner.Type()
        && !(ancestors != null
             && ancestors.x ==
             ClassOrInterfaceTypeToInstantiateNode.Variant
             .ClassOrInterfaceTypeDiamond
             && ancestors.prev != null
             && ancestors.prev.prev != null
             && ancestors.prev.prev.x ==
             // TODO: Rethink inner class creation in the grammer.
             // ClassOrInterfaceTypeToInstantiate delegates to
             // ClassOrInterfaceType which is a WholeType which is awkward.
             // Option 1: separate inner class creation from static class
             //     creation
             // Option 2: rework ClassOrInterfaceToInstantiate so that it does
             //     not delegate and rely on the StaticType of the whole
             //     creation expression to specify the type created.
             PrimaryNode.Variant.InnerClassCreation)) {
      processStaticType(node, nr);
      return;
    }
    if (node instanceof J8TypeDeclaration) {
      childTypeInfo = ((J8TypeDeclaration) node).getDeclaredTypeInfo();
    }

    // Compute whole types for method results and field types.
    SList<J8NodeVariant> ancestorsAndNode = SList.append(
        ancestors, node.getVariant());
    for (J8BaseNode child : node.getChildren()) {
      run(child, childTypeInfo, childResolver, ancestorsAndNode);
    }

    if (node instanceof J8CallableDeclaration) {
      J8CallableDeclaration cd = (J8CallableDeclaration) node;
      Name methodName = childTypeInfo.canonName.method(
          Mixins.getMethodName(cd), cd.getMethodVariant());
      CallableInfo info = (CallableInfo) memberWithName(
          childTypeInfo, methodName);
      if (info != null) {
        StaticType returnType;
        if (methodName.identifier.startsWith("<")) {
          // A special method.
          returnType = StaticType.T_VOID;
        } else {
          J8WholeType wholeReturnType = findWholeType(node);
          returnType = wholeReturnType != null
              ? wholeReturnType.getStaticType() : null;
          if (returnType == null) {
            error(node, "Cannot compute return type");
            returnType = StaticType.ERROR_TYPE;
          }
        }

        FormalParameterListNode paramList = null;
        for (FormalParameterListNode ps
            : node.finder(FormalParameterListNode.class)
            .exclude(J8NodeType.MethodBody, J8NodeType.ConstructorBody)
               .find()) {
          Preconditions.checkState(paramList == null);
          paramList = ps;
        }

        boolean hasErrorType = false;
        MethodDescriptor.Builder descriptor = MethodDescriptor.builder();
        ImmutableList.Builder<TypeSpecification> formalTypes =
            ImmutableList.builder();

        if (paramList != null) {
          for (J8BaseNode formal
              : Iterables.concat(
                  node.finder(FormalParameterNode.class)
                  .exclude(
                      J8NodeType.MethodBody,
                      J8NodeType.ConstructorBody, J8NodeType.BlockStatements)
                  .find(),
                  node.finder(LastFormalParameterNode.class)
                  .exclude(
                      J8NodeType.MethodBody,
                      J8NodeType.ConstructorBody, J8NodeType.BlockStatements)
                  .find())) {
            boolean isVariadic = false;
            if (formal instanceof LastFormalParameterNode) {
              if (LastFormalParameterNode.Variant.Variadic
                  == formal.getVariant()) {
                isVariadic = true;
              } else {
                continue;
              }
            }
            J8WholeType formalType = findWholeType(formal);
            StaticType staticType =
                formalType != null ? formalType.getStaticType() : null;
            if (staticType == null) {
              staticType = StaticType.ERROR_TYPE;
            }
            if (isVariadic) {
              // Promote T... formals to arrays.
              staticType = typePool.type(
                  staticType.typeSpecification.arrayOf(),
                  formal.getSourcePosition(), logger);
              info.setVariadic(true);
            }
            formalTypes.add(staticType.typeSpecification);
            StaticType staticErasedType = staticType.toErasedType();
            if (StaticType.ERROR_TYPE.equals(staticErasedType)) {
              hasErrorType = true;
              break;
            }
            descriptor.addFormalParameter(
                staticErasedType.typeSpecification.rawName,
                staticErasedType.typeSpecification.nDims);
          }
        }
        if (!hasErrorType) {
          StaticType returnErasedType = returnType.toErasedType();
          if (StaticType.ERROR_TYPE.equals(returnErasedType)) {
            hasErrorType = true;
          } else {
            descriptor.withReturnType(
                returnErasedType.typeSpecification.rawName,
                returnErasedType.typeSpecification.nDims);
          }
        } else {
          error(node, "Could not compute method descriptor");
        }

        if (!hasErrorType) {
          info.setDescriptor(descriptor.build());
        }
        info.setReturnType(returnType.typeSpecification);
        info.setFormalTypes(formalTypes.build());
        cd.setMemberInfo(info);
      } else {
        error(node, "Missing member info for " + methodName);
      }
    } else if (node instanceof FieldDeclarationNode
            || node instanceof ConstantDeclarationNode) {
      for (VariableDeclaratorIdNode var
           : node.finder(VariableDeclaratorIdNode.class)
           .exclude(J8NodeType.VariableInitializer)
           .find()) {
        IdentifierNode ident = var.firstChildWithType(IdentifierNode.class);
        if (ident != null) {
          Name fieldName = childTypeInfo.canonName.child(
              ident.getValue(), Name.Type.FIELD);
          FieldInfo info = (FieldInfo) memberWithName(
              childTypeInfo, fieldName);
          if (info != null) {
            J8WholeType valueType = findWholeType(node);
            if (valueType != null) {
              info.setValueType(valueType.getStaticType().typeSpecification);
            }
            ((J8MemberDeclaration) node).setMemberInfo(info);
          } else {
            error(node, "Missing member info for " + fieldName);
          }
        }
      }
    } else if (node instanceof EnumConstantNode) {
      EnumConstantNode constant = (EnumConstantNode) node;
      EnumConstantNameNode nameNode =
          constant.firstChildWithType(EnumConstantNameNode.class);
      if (nameNode != null) {
        Optional<IdentifierNode> identNode =
            nameNode.finder(IdentifierNode.class).findOne();
        if (identNode.isPresent()) {
          FieldInfo info = (FieldInfo) memberWithName(
              typeInfo,
              typeInfo.canonName.child(
                  identNode.get().getValue(), Name.Type.FIELD));
          info.setValueType(TypeSpecification.unparameterized(
              typeInfo.canonName));
        }
      }
    }
    // TODO annotation elements

  }

  private J8WholeType findWholeType(J8BaseNode node) {
    J8WholeType wholeType = null;
    for (J8WholeType t
        : node.finder(J8WholeType.class)
        .exclude(J8NodeType.Throws, J8NodeType.TypeParameters,
            J8NodeType.MethodBody, J8NodeType.ConstructorBody,
            J8NodeType.BlockStatements,
            J8NodeType.VariableInitializer,
            J8NodeType.FormalParameterList, J8NodeType.Result,
            J8NodeType.Type)
        .find()) {
      if (wholeType != null) {
        error(
            t,
            "Duplicate type at "
            + wholeType.getSourcePosition());
      } else {
        wholeType = t;
      }
    }
    return wholeType;
  }

  private static MemberInfo memberWithName(TypeInfo typeInfo, Name name) {
    for (MemberInfo mi : typeInfo.getDeclaredMembers()) {
      if (mi.canonName.equals(name)) {
        return mi;
      }
    }
    return null;
  }

  StaticType processStaticType(J8BaseNode node, TypeNameResolver r) {
    Class<? extends J8BaseNode> delegateType = null;
    StaticType t = null;
    switch (node.getNodeType()) {
      case Result: {
        ResultNode.Variant v = ((ResultNode) node).getVariant();
        switch (v) {
          case Void:
            t = StaticType.T_VOID;
            break;
          case UnannType:
            delegateType = UnannTypeNode.class;
            break;
        }
        break;
      }
      case PrimitiveType: {
        PrimitiveTypeNode.Variant v = ((PrimitiveTypeNode) node).getVariant();
        switch (v) {
          case AnnotationBoolean:
            t = StaticType.T_BOOLEAN;
            break;
          case AnnotationNumericType:
            delegateType = NumericTypeNode.class;
            break;
        }
        break;
      }
      case NumericType: {
        NumericTypeNode.Variant v = ((NumericTypeNode) node).getVariant();
        switch (v) {
          case FloatingPointType:
            delegateType = FloatingPointTypeNode.class;
            break;
          case IntegralType:
            delegateType = IntegralTypeNode.class;
            break;
        }
        break;
      }
      case IntegralType: {
        IntegralTypeNode.Variant v = ((IntegralTypeNode) node).getVariant();
        switch (v) {
          case Byte:  t = StaticType.T_BYTE; break;
          case Char:  t = StaticType.T_CHAR; break;
          case Int:   t = StaticType.T_INT; break;
          case Long:  t = StaticType.T_LONG; break;
          case Short: t = StaticType.T_SHORT; break;
        }
        break;
      }
      case FloatingPointType: {
        FloatingPointTypeNode.Variant v =
            ((FloatingPointTypeNode) node).getVariant();
        switch (v) {
          case Double: t = StaticType.T_DOUBLE; break;
          case Float:  t = StaticType.T_FLOAT; break;
        }
        break;
      }
      case ReferenceType: {
        ReferenceTypeNode.Variant v = ((ReferenceTypeNode) node).getVariant();
        switch (v) {
          case ArrayType:
            delegateType = ArrayTypeNode.class;
            break;
          case ClassOrInterfaceType:
            delegateType = ClassOrInterfaceTypeNode.class;
            break;
          case TypeVariable:
            delegateType = TypeVariableNode.class;
            break;
        }
        break;
      }
      case ClassOrInterfaceType: {
        TypeSpecification spec = AmbiguousNames.typeSpecificationOf(
            node, r, logger);
        t = typePool.type(spec, node.getSourcePosition(), logger);
        // step into type arguments to attach metadata
        // TODO: do we need to step into annotations?
        TypeArgumentsNode args = node.firstChildWithType(
            TypeArgumentsNode.class);
        if (args != null) {
          for (J8WholeType argType : args.finder(J8WholeType.class)
              .exclude(J8WholeType.class)
              .find()) {
            processStaticType((J8BaseNode) argType, r);
          }
        }
        break;
      }
      case ClassType: {
        ClassTypeNode.Variant v = ((ClassTypeNode) node).getVariant();
        switch (v) {
          case ClassOrInterfaceType:
            delegateType = ClassOrInterfaceTypeNode.class;
            break;
        }
        break;
      }
      case InterfaceType: {
        InterfaceTypeNode.Variant v = ((InterfaceTypeNode) node).getVariant();
        switch (v) {
          case ClassOrInterfaceType:
            delegateType = ClassOrInterfaceTypeNode.class;
            break;
        }
        break;
      }
      case TypeVariable: {
        TypeVariableNode.Variant v = ((TypeVariableNode) node).getVariant();
        switch (v) {
          case AnnotationIdentifier:
            IdentifierNode ident = node.firstChildWithType(
                IdentifierNode.class);
            Name name = Name.root(ident.getValue(), Name.Type.TYPE_PARAMETER);
            ImmutableList<Name> canonNames = r.lookupTypeName(name);
            switch (canonNames.size()) {
              case 0:
                error(ident, "Undefined type variable name " + name);
                t = StaticType.ERROR_TYPE;
                break;
              default:
                error(ident, "Ambiguous type name: " + canonNames);
                //$FALL-THROUGH$
              case 1:
                Name canonName = canonNames.get(0);
                if (canonName.type != Name.Type.TYPE_PARAMETER) {
                  error(
                      ident,
                      "Type variable name does not resolve to a type parameter "
                      + canonName);
                  t = StaticType.ERROR_TYPE;
                } else {
                  t = typePool.type(
                      TypeSpecification.unparameterized(canonName),
                      ident.getSourcePosition(),
                      logger);
                }
                break;
            }
        }
        break;
      }
      case ArrayType: {
        TypeNode elementTypeNode = node.firstChildWithType(TypeNode.class);
        if (elementTypeNode == null) {
          error(node, "Cannot find element type");
          t = StaticType.ERROR_TYPE;
        } else {
          StaticType elementType = processStaticType(elementTypeNode, r);
          if (StaticType.ERROR_TYPE.equals(elementType)) {
            t = StaticType.ERROR_TYPE;
          } else {
            TypeSpecification spec = elementType.typeSpecification.arrayOf();
            t = typePool.type(spec, node.getSourcePosition(), logger);
          }
        }
        break;
      }
      case UnannType: {
        UnannTypeNode.Variant v = ((UnannTypeNode) node).getVariant();
        switch (v) {
          case NotAtType:
            delegateType = TypeNode.class;
            break;
        }
        break;
      }
      case Type: {
        TypeNode.Variant v = ((TypeNode) node).getVariant();
        switch (v) {
          case PrimitiveType:
            delegateType = PrimitiveTypeNode.class;
            break;
          case ReferenceType:
            delegateType = ReferenceTypeNode.class;
            break;
        }
        break;
      }
      default:
        break;
    }
    if (delegateType != null) {
      J8BaseNode delegate = node.firstChildWithType(delegateType);
      if (delegate != null) {
        t = processStaticType(delegate, r);
      } else {
        error(node, "Partial type AST");
        t = StaticType.ERROR_TYPE;
      }
    }
    if (t == null) {
      error(node, "Malformed type for node of type " + node.getNodeType());
      t = StaticType.ERROR_TYPE;
    }
    if (node instanceof J8WholeType) {
      ((J8WholeType) node).setStaticType(t);
    }
    return t;
  }

  @Override
  public Void run(Iterable<? extends J8FileNode> fileNodes) {
    for (J8FileNode fileNode : fileNodes) {
      run((J8BaseNode) fileNode, null, null, null);
    }
    return null;
  }
}
