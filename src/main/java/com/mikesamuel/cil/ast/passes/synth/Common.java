package com.mikesamuel.cil.ast.passes.synth;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.common.base.Optional;
import com.google.common.base.Predicates;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Table;
import com.mikesamuel.cil.ast.j8.AnnotationNode;
import com.mikesamuel.cil.ast.j8.J8FileNode;
import com.mikesamuel.cil.ast.j8.MarkerAnnotationNode;
import com.mikesamuel.cil.ast.j8.ModifierNode;
import com.mikesamuel.cil.ast.meta.MemberInfoPool;
import com.mikesamuel.cil.ast.meta.Name;
import com.mikesamuel.cil.ast.meta.StaticType.TypePool;
import com.mikesamuel.cil.ast.meta.TypeInfo;
import com.mikesamuel.cil.ast.passes.NameAllocator;
import com.mikesamuel.cil.ast.passes.Temporaries;
import com.mikesamuel.cil.ast.passes.TypeNodeFactory;
import com.mikesamuel.cil.parser.Positioned;
import com.mikesamuel.cil.util.LogUtils;

final class Common {
  final Logger logger;
  final TypePool typePool;
  final MemberInfoPool memberInfoPool;
  final TypeNodeFactory factory;
  final Map<Name, UserDefinedType> byName = new LinkedHashMap<>();
  final Map<Name, EnumSet<UseType>> uses = new LinkedHashMap<>();
  final Multimap<Name, Erasure> bridges = LinkedHashMultimap.create();
  final NameAllocator nameAllocator;

  Common(
      Logger logger, TypePool typePool, MemberInfoPool memberInfoPool,
      TypeNodeFactory factory,
      Iterable<? extends J8FileNode> roots) {
    this.logger = logger;
    this.typePool = typePool;
    this.memberInfoPool = memberInfoPool;
    this.factory = factory;
    this.nameAllocator = NameAllocator.create(
        roots, Predicates.alwaysFalse(), typePool.r);
  }

  void error(Positioned pos, String message) {
    LogUtils.log(logger, Level.SEVERE, pos, message, null);
  }

  <T> Table<UserDefinedType, Name, T> grouped(Map<Name, T> m) {
    Table<UserDefinedType, Name, T> grouped = HashBasedTable.create();
    for (Map.Entry<Name, T> e : m.entrySet()) {
      Name nm = e.getKey();
      Name cn = nm.getContainingClass();
      UserDefinedType udt = byName.get(cn);
      if (udt != null) {
        grouped.put(udt, nm, e.getValue());
      }
    }
    return grouped;
  }

  static Name topLevelTypeOf(Name name) {
    Name topLevelName = name;
    for (Name nm = name; nm != null; nm = nm.parent.getContainingClass()) {
      topLevelName = nm;
    }
    return topLevelName.type == Name.Type.CLASS ? topLevelName : null;
  }

  ModifierNode makeSyntheticAnnotationModifier() {
    Optional<TypeInfo> synTiOpt = typePool.r.resolve(
        Temporaries.SYNTHETIC_ANNOTATION_NAME);
    if (synTiOpt.isPresent()) {
      return ModifierNode.Variant.Annotation.buildNode(
          AnnotationNode.Variant.MarkerAnnotation.buildNode(
              MarkerAnnotationNode.Variant.AtTypeName.buildNode(
                  factory.toTypeNameNode(
                      synTiOpt.get()))));
    }
    return null;
  }
}
