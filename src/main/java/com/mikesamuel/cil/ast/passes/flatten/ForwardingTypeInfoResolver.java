package com.mikesamuel.cil.ast.passes.flatten;

import java.util.LinkedHashMap;
import java.util.Map;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.mikesamuel.cil.ast.meta.Name;
import com.mikesamuel.cil.ast.meta.TypeInfo;
import com.mikesamuel.cil.ast.meta.TypeInfoResolver;
import com.mikesamuel.cil.ast.meta.TypeSpecification;
import com.mikesamuel.cil.ast.meta.TypeSpecification.TypeBinding;

final class ForwardingTypeInfoResolver implements TypeInfoResolver {
  final TypeInfoResolver fallback;

  ForwardingTypeInfoResolver(TypeInfoResolver fallback) {
    this.fallback = fallback;
  }

  private final Map<Name, TypeInfo> forwardingTypeParameters =
      new LinkedHashMap<>();

  /**
   * Given an anonymous type, we look up the super type and
   * manufacture forwarding parameters so that the type
   * resolver will find the proper super-type for parameters
   * in the normal class created from the anonymous type.
   */
  Optional<TypeInfo> addForwardersFor(Name anonymousClassName) {
    Optional<TypeInfo> tiOpt = fallback.resolve(anonymousClassName);
    if (!tiOpt.isPresent()) { return Optional.absent(); }
    TypeInfo ti = tiOpt.get();
    if (ti.interfaces.isEmpty() && !ti.superType.isPresent()) {
      return Optional.of(ti);
    }
    TypeSpecification superTypeSpec = ti.interfaces.isEmpty()
        ? ti.superType.get() : ti.interfaces.get(0);
    Optional<TypeInfo> stiOpt = fallback.resolve(superTypeSpec.rawName);
    if (!stiOpt.isPresent()) { return Optional.of(ti); }
    TypeInfo sti = stiOpt.get();
    if (sti.parameters.isEmpty()) { return Optional.of(ti); }

    // We have parameters on the super-type.  For each one, create a forwarding
    // parameter with the same super type.
    ImmutableList.Builder<Name> forwardingParameters = ImmutableList.builder();
    ImmutableList.Builder<TypeBinding> bindings = ImmutableList.builder();
    for (Name parameter : sti.parameters) {
      Name forwardingParameter = ti.canonName.child(
          parameter.identifier, Name.Type.TYPE_PARAMETER);

      TypeInfo.Builder fptiBuilder = TypeInfo.builder(forwardingParameter);
      Optional<TypeInfo> ptiOpt = resolve(parameter);
      if (ptiOpt.isPresent()) {
        TypeInfo pti = ptiOpt.get();
        fptiBuilder.superType(pti.superType);
        fptiBuilder.interfaces(pti.interfaces);
        fptiBuilder.modifiers(pti.modifiers);
      }
      TypeInfo fpti = fptiBuilder.build();

      forwardingParameters.add(forwardingParameter);
      bindings.add(new TypeBinding(
          TypeSpecification.unparameterized(forwardingParameter)));
      this.forwardingTypeParameters.put(fpti.canonName, fpti);
    }

    // Now reconstruct TypeInfo for the anonymous class.
    TypeInfo.Builder newTiBuilder = ti.builder()
        .parameters(forwardingParameters.build());
    if (!ti.interfaces.isEmpty()) {
      newTiBuilder.interfaces(ImmutableList.of(
          ti.interfaces.get(0).withBindings(bindings.build())));
    } else {
      newTiBuilder.superType(Optional.of(
          ti.superType.get().withBindings(bindings.build())));
    }

    TypeInfo newTi = newTiBuilder.build();
    this.forwardingTypeParameters.put(newTi.canonName, newTi);
    return Optional.of(newTi);
  }

  @Override
  public Optional<TypeInfo> resolve(Name className) {
    TypeInfo ti = forwardingTypeParameters.get(className);
    if (ti != null) { return Optional.of(ti); }
    return fallback.resolve(className);
  }

}
