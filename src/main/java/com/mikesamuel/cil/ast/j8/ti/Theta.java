package com.mikesamuel.cil.ast.j8.ti;

import java.util.Map;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.mikesamuel.cil.ast.meta.Name;
import com.mikesamuel.cil.ast.meta.PartialTypeSpecification;
import com.mikesamuel.cil.ast.meta.TypeSpecification;
import com.mikesamuel.cil.ast.meta.TypeSpecification.TypeBinding;

/**
 * The mapping between callee type parameters and inference variable
 * placeholders.
 */
final class Theta {
  /** Maps the callee's type parameters to placeholders used in bounds. */
  final ImmutableMap<Name, InferenceVariable> theta;
  final ImmutableMap<InferenceVariable, Name> reverse;

  Theta(ImmutableMap<Name, InferenceVariable> theta) {
    this.theta = theta;

    ImmutableMap.Builder<InferenceVariable, Name> b = ImmutableMap.builder();
    for (Map.Entry<Name, InferenceVariable> e : theta.entrySet()) {
      b.put(e.getValue(), e.getKey());
    }
    reverse = b.build();
  }

  TypeSpecification cross(TypeSpecification ts) {
    InferenceVariable v = theta.get(ts.rawName);
    if (v != null) {
      return TypeSpecification.unparameterized(v.name);
    }
    return ts.withBindings(
        new Function<PartialTypeSpecification, ImmutableList<TypeBinding>>() {

          @Override
          public ImmutableList<TypeBinding> apply(
              PartialTypeSpecification pts) {
            ImmutableList<TypeBinding> bindings = pts.bindings();
            if (bindings.isEmpty()) {
              return bindings;
            }
            ImmutableList.Builder<TypeSpecification.TypeBinding> b =
                ImmutableList.builder();
            boolean changed = false;
            for (TypeSpecification.TypeBinding binding : bindings) {
              TypeSpecification bts = cross(binding.typeSpec);
              if (bts != binding.typeSpec) {
                b.add(new TypeSpecification.TypeBinding(
                    binding.variance, bts));
                changed = true;
              } else {
                b.add(binding);
              }
            }
            return changed ? b.build() : bindings;
          }

        });
  }
}
