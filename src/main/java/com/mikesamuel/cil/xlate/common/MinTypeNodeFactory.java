package com.mikesamuel.cil.xlate.common;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Nullable;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.mikesamuel.cil.ast.jmin.ArrayTypeNode;
import com.mikesamuel.cil.ast.jmin.ClassOrInterfaceTypeNode;
import com.mikesamuel.cil.ast.jmin.ClassTypeNode;
import com.mikesamuel.cil.ast.jmin.DimNode;
import com.mikesamuel.cil.ast.jmin.FloatingPointTypeNode;
import com.mikesamuel.cil.ast.jmin.IdentifierNode;
import com.mikesamuel.cil.ast.jmin.IntegralTypeNode;
import com.mikesamuel.cil.ast.jmin.JminNodeVariant;
import com.mikesamuel.cil.ast.jmin.NumericTypeNode;
import com.mikesamuel.cil.ast.jmin.PackageNameNode;
import com.mikesamuel.cil.ast.jmin.PrimitiveTypeNode;
import com.mikesamuel.cil.ast.jmin.ReferenceTypeNode;
import com.mikesamuel.cil.ast.jmin.TypeArgumentListNode;
import com.mikesamuel.cil.ast.jmin.TypeArgumentNode;
import com.mikesamuel.cil.ast.jmin.TypeArgumentsNode;
import com.mikesamuel.cil.ast.jmin.TypeNode;
import com.mikesamuel.cil.ast.jmin.TypeVariableNode;
import com.mikesamuel.cil.ast.jmin.WildcardBoundsNode;
import com.mikesamuel.cil.ast.jmin.WildcardNode;
import com.mikesamuel.cil.ast.meta.JavaLang;
import com.mikesamuel.cil.ast.meta.Name;
import com.mikesamuel.cil.ast.meta.StaticType;
import com.mikesamuel.cil.ast.meta.StaticType.NumericType;
import com.mikesamuel.cil.ast.meta.StaticType.PrimitiveType;
import com.mikesamuel.cil.ast.meta.StaticType.TypePool;
import com.mikesamuel.cil.ast.meta.TypeInfo;
import com.mikesamuel.cil.ast.meta.TypeSpecification;
import com.mikesamuel.cil.ast.meta.TypeSpecification.TypeBinding;
import com.mikesamuel.cil.ast.meta.TypeSpecification.Variance;
import com.mikesamuel.cil.parser.Positioned;
import com.mikesamuel.cil.util.LogUtils;

final class MinTypeNodeFactory {
  final Logger logger;
  final TypePool pool;
  final @Nullable Positioned pos;

  private StaticType type(TypeSpecification ts) {
    return pool.type(ts, pos, logger);
  }

  private void error(String msg) {
    LogUtils.log(logger, Level.SEVERE, pos, msg, null);
  }

  MinTypeNodeFactory(Logger logger, TypePool pool, Positioned pos) {
    this.logger = logger;
    this.pool = pool;
    this.pos = pos;
  }


  ClassOrInterfaceTypeNode toClassOrInterfaceType(TypeSpecification ts) {
    PackageNameNode packageName = toPackageName(ts.rawName.parent);
    IdentifierNode ident = toIdentifier(ts.rawName);
    TypeArgumentsNode typeArguments =
        ts.bindings.isEmpty() ? null : toTypeArguments(ts.bindings());
    ClassOrInterfaceTypeNode tn = ClassOrInterfaceTypeNode.Variant
        .PackageNameDotAnnotationIdentifierTypeArguments.buildNode(
            packageName, ident);
    if (typeArguments != null) {
      tn.add(typeArguments);
    }
    tn.setStaticType(type(ts));
    return tn;
  }

  private TypeArgumentsNode toTypeArguments(
      Iterable<? extends TypeBinding> bindings) {
    TypeArgumentListNode listNode = TypeArgumentListNode.Variant
        .TypeArgumentComTypeArgument.buildNode();
    for (TypeBinding b : bindings) {
      if (b.typeSpec == null) {
        error("Type bound missing type");
      }
      ReferenceTypeNode rt = toReferenceType(
          b.typeSpec != null ? b.typeSpec : JavaLang.JAVA_LANG_OBJECT);
      switch (b.variance) {
        case EXTENDS:
        case SUPER:
          WildcardBoundsNode.Variant wcbv =
             b.variance == Variance.EXTENDS
                 ? WildcardBoundsNode.Variant.ExtendsReferenceType
                 : WildcardBoundsNode.Variant.SuperReferenceType;
          listNode.add(
              TypeArgumentNode.Variant.Wildcard.buildNode(
                  WildcardNode.Variant.AnnotationQmWildcardBounds.buildNode(
                      wcbv.buildNode(rt))));
          break;
        case INVARIANT:
          listNode.add(TypeArgumentNode.Variant.ReferenceType.buildNode(rt));
          break;
      }
    }
    return TypeArgumentsNode.Variant.LtTypeArgumentListGt.buildNode(listNode);
  }

  ReferenceTypeNode toReferenceType(TypeSpecification ts) {
    ReferenceTypeNode rtn;
    if (ts.nDims != 0) {
      rtn = ReferenceTypeNode.Variant.ArrayType.buildNode(toArrayType(ts));
    } else if (ts.rawName.type == Name.Type.TYPE_PARAMETER) {
      rtn = ReferenceTypeNode.Variant.TypeVariable.buildNode(
          toTypeVariable(ts.rawName));
    } else {
      rtn = ReferenceTypeNode.Variant.ClassOrInterfaceType.buildNode(
          toClassOrInterfaceType(ts));
    }
    rtn.setStaticType(type(ts));
    return rtn;
  }

  TypeVariableNode toTypeVariable(Name rawName) {
    TypeVariableNode n = TypeVariableNode.Variant.AnnotationIdentifier
        .buildNode(toIdentifier(rawName));
    Optional<TypeInfo> ti = pool.r.resolve(rawName);
    if (ti.isPresent()) {
      n.setReferencedTypeInfo(ti.get());
    }
    return n;
  }

  ArrayTypeNode toArrayType(TypeSpecification ts) {
    Preconditions.checkArgument(ts.nDims != 0);
    ArrayTypeNode atn = ArrayTypeNode.Variant.TypeAnnotationDim.buildNode(
        toType(ts.withNDims(ts.nDims - 1)),
        DimNode.Variant.LsRs.buildNode());
    atn.setStaticType(type(ts));
    return atn;
  }

  TypeNode toType(TypeSpecification ts) {
    if (ts.nDims == 0 && ts.rawName.type == Name.Type.FIELD) {
      PrimitiveTypeNode ptn = toPrimitiveType(ts.rawName);
      return TypeNode.Variant.PrimitiveType.buildNode(ptn);
    }
    return TypeNode.Variant.ReferenceType.buildNode(toReferenceType(ts));
  }

  private static final ImmutableMap<Name, JminNodeVariant> FIELD_TO_NTV
  = ImmutableMap.<Name, JminNodeVariant>builder()
      .put(StaticType.T_BYTE.typeSpecification.rawName,
          IntegralTypeNode.Variant.Byte)
      .put(StaticType.T_CHAR.typeSpecification.rawName,
          IntegralTypeNode.Variant.Char)
      .put(StaticType.T_INT.typeSpecification.rawName,
          IntegralTypeNode.Variant.Int)
      .put(StaticType.T_LONG.typeSpecification.rawName,
          IntegralTypeNode.Variant.Long)
      .put(StaticType.T_SHORT.typeSpecification.rawName,
          IntegralTypeNode.Variant.Short)
      .put(StaticType.T_DOUBLE.typeSpecification.rawName,
          FloatingPointTypeNode.Variant.Double)
      .put(StaticType.T_FLOAT.typeSpecification.rawName,
          FloatingPointTypeNode.Variant.Float)
      .build();

  private static final ImmutableMap<Name, PrimitiveType> FIELD_TO_PT;
  static {
    ImmutableMap.Builder<Name, PrimitiveType> b = ImmutableMap.builder();
    for (PrimitiveType pt : StaticType.PRIMITIVE_TYPES) {
      Name fn = pt.typeSpecification.rawName;
      Preconditions.checkState(
          !(pt instanceof NumericType) || FIELD_TO_NTV.containsKey(fn));
      b.put(fn, pt);
    }
    FIELD_TO_PT = b.build();
  }

  static PrimitiveTypeNode toPrimitiveType(Name nm) {
    StaticType.PrimitiveType pt = FIELD_TO_PT.get(nm);
    if (StaticType.T_BOOLEAN.equals(pt)) {
      return PrimitiveTypeNode.Variant.AnnotationBoolean.buildNode()
          .setStaticType(StaticType.T_BOOLEAN);
    } else {
      NumericType nt = (NumericType) pt;
      JminNodeVariant ntv = FIELD_TO_NTV.get(nm);
      return PrimitiveTypeNode.Variant.AnnotationNumericType.buildNode(
          (nt.isFloaty
              ? NumericTypeNode.Variant.FloatingPointType
              : NumericTypeNode.Variant.IntegralType)
          .buildNode(ntv.buildNode(ImmutableList.of())))
          .setStaticType(nt);
    }
  }

  static IdentifierNode toIdentifier(Name rawName) {
    return IdentifierNode.Variant.Builtin.buildNode(rawName.identifier)
        .setNamePartType(rawName.type);
  }

  static PackageNameNode toPackageName(Name parent) {
    List<Name> pkgs = new ArrayList<>();
    for (Name p = parent;
         p != null && !Name.DEFAULT_PACKAGE.equals(p);
         p = p.parent) {
      pkgs.add(p);
    }
    PackageNameNode pkgNameNode = PackageNameNode.Variant
        .IdentifierDotIdentifier.buildNode();
    for (int i = pkgs.size(); --i >= 0;) {
      pkgNameNode.add(toIdentifier(pkgs.get(i)));
    }
    return pkgNameNode;
  }

  ClassTypeNode toClassType(TypeSpecification ts) {
    return ClassTypeNode.Variant.ClassOrInterfaceType
        .buildNode(toClassOrInterfaceType(ts))
        .setStaticType(type(ts));
  }
}
