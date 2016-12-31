package com.mikesamuel.cil.ast.passes;

import java.util.logging.Logger;

import javax.annotation.Nullable;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.mikesamuel.cil.ast.ArrayTypeNode;
import com.mikesamuel.cil.ast.BaseNode;
import com.mikesamuel.cil.ast.ClassOrInterfaceTypeNode;
import com.mikesamuel.cil.ast.ClassTypeNode;
import com.mikesamuel.cil.ast.CompilationUnitNode;
import com.mikesamuel.cil.ast.ConstantDeclarationNode;
import com.mikesamuel.cil.ast.FieldDeclarationNode;
import com.mikesamuel.cil.ast.FloatingPointTypeNode;
import com.mikesamuel.cil.ast.FormalParameterListNode;
import com.mikesamuel.cil.ast.FormalParameterNode;
import com.mikesamuel.cil.ast.IdentifierNode;
import com.mikesamuel.cil.ast.IntegralTypeNode;
import com.mikesamuel.cil.ast.InterfaceTypeNode;
import com.mikesamuel.cil.ast.LastFormalParameterNode;
import com.mikesamuel.cil.ast.NodeType;
import com.mikesamuel.cil.ast.NumericTypeNode;
import com.mikesamuel.cil.ast.PrimitiveTypeNode;
import com.mikesamuel.cil.ast.ReferenceTypeNode;
import com.mikesamuel.cil.ast.ResultNode;
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
import com.mikesamuel.cil.ast.traits.MemberDeclaration;
import com.mikesamuel.cil.ast.traits.TypeDeclaration;
import com.mikesamuel.cil.ast.traits.TypeScope;
import com.mikesamuel.cil.ast.traits.WholeType;

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

  void run(BaseNode node, @Nullable TypeInfo typeInfo, TypeNameResolver nr) {
    TypeNameResolver childResolver = nr;
    TypeInfo childTypeInfo = typeInfo;
    if (node instanceof TypeScope) {
      childResolver = ((TypeScope) node).getTypeNameResolver();
    }
    if (node instanceof WholeType) {
      ((WholeType) node).setStaticType(toStaticType(node, nr));
      return;
    }
    if (node instanceof TypeDeclaration) {
      childTypeInfo = ((TypeDeclaration) node).getDeclaredTypeInfo();
    }

    // Compute whole types for method results and field types.
    for (BaseNode child : node.getChildren()) {
      run(child, childTypeInfo, childResolver);
    }

    if (node instanceof CallableDeclaration) {
      CallableDeclaration cd = (CallableDeclaration) node;
      Name methodName = childTypeInfo.canonName.method(
          cd.getMethodName(), cd.getMethodDescriptor());
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
            }
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
        info.setReturnType(returnType);
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
            info.setValueType(valueType.getStaticType());
            ((MemberDeclaration) node).setMemberInfo(info);
          } else {
            error(node, "Missing member info for " + fieldName);
          }
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

  StaticType toStaticType(BaseNode node, TypeNameResolver r) {
    Class<? extends BaseNode> delegateType = null;
    switch (node.getNodeType()) {
      case Result: {
        ResultNode.Variant v = ((ResultNode) node).getVariant();
        switch (v) {
          case Void:
            return StaticType.T_VOID;
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
            return StaticType.T_BOOLEAN;
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
          case Byte:  return StaticType.T_BYTE;
          case Char:  return StaticType.T_CHAR;
          case Int:   return StaticType.T_INT;
          case Long:  return StaticType.T_LONG;
          case Short: return StaticType.T_SHORT;
        }
        break;
      }
      case FloatingPointType: {
        FloatingPointTypeNode.Variant v =
            ((FloatingPointTypeNode) node).getVariant();
        switch (v) {
          case Double: return StaticType.T_DOUBLE;
          case Float:  return StaticType.T_FLOAT;
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
        return typePool.type(spec, node.getSourcePosition(), logger);
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
                return StaticType.ERROR_TYPE;
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
                  return StaticType.ERROR_TYPE;
                }
                return typePool.type(
                    new TypeSpecification(canonName), ident.getSourcePosition(),
                    logger);
            }
        }
        throw new AssertionError(v);
      }
      case ArrayType: {
        TypeNode elementType = node.firstChildWithType(TypeNode.class);
        if (elementType == null) {
          error(node, "Cannot find element type");
          return StaticType.ERROR_TYPE;
        }
        StaticType t = toStaticType(elementType, r);
        TypeSpecification spec = new TypeSpecification(
            t.typeSpecification.typeName,
            t.typeSpecification.bindings,
            t.typeSpecification.nDims + 1);
        return typePool.type(spec, node.getSourcePosition(), logger);
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
        return toStaticType(delegate, r);
      }
    }
    error(node, "Malformed type for node of type " + node.getNodeType());
    return StaticType.ERROR_TYPE;
  }

  @Override
  Void run(Iterable<? extends CompilationUnitNode> compilationUnits) {
    for (CompilationUnitNode cu : compilationUnits) {
      run(cu, null, cu.getTypeNameResolver());
    }
    return null;
  }


}
