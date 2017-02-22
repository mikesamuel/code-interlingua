package com.mikesamuel.cil.ast.passes;

import java.util.logging.Logger;

import javax.annotation.Nullable;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.mikesamuel.cil.ast.ArrayTypeNode;
import com.mikesamuel.cil.ast.BaseNode;
import com.mikesamuel.cil.ast.ClassOrInterfaceTypeNode;
import com.mikesamuel.cil.ast.ClassOrInterfaceTypeToInstantiateNode;
import com.mikesamuel.cil.ast.ClassTypeNode;
import com.mikesamuel.cil.ast.ConstantDeclarationNode;
import com.mikesamuel.cil.ast.EnumConstantNameNode;
import com.mikesamuel.cil.ast.EnumConstantNode;
import com.mikesamuel.cil.ast.FieldDeclarationNode;
import com.mikesamuel.cil.ast.FloatingPointTypeNode;
import com.mikesamuel.cil.ast.FormalParameterListNode;
import com.mikesamuel.cil.ast.FormalParameterNode;
import com.mikesamuel.cil.ast.IdentifierNode;
import com.mikesamuel.cil.ast.IntegralTypeNode;
import com.mikesamuel.cil.ast.InterfaceTypeNode;
import com.mikesamuel.cil.ast.LastFormalParameterNode;
import com.mikesamuel.cil.ast.NodeType;
import com.mikesamuel.cil.ast.NodeVariant;
import com.mikesamuel.cil.ast.NumericTypeNode;
import com.mikesamuel.cil.ast.PrimaryNode;
import com.mikesamuel.cil.ast.PrimitiveTypeNode;
import com.mikesamuel.cil.ast.ReferenceTypeNode;
import com.mikesamuel.cil.ast.ResultNode;
import com.mikesamuel.cil.ast.TypeArgumentsNode;
import com.mikesamuel.cil.ast.TypeNode;
import com.mikesamuel.cil.ast.TypeVariableNode;
import com.mikesamuel.cil.ast.UnannTypeNode;
import com.mikesamuel.cil.ast.VariableDeclaratorIdNode;
import com.mikesamuel.cil.ast.meta.CallableInfo;
import com.mikesamuel.cil.ast.meta.FieldInfo;
import com.mikesamuel.cil.ast.meta.MemberInfo;
import com.mikesamuel.cil.ast.meta.Name;
import com.mikesamuel.cil.ast.meta.StaticType;
import com.mikesamuel.cil.ast.meta.StaticType.TypePool;
import com.mikesamuel.cil.ast.meta.TypeInfo;
import com.mikesamuel.cil.ast.meta.TypeNameResolver;
import com.mikesamuel.cil.ast.meta.TypeSpecification;
import com.mikesamuel.cil.ast.traits.CallableDeclaration;
import com.mikesamuel.cil.ast.traits.FileNode;
import com.mikesamuel.cil.ast.traits.MemberDeclaration;
import com.mikesamuel.cil.ast.traits.TypeDeclaration;
import com.mikesamuel.cil.ast.traits.TypeScope;
import com.mikesamuel.cil.ast.traits.WholeType;
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
      BaseNode node, @Nullable TypeInfo typeInfo, TypeNameResolver nr,
      @Nullable SList<NodeVariant> ancestors) {
    TypeNameResolver childResolver = nr;
    TypeInfo childTypeInfo = typeInfo;
    if (node instanceof TypeScope) {
      childResolver = ((TypeScope) node).getTypeNameResolver();
    }
    if (node instanceof WholeType
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
    if (node instanceof TypeDeclaration) {
      childTypeInfo = ((TypeDeclaration) node).getDeclaredTypeInfo();
    }

    // Compute whole types for method results and field types.
    SList<NodeVariant> ancestorsAndNode = SList.append(
        ancestors, node.getVariant());
    for (BaseNode child : node.getChildren()) {
      run(child, childTypeInfo, childResolver, ancestorsAndNode);
    }

    if (node instanceof CallableDeclaration) {
      CallableDeclaration cd = (CallableDeclaration) node;
      Name methodName = childTypeInfo.canonName.method(
          cd.getMethodName(), cd.getMethodVariant());
      CallableInfo info = (CallableInfo) memberWithName(
          childTypeInfo, methodName);
      if (info != null) {
        StaticType returnType;
        if (cd.getMethodName().startsWith("<")) {
          // A special method.
          returnType = StaticType.T_VOID;
        } else {
          WholeType wholeReturnType = findWholeType(node);
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
            .exclude(NodeType.MethodBody, NodeType.ConstructorBody)
               .find()) {
          Preconditions.checkState(paramList == null);
          paramList = ps;
        }

        boolean hasErrorType = false;
        String errorDesc = StaticType.ERROR_TYPE.toDescriptor();
        StringBuilder descriptor = new StringBuilder();
        ImmutableList.Builder<TypeSpecification> formalTypes =
            ImmutableList.builder();

        if (paramList == null) {
          descriptor.append("()");
        } else {
          descriptor.append('(');
          for (BaseNode formal
              : Iterables.concat(
                  node.finder(FormalParameterNode.class)
                  .exclude(
                      NodeType.MethodBody,
                      NodeType.ConstructorBody, NodeType.BlockStatements)
                  .find(),
                  node.finder(LastFormalParameterNode.class)
                  .exclude(
                      NodeType.MethodBody,
                      NodeType.ConstructorBody, NodeType.BlockStatements)
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
            WholeType formalType = findWholeType(formal);
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
            String tdesc = staticType.toDescriptor();
            if (tdesc.endsWith(errorDesc)) {  // [X is an array of errors.
              hasErrorType = true;
              break;
            }
            descriptor.append(tdesc);
          }
          descriptor.append(')');
        }
        if (!hasErrorType) {
          String rdesc = returnType.toDescriptor();
          if (rdesc.endsWith(errorDesc)) {
            hasErrorType = true;
          } else {
            descriptor.append(rdesc);
          }
        } else {
          error(node, "Could not compute method descriptor");
        }

        if (!hasErrorType) {
          info.setDescriptor(descriptor.toString());
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
           .exclude(NodeType.VariableInitializer)
           .find()) {
        IdentifierNode ident = var.firstChildWithType(IdentifierNode.class);
        if (ident != null) {
          Name fieldName = childTypeInfo.canonName.child(
              ident.getValue(), Name.Type.FIELD);
          FieldInfo info = (FieldInfo) memberWithName(
              childTypeInfo, fieldName);
          if (info != null) {
            WholeType valueType = findWholeType(node);
            if (valueType != null) {
              info.setValueType(valueType.getStaticType().typeSpecification);
            }
            ((MemberDeclaration) node).setMemberInfo(info);
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
          info.setValueType(new TypeSpecification(typeInfo.canonName));
        }
      }
    }
    // TODO annotation elements

  }

  private WholeType findWholeType(BaseNode node) {
    WholeType wholeType = null;
    for (WholeType t
        : node.finder(WholeType.class)
        .exclude(NodeType.Throws, NodeType.TypeParameters,
            NodeType.MethodBody, NodeType.ConstructorBody,
            NodeType.BlockStatements,
            NodeType.VariableInitializer,
            NodeType.FormalParameterList, NodeType.Result,
            NodeType.Type)
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
    for (MemberInfo mi : typeInfo.declaredMembers) {
      if (mi.canonName.equals(name)) {
        return mi;
      }
    }
    return null;
  }

  StaticType processStaticType(BaseNode node, TypeNameResolver r) {
    Class<? extends BaseNode> delegateType = null;
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
          for (WholeType argType : args.finder(WholeType.class)
              .exclude(WholeType.class)
              .find()) {
            processStaticType((BaseNode) argType, r);
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
                      new TypeSpecification(canonName),
                      ident.getSourcePosition(),
                      logger);
                }
                break;
            }
        }
        throw new AssertionError(v);
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
      BaseNode delegate = node.firstChildWithType(delegateType);
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
    if (node instanceof WholeType) {
      ((WholeType) node).setStaticType(t);
    }
    return t;
  }

  @Override
  public Void run(Iterable<? extends FileNode> fileNodes) {
    for (FileNode fileNode : fileNodes) {
      run((BaseNode) fileNode, null, null, null);
    }
    return null;
  }

}
