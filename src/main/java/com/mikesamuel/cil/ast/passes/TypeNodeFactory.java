package com.mikesamuel.cil.ast.passes;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.mikesamuel.cil.ast.j8.AdditionalBoundNode;
import com.mikesamuel.cil.ast.j8.ArrayTypeNode;
import com.mikesamuel.cil.ast.j8.BooleanLiteralNode;
import com.mikesamuel.cil.ast.j8.CastExpressionNode;
import com.mikesamuel.cil.ast.j8.CastNode;
import com.mikesamuel.cil.ast.j8.CharacterLiteralNode;
import com.mikesamuel.cil.ast.j8.ClassOrInterfaceTypeNode;
import com.mikesamuel.cil.ast.j8.ClassTypeNode;
import com.mikesamuel.cil.ast.j8.ConvertCastNode;
import com.mikesamuel.cil.ast.j8.DimNode;
import com.mikesamuel.cil.ast.j8.ExceptionTypeListNode;
import com.mikesamuel.cil.ast.j8.ExceptionTypeNode;
import com.mikesamuel.cil.ast.j8.ExpressionAtomNode;
import com.mikesamuel.cil.ast.j8.ExpressionNode;
import com.mikesamuel.cil.ast.j8.FloatingPointLiteralNode;
import com.mikesamuel.cil.ast.j8.FloatingPointTypeNode;
import com.mikesamuel.cil.ast.j8.FormalParameterListNode;
import com.mikesamuel.cil.ast.j8.FormalParameterNode;
import com.mikesamuel.cil.ast.j8.FormalParametersNode;
import com.mikesamuel.cil.ast.j8.IdentifierNode;
import com.mikesamuel.cil.ast.j8.IntegerLiteralNode;
import com.mikesamuel.cil.ast.j8.IntegralTypeNode;
import com.mikesamuel.cil.ast.j8.InterfaceTypeNode;
import com.mikesamuel.cil.ast.j8.J8BaseNode;
import com.mikesamuel.cil.ast.j8.J8NodeVariant;
import com.mikesamuel.cil.ast.j8.LastFormalParameterNode;
import com.mikesamuel.cil.ast.j8.LiteralNode;
import com.mikesamuel.cil.ast.j8.NullLiteralNode;
import com.mikesamuel.cil.ast.j8.NumericTypeNode;
import com.mikesamuel.cil.ast.j8.PackageOrTypeNameNode;
import com.mikesamuel.cil.ast.j8.PrimitiveTypeNode;
import com.mikesamuel.cil.ast.j8.ReferenceTypeNode;
import com.mikesamuel.cil.ast.j8.ResultNode;
import com.mikesamuel.cil.ast.j8.ThrowsNode;
import com.mikesamuel.cil.ast.j8.TypeArgumentListNode;
import com.mikesamuel.cil.ast.j8.TypeArgumentNode;
import com.mikesamuel.cil.ast.j8.TypeArgumentsNode;
import com.mikesamuel.cil.ast.j8.TypeBoundNode;
import com.mikesamuel.cil.ast.j8.TypeNameNode;
import com.mikesamuel.cil.ast.j8.TypeNode;
import com.mikesamuel.cil.ast.j8.TypeVariableNode;
import com.mikesamuel.cil.ast.j8.UnannTypeNode;
import com.mikesamuel.cil.ast.j8.UnaryExpressionNode;
import com.mikesamuel.cil.ast.j8.VariableDeclaratorIdNode;
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

/** Utility for manufacturing type related AST nodes. */
public final class TypeNodeFactory {
  final Logger logger;
  final TypePool typePool;
  private boolean allowMethodContainers;

  /** */
  public TypeNodeFactory(Logger logger, TypePool typePool) {
    this.logger = logger;
    this.typePool = typePool;
  }

  /**
   * Types defined in methods are not normally mentionable via
   * full type names.
   * <p>
   * It is convenient to be able to represent method names in the AST
   * temporarily when migrating them out, so, when a flag is enabled,
   * we represent method based type names in the TypeName and
   * ClassOrInterfaceType ASTs.
   */
  public void allowMethodContainers() {
    this.allowMethodContainers = true;
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

  /** */
  public static PrimitiveTypeNode toPrimitiveTypeNode(PrimitiveType typ) {
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

  /** Does not accept null. */
  ReferenceTypeNode toReferenceTypeNode(ReferenceType typ) {
    // TODO: Add a MentionableReferenceType that the null type does
    // not implement but which others do to make this type safe.
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
    } else if (typ.typeSpecification.rawName.type == Name.Type.TYPE_PARAMETER) {
      return ReferenceTypeNode.Variant.TypeVariable.buildNode(
          toTypeVariableNode(typ.typeSpecification))
          .setStaticType(typ);
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

  /** */
  public ClassOrInterfaceTypeNode toClassOrInterfaceTypeNode(
      PartialTypeSpecification spec) {
    PartialTypeSpecification parentSpec = spec.parent();
    ClassOrInterfaceTypeNode parent = parentSpec != null
        ? toClassOrInterfaceTypeNode(parentSpec)
        : null;
    TypeArgumentsNode arguments = null;
    if (!spec.bindings().isEmpty()) {
      TypeArgumentListNode typeArgumentList =
          TypeArgumentListNode.Variant.TypeArgumentComTypeArgument
          .buildNode();
      for (TypeBinding b : spec.bindings()) {
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
        ReferenceTypeNode rt = b.typeSpec != null
            ? toReferenceTypeNode(
                (ReferenceType) typePool.type(b.typeSpec, null, logger))
            : null;
        if (rt == null) {
          typeArgumentList.add(
              TypeArgumentNode.Variant.Wildcard.buildNode(
                  WildcardNode.Variant.AnnotationQmWildcardBounds
                  .buildNode()));
        } else if (boundsVariant == null) {
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
    }

    Name rawName = spec.getRawName();
    String identifierValue;
    if (spec instanceof TypeSpecification) {
      TypeSpecification ts = (TypeSpecification) spec;
      Preconditions.checkArgument(ts.nDims == 0);
      Preconditions.checkArgument(ts.rawName.type.isType);
      identifierValue = rawName.identifier;
    } else if (spec instanceof MethodTypeContainer) {
      // Anonymous class names and named types declared in method bodies cannot
      // be addressed by fully qualified type names.
      Preconditions.checkState(allowMethodContainers);
      identifierValue = identifierValueForMethod(rawName);
    } else {
      throw new AssertionError(spec);
    }
    IdentifierNode ident = IdentifierNode.Variant.Builtin
        .buildNode(identifierValue)
        .setNamePartType(rawName.type);
    ClassOrInterfaceTypeNode newTypeNode = ClassOrInterfaceTypeNode.Variant
        .ClassOrInterfaceTypeDotAnnotationIdentifierTypeArguments.buildNode();
    if (parent != null) {
      newTypeNode.add(parent);
    }
    newTypeNode.add(ident);
    if (arguments != null) {
      newTypeNode.add(arguments);
    }
    if (spec instanceof TypeSpecification) {
      newTypeNode.setStaticType(
          typePool.type((TypeSpecification) spec, null, null));
    }
    return newTypeNode;
  }

  private static String identifierValueForMethod(Name rawName) {
    return rawName.identifier + "$" + rawName.variant;
  }

  /** */
  public TypeNode toTypeNode(StaticType typ) {
    if (typ instanceof PrimitiveType) {
      return TypeNode.Variant.PrimitiveType.buildNode(
          toPrimitiveTypeNode((PrimitiveType) typ));
    } else {
      Preconditions.checkArgument(typ instanceof ReferenceType);
      return TypeNode.Variant.ReferenceType.buildNode(
          toReferenceTypeNode((ReferenceType) typ));
    }
  }

  /** */
  public J8BaseNode toUnannTypeNode(StaticType typ) {
    return UnannTypeNode.Variant.NotAtType.buildNode(toTypeNode(typ));
  }


  /** */
  public TypeNameNode toTypeNameNode(TypeInfo typeInfo) {
    TypeNameNode result = toTypeNameNode(typeInfo.canonName);
    result.setReferencedTypeInfo(typeInfo);
    return result;
  }

  /** */
  public TypeNameNode toTypeNameNode(Name typeName) {
    Preconditions.checkArgument(typeName.type == Name.Type.CLASS);
    return (TypeNameNode) toNameTree(
        typeName,
        TypeNameNode.Variant.Identifier,
        TypeNameNode.Variant.PackageOrTypeNameDotIdentifier);
  }

  /** */
  public PackageOrTypeNameNode toPackageOrTypeNameNode(
      Name typeOrPackageName) {
    Preconditions.checkArgument(
        typeOrPackageName.type == Name.Type.CLASS
        || typeOrPackageName.type == Name.Type.PACKAGE);
    return (PackageOrTypeNameNode) toNameTree(
        typeOrPackageName,
        PackageOrTypeNameNode.Variant.Identifier,
        PackageOrTypeNameNode.Variant.PackageOrTypeNameDotIdentifier);
  }

  private J8BaseNode toNameTree(
      Name typeName,
      J8NodeVariant identifierVariant,
      J8NodeVariant parentThenIdentifierVariant) {
    IdentifierNode identNode;
    if (typeName.type == Name.Type.METHOD) {
      Preconditions.checkState(this.allowMethodContainers);
      identNode = IdentifierNode.Variant.Builtin
          .buildNode(identifierValueForMethod(typeName))
          .setNamePartType(Name.Type.METHOD);
    } else {
      identNode = toIdentifierNode(typeName);
    }
    if (typeName.parent == null
        || Name.DEFAULT_PACKAGE.equals(typeName.parent)) {
      return identifierVariant.buildNode(ImmutableList.of(identNode));
    } else {
      return parentThenIdentifierVariant.buildNode(ImmutableList.of(
          toPackageOrTypeNameNode(typeName.parent),
          identNode));
    }
  }

  /** */
  public static IdentifierNode toIdentifierNode(Name nm) {
    IdentifierNode node = IdentifierNode.Variant.Builtin
        .buildNode(nm.identifier);
    node.setNamePartType(nm.type);
    return node;
  }

  /** */
  public TypeArgumentsNode toTypeArgumentsNode(
      Positioned pos, Iterable<? extends TypeBinding> typeActuals) {
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

  /** */
  public ResultNode toResultNode(StaticType rt) {
    boolean isVoid = StaticType.T_VOID.typeSpecification.equals(
        rt.typeSpecification);
    if (isVoid) {
      return ResultNode.Variant.Void.buildNode();
    } else {
      return ResultNode.Variant.UnannType.buildNode(toUnannTypeNode(rt));
    }
  }

  /** */
  public ExceptionTypeNode toExceptionType(TypeSpecification ts) {
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

  /** */
  public static TypeVariableNode toTypeVariableNode(TypeSpecification ts) {
    return TypeVariableNode.Variant.AnnotationIdentifier.buildNode(
        toIdentifierNode(ts.rawName));
  }

  /** */
  public TypeBoundNode typeBoundFor(TypeInfo ti) {
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

  /** */
  public FormalParameterListNode toFormalParameterListNode(
      List<? extends TypeSpecification> types,
      List<? extends String> names, boolean isVariadic) {
    int n = types.size();
    Preconditions.checkArgument(n == names.size());
    if (n == 0) { return null; }

    FormalParameterListNode listNode;
    int nRegularParameters = n - (isVariadic ? 1 : 0);
    if (nRegularParameters == 0) {
      listNode = FormalParameterListNode.Variant
          .LastFormalParameter.buildNode();
    } else {
      listNode = FormalParameterListNode.Variant
          .FormalParametersComLastFormalParameter.buildNode();
      ImmutableList.Builder<J8BaseNode> formalParameters =
          ImmutableList.builder();
      for (int i = 0; i < nRegularParameters; ++i) {
        TypeSpecification f = types.get(i);
        String name = names.get(i);
        J8BaseNode formalNode =
            FormalParameterNode.Variant.Declaration
            .buildNode(ImmutableList.<J8BaseNode>of(
                toUnannTypeNode(typePool.type(f, null, logger)),
                VariableDeclaratorIdNode.Variant.IdentifierDims.buildNode(
                    IdentifierNode.Variant.Builtin.buildNode(name))));
        formalParameters.add(formalNode);
      }
      listNode.add(
          FormalParametersNode.Variant.FormalParameterComFormalParameter
          .buildNode(formalParameters.build()));
    }
    if (isVariadic) {
      TypeSpecification f = types.get(nRegularParameters);
      String name = names.get(nRegularParameters);
      listNode.add(
          LastFormalParameterNode.Variant.Variadic
          .buildNode(ImmutableList.<J8BaseNode>of(
              toUnannTypeNode(typePool.type(f, null, logger)),
              VariableDeclaratorIdNode.Variant.IdentifierDims.buildNode(
                  IdentifierNode.Variant.Builtin.buildNode(name)))));
    }
    return listNode;
  }

  /** */
  public J8BaseNode toThrowsNode(ImmutableList<TypeSpecification> thrownTypes) {
    ImmutableList.Builder<J8BaseNode> b = ImmutableList.builder();
    for (TypeSpecification thrown : thrownTypes) {
      b.add(toExceptionType(thrown));
    }
    return ThrowsNode.Variant.ThrowsExceptionTypeList
        .buildNode(
            ExceptionTypeListNode.Variant.ExceptionTypeComExceptionType
            .buildNode(b.build()));
  }

  private static final ImmutableMap<TypeSpecification, ExpressionNode> ZEROS =
      ImmutableMap.<TypeSpecification, ExpressionNode>builder()
      .put(
          StaticType.T_BOOLEAN.typeSpecification,
          ExpressionNode.Variant.ConditionalExpression.buildNode(
              ExpressionAtomNode.Variant.Literal.buildNode(
                  LiteralNode.Variant.BooleanLiteral.buildNode(
                      BooleanLiteralNode.Variant.False.buildNode()))))
      .put(
          StaticType.T_BYTE.typeSpecification,
          ExpressionNode.Variant.ConditionalExpression.buildNode(
              UnaryExpressionNode.Variant.CastExpression.buildNode(
                  CastExpressionNode.Variant.Expression.buildNode(
                      CastNode.Variant.ConvertCast.buildNode(
                          ConvertCastNode.Variant.PrimitiveType.buildNode(
                              PrimitiveTypeNode.Variant.AnnotationNumericType
                              .buildNode(
                                  IntegralTypeNode.Variant.Byte.buildNode()))),
                      ExpressionAtomNode.Variant.Literal.buildNode(
                          LiteralNode.Variant.IntegerLiteral.buildNode(
                              IntegerLiteralNode.Variant.Builtin.buildNode(
                                  "0")))))))
      .put(
          StaticType.T_CHAR.typeSpecification,
          ExpressionNode.Variant.ConditionalExpression.buildNode(
              ExpressionAtomNode.Variant.Literal.buildNode(
                  LiteralNode.Variant.CharacterLiteral.buildNode(
                      CharacterLiteralNode.Variant.Builtin.buildNode(
                          "'\\0'")))))
      .put(
          StaticType.T_DOUBLE.typeSpecification,
          ExpressionNode.Variant.ConditionalExpression.buildNode(
              ExpressionAtomNode.Variant.Literal.buildNode(
                  LiteralNode.Variant.IntegerLiteral.buildNode(
                      FloatingPointLiteralNode.Variant.Builtin
                      .buildNode("0D")))))
      .put(
          StaticType.T_FLOAT.typeSpecification,
          ExpressionNode.Variant.ConditionalExpression.buildNode(
              ExpressionAtomNode.Variant.Literal.buildNode(
                  LiteralNode.Variant.IntegerLiteral.buildNode(
                      FloatingPointLiteralNode.Variant.Builtin
                      .buildNode("0F")))))
      .put(
          StaticType.T_INT.typeSpecification,
          ExpressionNode.Variant.ConditionalExpression.buildNode(
              ExpressionAtomNode.Variant.Literal.buildNode(
                  LiteralNode.Variant.IntegerLiteral.buildNode(
                      IntegerLiteralNode.Variant.Builtin.buildNode("0")))))
      .put(
          StaticType.T_LONG.typeSpecification,
          ExpressionNode.Variant.ConditionalExpression.buildNode(
              ExpressionAtomNode.Variant.Literal.buildNode(
                  LiteralNode.Variant.IntegerLiteral.buildNode(
                      IntegerLiteralNode.Variant.Builtin.buildNode("0L")))))
      .put(
          StaticType.T_SHORT.typeSpecification,
          ExpressionNode.Variant.ConditionalExpression.buildNode(
              UnaryExpressionNode.Variant.CastExpression.buildNode(
                  CastExpressionNode.Variant.Expression.buildNode(
                      CastNode.Variant.ConvertCast.buildNode(
                          ConvertCastNode.Variant.PrimitiveType.buildNode(
                              PrimitiveTypeNode.Variant.AnnotationNumericType
                              .buildNode(
                                  IntegralTypeNode.Variant.Short.buildNode()))),
                      ExpressionAtomNode.Variant.Literal.buildNode(
                          LiteralNode.Variant.IntegerLiteral.buildNode(
                              IntegerLiteralNode.Variant.Builtin.buildNode(
                                  "0")))))))
      .build();

  /** */
  public static ExpressionNode zeroValueFor(TypeSpecification ts) {
    ExpressionNode e = ZEROS.get(ts);
    return e != null
        ? e.deepClone()
        : ExpressionNode.Variant.ConditionalExpression.buildNode(
            ExpressionAtomNode.Variant.Literal.buildNode(
                LiteralNode.Variant.NullLiteral.buildNode(
                    NullLiteralNode.Variant.Null.buildNode())));
  }
}
