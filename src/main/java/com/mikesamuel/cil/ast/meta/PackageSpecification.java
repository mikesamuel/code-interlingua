package com.mikesamuel.cil.ast.meta;

import java.util.Set;

import javax.annotation.Nullable;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

/**
 * Specifies the package for a whole {@link TypeSpecification}.
 */
public final class PackageSpecification extends PartialTypeSpecification {
  /** The package specification for the default package. */
  public static final PartialTypeSpecification DEFAULT_PACKAGE =
      new PackageSpecification(Name.DEFAULT_PACKAGE);

  /** The name of the package. */
  public final Name packageName;

  /** */
  public PackageSpecification(Name name) {
    Preconditions.checkArgument(name.type == Name.Type.PACKAGE);
    this.packageName = name;
  }

  @Override
  public Name getRawName() {
    return packageName;
  }

  @Override
  public @Nullable PartialTypeSpecification parent() {
    return null;
  }

  @Override
  public ImmutableList<TypeSpecification.TypeBinding> bindings() {
    return ImmutableList.of();
  }

  @Override
  PackageSpecification withBindings(
      Function<? super PartialTypeSpecification,
               ? extends Iterable<TypeSpecification.TypeBinding>>
      newBindings) {
    return this;
  }

  @Override
  public PackageSpecification derive(
      Iterable<? extends TypeSpecification.TypeBinding> newBindings,
      PartialTypeSpecification newParent) {
    Preconditions.checkArgument(Iterables.isEmpty(newBindings));
    Preconditions.checkArgument(newParent == null);
    return this;
  }


  @Override
  protected PackageSpecification canon(
      TypeInfoResolver r, Set<Name> resolving) {
    return this;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result
        + ((packageName == null) ? 0 : packageName.hashCode());
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
    PackageSpecification other = (PackageSpecification) obj;
    if (packageName == null) {
      if (other.packageName != null) {
        return false;
      }
    } else if (!packageName.equals(other.packageName)) {
      return false;
    }
    return true;
  }

  @Override
  protected void appendToStringBuilder(StringBuilder sb) {
    packageName.appendInternalNameString(sb);
  }
}
