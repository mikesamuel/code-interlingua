package com.mikesamuel.cil.ast.meta;

import java.util.Set;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

/**
 * Specifies a method that can contain type declarations.
 */
public final class MethodTypeContainer extends PartialTypeSpecification {
  /** The type that contains the method. */
  public final TypeSpecification parent;
  final Name name;
  final ImmutableList<TypeSpecification.TypeBinding> bindings;

  /**
   * @param parent the type that contains the method.
   * @param name the method name including a non-zero variant.
   * @param bindings for the method's declared type parameters.
   */
  public MethodTypeContainer(
      TypeSpecification parent, Name name,
      Iterable<? extends TypeSpecification.TypeBinding> bindings) {
    Preconditions.checkArgument(name.parent == parent.rawName);
    Preconditions.checkArgument(name.type == Name.Type.METHOD);
    this.parent = Preconditions.checkNotNull(parent);
    this.name = name;
    this.bindings = ImmutableList.copyOf(bindings);
  }

  @Override
  public MethodTypeContainer derive(
      Iterable<? extends TypeSpecification.TypeBinding> newBindings,
      PartialTypeSpecification newParent) {
    return new MethodTypeContainer(
        (TypeSpecification) newParent,
        parent.rawName.method(name.identifier, name.variant),
        newBindings);
  }

  @Override
  public Name getRawName() {
    return name;
  }

  @Override
  public PartialTypeSpecification parent() {
    return parent;
  }

  @Override
  public ImmutableList<TypeSpecification.TypeBinding> bindings() {
    return bindings;
  }

  @Override
  MethodTypeContainer withBindings(
      Function<? super PartialTypeSpecification,
               ? extends Iterable<TypeSpecification.TypeBinding>>
      newBindings) {
    Iterable<TypeSpecification.TypeBinding> newBindingList =
        newBindings.apply(this);
    TypeSpecification newParent = parent.withBindings(newBindings);
    if (newBindingList != null || newParent != parent) {
      return new MethodTypeContainer(
          newParent, name, newBindingList != null ? newBindingList : bindings);
    }
    return this;
  }

  @Override
  protected MethodTypeContainer canon(
      TypeInfoResolver r, Set<Name> resolving) {
    TypeSpecification canonParent = parent.canon(r, resolving);
    ImmutableList<TypeSpecification.TypeBinding> canonBindings =
        getCanonBindings(r, resolving);

    return canonBindings != bindings || parent != canonParent
        ? new MethodTypeContainer(canonParent, name, canonBindings)
        : this;

  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((bindings == null) ? 0 : bindings.hashCode());
    result = prime * result + ((name == null) ? 0 : name.hashCode());
    result = prime * result + ((parent == null) ? 0 : parent.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    MethodTypeContainer other = (MethodTypeContainer) obj;
    if (bindings == null) {
      if (other.bindings != null) {
        return false;
      }
    } else if (!bindings.equals(other.bindings)) {
      return false;
    }
    if (name == null) {
      if (other.name != null) {
        return false;
      }
    } else if (!name.equals(other.name)) {
      return false;
    }
    if (parent == null) {
      if (other.parent != null) {
        return false;
      }
    } else if (!parent.equals(other.parent)) {
      return false;
    }
    return true;
  }

}
