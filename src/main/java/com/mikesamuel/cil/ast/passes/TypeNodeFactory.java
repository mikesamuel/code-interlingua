package com.mikesamuel.cil.ast.passes;

import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.mikesamuel.cil.ast.j8.AdditionalBoundNode;
import com.mikesamuel.cil.ast.j8.ArrayTypeNode;
import com.mikesamuel.cil.ast.j8.ClassOrInterfaceTypeNode;
import com.mikesamuel.cil.ast.j8.ClassTypeNode;
import com.mikesamuel.cil.ast.j8.DimNode;
import com.mikesamuel.cil.ast.j8.ExceptionTypeNode;
import com.mikesamuel.cil.ast.j8.FloatingPointTypeNode;
import com.mikesamuel.cil.ast.j8.IdentifierNode;
import com.mikesamuel.cil.ast.j8.IntegralTypeNode;
import com.mikesamuel.cil.ast.j8.InterfaceTypeNode;
import com.mikesamuel.cil.ast.j8.J8BaseNode;
import com.mikesamuel.cil.ast.j8.J8NodeVariant;
import com.mikesamuel.cil.ast.j8.NumericTypeNode;
import com.mikesamuel.cil.ast.j8.PackageOrTypeNameNode;
import com.mikesamuel.cil.ast.j8.PrimitiveTypeNode;
import com.mikesamuel.cil.ast.j8.ReferenceTypeNode;
import com.mikesamuel.cil.ast.j8.ResultNode;
import com.mikesamuel.cil.ast.j8.TypeArgumentListNode;
import com.mikesamuel.cil.ast.j8.TypeArgumentNode;
import com.mikesamuel.cil.ast.j8.TypeArgumentsNode;
import com.mikesamuel.cil.ast.j8.TypeBoundNode;
import com.mikesamuel.cil.ast.j8.TypeNameNode;
import com.mikesamuel.cil.ast.j8.TypeNode;
import com.mikesamuel.cil.ast.j8.TypeVariableNode;
import com.mikesamuel.cil.ast.j8.UnannTypeNode;
import com.mikesamuel.cil.ast.j8.WildcardBoundsNode;
import com.mikesamuel.cil.ast.j8.WildcardNode;
import com.mikesamuel.cil.ast.meta.JavaLang;
import com.mikesamuel.cil.ast.meta.MethodTypeContainer;
import com.mikesamuel.cil.ast.meta.Name;
import com.mikesamuel.cil.ast.meta.PackageSpecification;
import com.mikesamuel.cil.ast.meta.PartialTypeSpecification;
import com.mikesamuel.cil.ast.meta.StaticType;
import com.mikesamuel.cil.ast.meta.StaticType.NumericType;
import com.mikesamuel.cil.ast.meta.StaticType.PrimitiveType;
import com.mikesamuel.cil.ast.meta.StaticType.TypePool;
import com.mikesamuel.cil.ast.meta.StaticType.TypePool.ArrayType;
import com.mikesamuel.cil.ast.meta.StaticType.TypePool.ReferenceType;
import com.mikesamuel.cil.ast.meta.TypeInfo;
import com.mikesamuel.cil.ast.meta.TypeSpecification;
import com.mikesamuel.cil.ast.meta.TypeSpecification.TypeBinding;
import com.mikesamuel.cil.parser.Positioned;
import com.mikesamuel.cil.util.LogUtils;

class TypeNodeFactory {
  final Logger logger;
  final TypePool typePool;

  TypeNodeFactory(Logger logger, TypePool typePool) {
    this.logger = logger;
    this.typePool = typePool;
  }

  static final ImmutableMap<NumericType, J8NodeVariant>
      NUMERIC_TYPE_TO_VARIANT =
      ImmutableMap.<NumericType, J8NodeVariant>builder()
      .put(StaticType.T_BYTE,   IntegralTypeNode.Variant.Byte)
      .put(StaticType.T_CHAR,   IntegralTypeNode.Variant.Char)
      .put(StaticType.T_SHORT,  IntegralTypeNode.Variant.Short)
      .put(StaticType.T_INT,    IntegralTypeNode.Variant.Int)
      .put(StaticType.T_LONG,   IntegralTypeNode.Variant.Long)
      .put(StaticType.T_FLOAT,  FloatingPointTypeNode.Variant.Float)
      .put(StaticType.T_DOUBLE, FloatingPointTypeNode.Variant.Double)
      .build();


  static PrimitiveTypeNode toPrimitiveTypeNode(PrimitiveType typ) {
    if (StaticType.T_BOOLEAN.equals(typ)) {
      return PrimitiveTypeNode.Variant.AnnotationBoolean
          .buildNode(ImmutableList.of())
          .setStaticType(typ);
    }

    NumericType nt = (NumericType) typ;

    J8NodeVariant v = NUMERIC_TYPE_TO_VARIANT.get(nt);
    NumericTypeNode numericTypeNode =
        (nt.isFloaty ? NumericTypeNode.Variant.FloatingPointType
        : NumericTypeNode.Variant.IntegralType)
        .buildNode(v.buildNode(ImmutableList.of()))
        .setStaticType(typ);

    return PrimitiveTypeNode.Variant.AnnotationNumericType
        .buildNode(numericTypeNode)
        .setStaticType(typ);
  }

  ReferenceTypeNode toReferenceTypeNode(ReferenceType typ) {
    Preconditions.checkArgument(!typePool.T_NULL.equals(typ));
    Preconditions.checkArgument(!StaticType.ERROR_TYPE.equals(typ));
    if (typ instanceof TypePool.ArrayType) {
      TypePool.ArrayType at = (ArrayType) typ;
      StaticType baseElementType = at.baseElementType;
      TypeNode typeNode = toTypeNode(at.baseElementType);

      StaticType ct = baseElementType;
      Preconditions.checkState(at.dimensionality > 0);
      for (int nDims = at.dimensionality; --nDims >= 1;) {
        ct = typePool.type(ct.typeSpecification.arrayOf(), null, logger);
        typeNode = TypeNode.Variant.ReferenceType.buildNode(
            ReferenceTypeNode.Variant.ArrayType.buildNode(
                ArrayTypeNode.Variant.TypeAnnotationDim.buildNode(
                    typeNode, DimNode.Variant.LsRs.buildNode())
                .setStaticType(ct))
            .setStaticType(ct));
      }
      return ReferenceTypeNode.Variant.ArrayType.buildNode(
              ArrayTypeNode.Variant.TypeAnnotationDim.buildNode(
                  typeNode, DimNode.Variant.LsRs.buildNode())
              .setStaticType(at))
          .setStaticType(at);
    } else if (typ instanceof TypePool.ClassOrInterfaceType) {
      TypePool.ClassOrInterfaceType ct = (TypePool.ClassOrInterfaceType) typ;
      ClassOrInterfaceTypeNode ciNode = toClassOrInterfaceTypeNode(
          ct.typeSpecification);
      ciNode.setStaticType(typ);
      return ReferenceTypeNode.Variant.ClassOrInterfaceType.buildNode(ciNode)
          .setStaticType(typ);
    } else {
      throw new AssertionError(typ);
    }
  }

  ClassOrInterfaceTypeNode packageToClassOrInterfaceTypeNode(
      Name packageName) {
    if (Name.DEFAULT_PACKAGE.equals(packageName)) {
      return null;
    }
    ClassOrInterfaceTypeNode parent = packageToClassOrInterfaceTypeNode(
        packageName.parent);
    IdentifierNode ident = IdentifierNode.Variant.Builtin
        .buildNode(packageName.identifier)
        .setNamePartType(packageName.type);
    ClassOrInterfaceTypeNode newTypeNode = ClassOrInterfaceTypeNode.Variant
        .ClassOrInterfaceTypeDotAnnotationIdentifierTypeArguments.buildNode();
    if (parent != null) {
      newTypeNode.add(parent);
    }
    newTypeNode.add(ident);
    return newTypeNode;
  }

  ClassOrInterfaceTypeNode toClassOrInterfaceTypeNode(
      PartialTypeSpecification spec) {
    ClassOrInterfaceTypeNode parent = spec.parent() != null
        ? toClassOrInterfaceTypeNode(spec.parent())
        : null;
    TypeArgumentsNode arguments = null;
    if (!spec.bindings().isEmpty()) {
      TypeArgumentListNode typeArgumentList =
          TypeArgumentListNode.Variant.TypeArgumentComTypeArgument
          .buildNode();
      for (TypeBinding b : spec.bindings()) {
        ReferenceTypeNode rt = toReferenceTypeNode(
            (ReferenceType) typePool.type(b.typeSpec, null, logger));
        WildcardBoundsNode.Variant boundsVariant = null;
        switch (b.variance) {
          case EXTENDS:
            boundsVariant = WildcardBoundsNode.Variant.ExtendsReferenceType;
            break;
          case INVARIANT:
            break;
          case SUPER:
            boundsVariant = WildcardBoundsNode.Variant.SuperReferenceType;
            break;
        }
        if (boundsVariant == null) {
          typeArgumentList.add(
              TypeArgumentNode.Variant.ReferenceType.buildNode(rt));
        } else {
          typeArgumentList.add(
              TypeArgumentNode.Variant.Wildcard.buildNode(
              WildcardNode.Variant.AnnotationQmWildcardBounds
                  .buildNode(boundsVariant.buildNode(rt))));
        }
      }
      arguments = TypeArgumentsNode.Variant.LtTypeArgumentListGt.buildNode(
          typeArgumentList);
    }

    if (spec instanceof PackageSpecification) {
      return packageToClassOrInterfaceTypeNode(
          ((PackageSpecification) spec).packageName);
    } else if (spec instanceof TypeSpecification) {
      TypeSpecification ts = (TypeSpecification) spec;
      Preconditions.checkArgument(ts.nDims == 0);
      Preconditions.checkArgument(ts.rawName.type.isType);
      IdentifierNode ident = IdentifierNode.Variant.Builtin
          .buildNode(ts.rawName.identifier)
          .setNamePartType(ts.rawName.type);
      ClassOrInterfaceTypeNode newTypeNode = ClassOrInterfaceTypeNode.Variant
          .ClassOrInterfaceTypeDotAnnotationIdentifierTypeArguments.buildNode();
      if (parent != null) {
        newTypeNode.add(parent);
      }
      newTypeNode.add(ident);
      if (arguments != null) {
        newTypeNode.add(arguments);
      }
      return newTypeNode;
    } else {
      Preconditions.checkState(spec instanceof MethodTypeContainer);
      // Anonymous class names cannot be addressed by type names.
      throw new IllegalArgumentException(spec.toString());
    }
  }

  TypeNode toTypeNode(StaticType typ) {
    if (typ instanceof PrimitiveType) {
      return TypeNode.Variant.PrimitiveType.buildNode(
          toPrimitiveTypeNode((PrimitiveType) typ));
    } else {
      Preconditions.checkArgument(typ instanceof ReferenceType);
      return TypeNode.Variant.ReferenceType.buildNode(
          toReferenceTypeNode((ReferenceType) typ));
    }
  }

  static TypeNameNode toTypeNameNode(Name typeName) {
    Preconditions.checkArgument(typeName.type == Name.Type.CLASS);
    TypeNameNode result;
    if (typeName.parent == null
        || Name.DEFAULT_PACKAGE.equals(typeName.parent)) {
      result = TypeNameNode.Variant.Identifier.buildNode(
          toIdentifierNode(typeName));
    } else {
      result = TypeNameNode.Variant.PackageOrTypeNameDotIdentifier
          .buildNode(
              toPackageOrTypeNameNode(typeName.parent),
              toIdentifierNode(typeName));
    }
    return result;
  }

  static PackageOrTypeNameNode toPackageOrTypeNameNode(
      Name typeOrPackageName) {
    PackageOrTypeNameNode result;
    if (typeOrPackageName.parent == null
        || Name.DEFAULT_PACKAGE.equals(typeOrPackageName.parent)) {
      result = PackageOrTypeNameNode.Variant.Identifier.buildNode(
          toIdentifierNode(typeOrPackageName));
    } else {
      result = PackageOrTypeNameNode.Variant.PackageOrTypeNameDotIdentifier
          .buildNode(
              toPackageOrTypeNameNode(typeOrPackageName.parent),
              toIdentifierNode(typeOrPackageName));
    }
    return result;
  }

  static IdentifierNode toIdentifierNode(Name nm) {
    IdentifierNode node = IdentifierNode.Variant.Builtin
        .buildNode(nm.identifier);
    node.setNamePartType(nm.type);
    return node;
  }

  TypeArgumentsNode toTypeArgumentsNode(
      Positioned pos,
      Iterable<? extends TypeBinding> typeActuals) {
    Preconditions.checkArgument(!Iterables.isEmpty(typeActuals));
    ImmutableList.Builder<TypeArgumentNode> typeArguments =
        ImmutableList.builder();
    for (TypeBinding b : typeActuals) {
      StaticType t = typePool.type(b.typeSpec, pos, logger);
      ReferenceTypeNode rtNode = toReferenceTypeNode((ReferenceType) t);
      if (t instanceof ReferenceType) {
        typeArguments.add(
            (b.variance == TypeSpecification.Variance.INVARIANT
            ? TypeArgumentNode.Variant.ReferenceType.buildNode(rtNode)
            : TypeArgumentNode.Variant.Wildcard.buildNode(
                WildcardNode.Variant.AnnotationQmWildcardBounds.buildNode(
                    (b.variance == TypeSpecification.Variance.EXTENDS
                    ? WildcardBoundsNode.Variant.ExtendsReferenceType
                        : WildcardBoundsNode.Variant.SuperReferenceType)
                    .buildNode(rtNode)))));
      }
    }
    return TypeArgumentsNode.Variant.LtTypeArgumentListGt
        .buildNode(
            TypeArgumentListNode.Variant.TypeArgumentComTypeArgument
            .buildNode(typeArguments.build()));
  }

  ResultNode toResultNode(StaticType rt) {
    boolean isVoid = StaticType.T_VOID.typeSpecification.equals(
        rt.typeSpecification);
    if (isVoid) {
      return ResultNode.Variant.Void.buildNode();
    } else {
      return ResultNode.Variant.UnannType.buildNode(
          UnannTypeNode.Variant.NotAtType.buildNode(toTypeNode(rt)));
    }
  }

  ExceptionTypeNode toExceptionType(TypeSpecification ts) {
    if (ts.nDims != 0) {
      LogUtils.log(
          logger, Level.SEVERE, null,
          "Cannot create exception type from array : " + ts, null);
      return toExceptionType(JavaLang.JAVA_LANG_RUNTIMEEXCEPTION);
    }
    if (ts.rawName.type == Name.Type.TYPE_PARAMETER) {
      return ExceptionTypeNode.Variant.TypeVariable.buildNode(
          toTypeVariableNode(ts));
    }
    return ExceptionTypeNode.Variant.ClassType.buildNode(
        ClassTypeNode.Variant.ClassOrInterfaceType.buildNode(
            toClassOrInterfaceTypeNode(ts)));
  }

  static TypeVariableNode toTypeVariableNode(TypeSpecification ts) {
    return TypeVariableNode.Variant.AnnotationIdentifier.buildNode(
        toIdentifierNode(ts.rawName));
  }

  TypeBoundNode typeBoundFor(TypeInfo ti) {
    if (ti.interfaces.isEmpty() && ti.superType.isPresent()) {
      TypeSpecification st = ti.superType.get();
      if (JavaLang.JAVA_LANG_OBJECT.equals(st)) {
        return null;
      }
      if (st.rawName.type == Name.Type.TYPE_PARAMETER
          && st.nDims == 0) {
        return TypeBoundNode.Variant.ExtendsTypeVariable.buildNode(
            toTypeVariableNode(st));
      }
    }
    ImmutableList.Builder<J8BaseNode> bounds = ImmutableList.builder();
    // The first type must be a concrete type, so we enumerate the
    // super type then the interfaces.
    bounds.add(toClassOrInterfaceTypeNode(
        ti.superType.or(JavaLang.JAVA_LANG_OBJECT)));
    for (TypeSpecification iface : ti.interfaces) {
      bounds.add(AdditionalBoundNode.Variant.AmpInterfaceType.buildNode(
          InterfaceTypeNode.Variant.ClassOrInterfaceType.buildNode(
              toClassOrInterfaceTypeNode(iface))));
    }
    return TypeBoundNode.Variant.ExtendsClassOrInterfaceTypeAdditionalBound
        .buildNode(bounds.build());
  }
}
