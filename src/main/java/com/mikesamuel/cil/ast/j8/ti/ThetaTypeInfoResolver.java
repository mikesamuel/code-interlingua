package com.mikesamuel.cil.ast.j8.ti;

import java.lang.reflect.Modifier;
import java.util.Map;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.mikesamuel.cil.ast.meta.JavaLang;
import com.mikesamuel.cil.ast.meta.Name;
import com.mikesamuel.cil.ast.meta.TypeInfo;
import com.mikesamuel.cil.ast.meta.TypeInfoResolver;
import com.mikesamuel.cil.ast.meta.TypeSpecification;

final class ThetaTypeInfoResolver implements TypeInfoResolver {

  final ImmutableMap<Name, TypeInfo> typeInfo;
  final TypeInfoResolver fallback;

  public ThetaTypeInfoResolver(
      Theta theta,
      TypeInfoResolver fallback) {
    // Make sure that we own the alpha name-space.
    Preconditions.checkArgument(
        !fallback.resolve(InferenceVariable.ALPHA_CONTAINER).isPresent());
    ImmutableList.Builder<Name> alphas = ImmutableList.builder();
    for (InferenceVariable iv : theta.theta.values()) {
      alphas.add(iv.name);
    }
    ImmutableMap.Builder<Name, TypeInfo> b = ImmutableMap.builder();
    b.put(
        InferenceVariable.ALPHA_CONTAINER,
        (TypeInfo.builder(InferenceVariable.ALPHA_CONTAINER)
            .parameters(alphas.build())
            .superType(Optional.of(JavaLang.JAVA_LANG_OBJECT))
            .modifiers(Modifier.PRIVATE | Modifier.FINAL | Modifier.INTERFACE)
            .build()));
    for (Map.Entry<Name, InferenceVariable> e : theta.theta.entrySet()) {
      Name alphaName = e.getValue().name;
      b.put(
          alphaName,
          (TypeInfo.builder(alphaName)
              .superType(Optional.of(
                  TypeSpecification.unparameterized(e.getKey())))
              .build()));
    }
    this.typeInfo = b.build();
    this.fallback = fallback;
  }

  @Override
  public Optional<TypeInfo> resolve(Name typeName) {
    TypeInfo ti = typeInfo.get(typeName);
    return ti != null ? Optional.of(ti) : fallback.resolve(typeName);
  }

}
