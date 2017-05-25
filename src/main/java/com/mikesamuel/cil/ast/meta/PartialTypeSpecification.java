package com.mikesamuel.cil.ast.meta;

import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;

/**
 * Part of a type specification.
 */
public abstract class PartialTypeSpecification {
  /**
   * The name of the
   */
  public abstract Name getRawName();

  /**
   * The specification for the partial type that contains this partial type.
   */
  public abstract @Nullable PartialTypeSpecification parent();

  /**
   * The specification for the bindings for this partial type.
   */
  public abstract ImmutableList<TypeSpecification.TypeBinding> bindings();

  abstract PartialTypeSpecification withBindings(
      Function<? super PartialTypeSpecification,
               ? extends Iterable<TypeSpecification.TypeBinding>>
      newBindings);

  protected abstract PartialTypeSpecification canon(
      TypeInfoResolver r, Set<Name> resolving);

  protected ImmutableList<TypeSpecification.TypeBinding> getCanonBindings(
      TypeInfoResolver r, Set<Name> resolving) {
    ImmutableList<TypeSpecification.TypeBinding> bindings = bindings();
    if (bindings.isEmpty()) {
      return bindings;
    }
    ImmutableList<Name> parameters = ImmutableList.of();
    {
      Name nm = getRawName();
      Optional<TypeInfo> infoOpt = r.resolve(nm.getContainingClass());
      if (!infoOpt.isPresent()) {
        return bindings;
      }
      switch (nm.type) {
        case CLASS:
          parameters = infoOpt.get().parameters;
          break;
        case METHOD:
          for (MemberInfo mi : infoOpt.get().getDeclaredMembers()) {
            if (mi.canonName.equals(nm)) {
              parameters = ((CallableInfo) mi).typeParameters;
              break;
            }
          }
          break;
        default:
          throw new AssertionError(nm);
      }
    }
    ImmutableList.Builder<TypeSpecification.TypeBinding> canonBindings = null;
    for (int i = 0, n = bindings.size(); i < n; ++i) {
      TypeSpecification.TypeBinding b = bindings.get(i);
      TypeSpecification.TypeBinding canon;
      if (b.typeSpec == null) {
        TypeSpecification paramBaseType = JavaLang.JAVA_LANG_OBJECT;

        find_type_parameter_bound:
        if (parameters.size() > i) {
          TypeSpecification paramSpec = TypeSpecification.unparameterized(
              parameters.get(i));
          // Handle cases where one type parameter extends another as in
          // <T1, T2 extends T1>
          while (true) {
            Optional<TypeInfo> paramInfoOpt = r.resolve(paramSpec.rawName);
            if (!paramInfoOpt.isPresent()) {
              break find_type_parameter_bound;
            }
            TypeInfo paramInfo = paramInfoOpt.get();
            if (!paramInfo.superType.isPresent()) {
              break find_type_parameter_bound;
            }
            TypeSpecification paramSuperType = paramInfo.superType.get();
            paramSpec = paramSuperType;
            if (paramSuperType.rawName.type == Name.Type.CLASS) {
              break;
            }
          }
          paramBaseType = paramSpec;
        }
        if (resolving.add(paramBaseType.rawName)) {
          paramBaseType = paramBaseType.canon(r, resolving);
          resolving.remove(paramBaseType.rawName);
        } else {
          // TODO: Does this occur in legit cases?
          // TODO: testcase (non-legit but parses)
          //   class C<T extends C<?>> {
          //     { C<?> c = null; }
          //   }
          paramBaseType = StaticType.ERROR_TYPE.typeSpecification;
        }
        canon = new TypeSpecification.TypeBinding(b.variance, paramBaseType);
      } else {
        TypeSpecification bspec = b.typeSpec.canon(r);
        canon = bspec == b.typeSpec
            ? b : new TypeSpecification.TypeBinding(b.variance, bspec);
      }
      if (canonBindings == null && canon != b) {
        canonBindings = ImmutableList.builder();
        canonBindings.addAll(bindings.subList(0, i));
      }
      if (canonBindings != null) {
        canonBindings.add(canon);
      }
    }
    return canonBindings != null ? canonBindings.build() : bindings;
  }

  @Override
  public final String toString() {
    StringBuilder sb = new StringBuilder();
    appendToStringBuilder(sb);
    return sb.toString();
  }

  protected void appendToStringBuilder(StringBuilder sb) {
    PartialTypeSpecification parent = parent();
    if (parent != null) {
      parent.appendToStringBuilder(sb);
    }
    Name rawName = getRawName();
    Name.appendInternalNamePart(
        rawName.parent != null ? rawName.parent.type : null,
        rawName.identifier, rawName.type, rawName.variant, sb);
    ImmutableList<TypeSpecification.TypeBinding> bindings = bindings();
    if (!bindings.isEmpty()) {
      String pre = "<";
      for (TypeSpecification.TypeBinding b : bindings) {
        sb.append(pre);
        pre = ", ";
        sb.append(b);
      }
      sb.append('>');
    }
  }

  /**
   * Constructs a type specification from a name.
   *
   * @param typeName a qualified unambiguous name.
   * @param bindingsForName called once for each name part that refers to a
   *    class or interface type.
   */
  public static PartialTypeSpecification fromName(
      Name typeName,
      Function<? super Name, ? extends List<TypeSpecification.TypeBinding>>
          bindingsForName) {
    if (typeName.type == Name.Type.PACKAGE) {
      return new PackageSpecification(typeName);
    }

    PartialTypeSpecification parent = null;
    if (typeName.parent != null) {
      parent = fromName(typeName.parent, bindingsForName);
    }

    List<TypeSpecification.TypeBinding> bindings;
    switch (typeName.type) {
      case FIELD:
        return new TypeSpecification(parent, typeName, ImmutableList.of(), 0);
      case METHOD: {
        Name declaringClass = typeName.getContainingClass();
        bindings = bindingsForName.apply(declaringClass);
        return new MethodTypeContainer(
            (TypeSpecification) parent,  // methods are declared in classes.
            typeName, bindings);
      }
      case CLASS:
        bindings = bindingsForName.apply(typeName);
        return new TypeSpecification(parent, typeName, bindings, 0);
      case TYPE_PARAMETER:
        return new TypeSpecification(
            TypeSpecification.autoScopedPartial(
                typeName.parent,
                TypeInfoResolver.Resolvers.nullResolver()),
            typeName.identifier,
            typeName.type,
            ImmutableList.of(),
            0);
      case PACKAGE:  // Should have been handled above.
      case LOCAL:  // Bad input
      case AMBIGUOUS:  // Bad input
        break;
    }
    throw new AssertionError(typeName);
  }

}
