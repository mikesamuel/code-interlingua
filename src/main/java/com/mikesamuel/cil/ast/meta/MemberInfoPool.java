package com.mikesamuel.cil.ast.meta;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

import com.google.common.base.Optional;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.mikesamuel.cil.ast.meta.StaticType.TypePool;
import com.mikesamuel.cil.ast.meta.TypeSpecification.TypeBinding;

/**
 * Pools information about member relationships.
 */
public final class MemberInfoPool {
  /** Used to resolve canonical names. */
  public final TypePool typePool;

  /** */
  public MemberInfoPool(TypePool typePool) {
    this.typePool = typePool;
  }

  /**
   * Maps member names to the names of members they cancel.
   * For fields, a field cancels fields that it hides.
   * For methods, a method cancels methods it overrides.
   */
  private final Multimap<Name, Name> cancels = HashMultimap.create();

  Collection<Name> cancelledBy(MemberInfo mi) {
    // This assumes there is one MemberInfo per canonName which should be
    // true for a proper canonResolver.
    if (!cancels.containsKey(mi.canonName)) {
      Collection<Name> cancelled;
      if (mi instanceof CallableInfo) {
        cancelled = overriddenBy((CallableInfo) mi);
      } else {
        cancelled = maskedBy((FieldInfo) mi);
      }
      cancels.putAll(mi.canonName, cancelled);
    }
    return Collections.unmodifiableCollection(cancels.get(mi.canonName));
  }

  private Collection<Name> maskedBy(FieldInfo fi) {
    Optional<TypeInfo> declaringTypeInfoOpt = typePool.r.resolve(
        fi.canonName.getContainingClass());
    if (declaringTypeInfoOpt.isPresent()) {
      TypeInfo dti = declaringTypeInfoOpt.get();
      class ScanParents {
        Set<Name> cancelled = Sets.newLinkedHashSet();
        Set<Name> typesVisited = Sets.newHashSet();
        void scan(TypeSpecification ts) {
          if (!typesVisited.add(ts.typeName)) {
            // It's illegal to implement an interface with two different
            // parameterizations (TODO: at least I think it is).
            return;
          }
          for (TypeSpecification superType
              // Even if the start type is a concrete type we still need to walk
              // interfaces to get default methods and static methods.
              : typePool.r.superTypesOf(ts)) {

            Optional<TypeInfo> tio = typePool.r.resolve(superType.typeName);
            if (!tio.isPresent()) { return; }
            TypeInfo ti = tio.get();
            for (MemberInfo mi : ti.declaredMembers) {
              if (mi instanceof FieldInfo
                  && mi.canonName.identifier.equals(fi.canonName.identifier)) {
                cancelled.add(mi.canonName);
              }
            }
          }
        }
      }
      ScanParents scanner = new ScanParents();
      scanner.scan(TypeSpecification.autoScoped(dti));
      return scanner.cancelled;
    }
    return ImmutableList.of();
  }

  private Collection<Name> overriddenBy(CallableInfo ci) {
    ImmutableList<TypeSpecification> erasedSig = erasedSignatureOf(
        ci, ImmutableMap.of());
    Optional<TypeInfo> declaringTypeInfoOpt = typePool.r.resolve(
        ci.canonName.getContainingClass());
    if (declaringTypeInfoOpt.isPresent()) {
      TypeInfo dti = declaringTypeInfoOpt.get();
      class ScanParents {
        Set<Name> cancelled = Sets.newLinkedHashSet();
        Set<Name> typesVisited = Sets.newHashSet();
        void scan(TypeSpecification ts) {
          if (!typesVisited.add(ts.typeName)) {
            // It's illegal to implement an interface with two different
            // parameterizations (TODO: at least I think it is).
            return;
          }
          for (TypeSpecification superType
              // Even if the start type is a concrete type we still need to walk
              // interfaces to get default methods and static methods.
              : typePool.r.superTypesOf(ts)) {

            Optional<TypeInfo> tio = typePool.r.resolve(superType.typeName);
            if (!tio.isPresent()) { return; }
            TypeInfo ti = tio.get();
            Map<Name, TypeBinding> typeParamMap = null;
            for (MemberInfo mi : ti.declaredMembers) {
              if (mi instanceof CallableInfo
                  && mi.canonName.identifier.equals(ci.canonName.identifier)) {
                // Method with same name.
                if (typeParamMap == null) {
                  typeParamMap = Maps.newLinkedHashMap();
                  int nBindings = superType.bindings.size();
                  if (nBindings == ti.parameters.size()) {  // Not raw
                    for (int i = 0; i < nBindings; ++i) {
                      typeParamMap.put(
                          ti.parameters.get(i), superType.bindings.get(i));
                    }
                  }
                }

                @SuppressWarnings("synthetic-access")
                ImmutableList<TypeSpecification> superTypeErasedSig =
                    erasedSignatureOf((CallableInfo) mi, typeParamMap);

                if (erasedSig.equals(superTypeErasedSig)) {
                  cancelled.add(mi.canonName);
                }
              }
            }
          }
        }
      }
      ScanParents scanner = new ScanParents();
      scanner.scan(TypeSpecification.autoScoped(dti));
      return scanner.cancelled;
    }
    return ImmutableList.of();
  }

  private ImmutableList<TypeSpecification> erasedSignatureOf(
      CallableInfo ci, Map<Name, TypeBinding> substMap) {
    ImmutableList.Builder<TypeSpecification> erasedTypes =
        ImmutableList.builder();

    ImmutableList<TypeSpecification> formalTypes = ci.getFormalTypes();
    for (TypeSpecification ft : formalTypes) {
      TypeSpecification ts = ft.subst(substMap);
      StaticType sft = typePool.type(ts, null, null);
      erasedTypes.add(sft.toErasedType().typeSpecification);
    }
    return erasedTypes.build();
  }

  /**
   * Members with the given name that are available on the containing type and
   * visible from scope.
   */
  public <MI extends MemberInfo>
  ImmutableList<ParameterizedMember<MI>> getMembers(
      Class<MI> memberType, String memberName, Name scope,
      TypeSpecification containingType) {

    final class Search {
      Set<Name> typesSeen = Sets.newHashSet();
      Set<Name> cancelled = Sets.newHashSet();
      ImmutableList.Builder<ParameterizedMember<MI>> out =
          ImmutableList.builder();

      void search(TypeSpecification declaringType) {
        if (!typesSeen.add(declaringType.typeName)) {
          return;
        }
        Optional<TypeInfo> tio = typePool.r.resolve(declaringType.typeName);
        if (!tio.isPresent()) {
          return;
        }
        TypeInfo ti = tio.get();

        for (MemberInfo mi : ti.declaredMembers) {
          if (!cancelled.contains(mi.canonName)
              && memberType.isInstance(mi)
              && mi.canonName.identifier.equals(memberName)
              && mi.accessibleFrom(scope, typePool.r)) {
            out.add(new ParameterizedMember<>(
                declaringType, memberType.cast(mi)));
            cancelled.addAll(cancelledBy(mi));
          }
        }
        for (TypeSpecification superType
            // Even if the start type is a concrete type we still need to walk
            // interfaces to get default methods and static methods.
            : typePool.r.superTypesOf(declaringType)) {
          search(superType);
        }
      }
    }

    Search search = new Search();
    search.search(containingType);

    return search.out.build();
  }

  /**
   * Bundles a member with the type bindings of its declaring type in context.
   */
  public static final class ParameterizedMember<MI extends MemberInfo> {
    /** The parameterized type that contains the member. */
    public final TypeSpecification declaringType;
    /** A member declared in declaringType.canonName. */
    public final MI member;

    ParameterizedMember(TypeSpecification declaringType, MI member) {
      this.declaringType = declaringType;
      this.member = member;
    }

    @Override
    public String toString() {
      return "(" + member.canonName + " in " + declaringType + ")";
    }
  }
}
