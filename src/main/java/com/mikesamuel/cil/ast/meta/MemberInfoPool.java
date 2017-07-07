package com.mikesamuel.cil.ast.meta;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import com.google.common.base.Functions;
import com.google.common.base.Optional;
import com.google.common.base.Throwables;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
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
      if (mi instanceof CallableInfo) {
        CallableInfo ci = (CallableInfo) mi;
        if (!Name.isSpecialMethodIdentifier(ci.canonName.identifier)) {
          // constructors are not inherited.
          Set<Name> ob = overriddenBy(ci);
          cancels.putAll(ci.canonName, ob);
          Set<Name> bd = bridgesTo(ci);
          cancels.putAll(ci.canonName, bd);
        }
      } else {
        cancels.putAll(mi.canonName, maskedBy((FieldInfo) mi));
      }
    }
    return Collections.unmodifiableCollection(cancels.get(mi.canonName));
  }

  /**
   * Names of methods in super-types that are overridden by the given one.
   * This includes methods that are transitively overridden, and includes
   * implementations of interface methods.
   */
  public ImmutableSet<Name> overriddenBy(CallableInfo ci) {
    try {
      return overriddenBy.get(ci);
    } catch (ExecutionException ex) {
      Throwables.propagate(ex);
      return null;
    }
  }

  private ImmutableSet<Name> bridgesTo(CallableInfo dest) {
    // org.springframework.core.BridgeMethodResolver is instructive here.
    Optional<TypeInfo> declaringTypeInfoOpt = typePool.r.resolve(
        dest.canonName.getContainingClass());
    if (!declaringTypeInfoOpt.isPresent()) {
      return ImmutableSet.of();
    }
    TypeInfo dti = declaringTypeInfoOpt.get();

    // If they override any of the same methods, then they are bridge methods.
    ImmutableSet<Name> overriddenByDest = overriddenBy(dest);

    ImmutableSet.Builder<Name> b = ImmutableSet.builder();
    for (MemberInfo mi : dti.getDeclaredMembers()) {
      if (!(mi instanceof CallableInfo)) {
        continue;
      }
      CallableInfo ci = (CallableInfo) mi;
      if (!ci.isBridge()) {
        continue;
      }
      if (!ci.canonName.identifier.equals(dest.canonName.identifier)) {
        continue;
      }
      if (ci.canonName.equals(dest.canonName)) {
        continue;
      }
      ImmutableSet<Name> overriddenByCi = overriddenBy(ci);
      if (!Collections.disjoint(overriddenByDest, overriddenByCi)) {
        b.add(ci.canonName);
      }
    }
    return b.build();
  }


  private Collection<Name> maskedBy(FieldInfo fi) {
    TypeSpecification typeSpec = TypeSpecification.autoScoped(
        fi.canonName.getContainingClass(), typePool.r);

    ImmutableSet.Builder<Name> b = ImmutableSet.builder();
    for (TypeSpecification superType
        // Even if the start type is a concrete type we still need to walk
        // interfaces to get default methods and static methods.
        : typePool.r.superTypesTransitiveOf(typeSpec)) {
      if (typeSpec.rawName.equals(superType.rawName)) {
        continue;
      }

      Optional<TypeInfo> tio = typePool.r.resolve(superType.rawName);
      if (!tio.isPresent()) { continue; }
      TypeInfo ti = tio.get();
      for (MemberInfo mi : ti.getDeclaredMembers()) {
        if (mi instanceof FieldInfo
            && mi.canonName.identifier.equals(fi.canonName.identifier)) {
          b.add(mi.canonName);
        }
      }
    }
    return b.build();
  }

  private final LoadingCache<CallableInfo, ImmutableSet<Name>> overriddenBy =
      CacheBuilder.newBuilder()
      .build(new CacheLoader<CallableInfo, ImmutableSet<Name>>() {

        @Override
        public ImmutableSet<Name> load(CallableInfo ci) throws Exception {
          // TODO: Take a close look at
          // //docs.oracle.com/javase/specs/jls/se8/html/jls-8.html#jls-8.4.2
          // and see if this properly matches the definition of subsignature.
          // I don't think it deals with overrides when there are raw parameter
          // types.
          Name containingClassName = ci.canonName.getContainingClass();
          @SuppressWarnings("synthetic-access")
          ImmutableList<TypeSpecification> erasedSig = erasedSignatureOf(
              ci, ImmutableMap.of());
          TypeSpecification typeSpec = TypeSpecification.autoScoped(
              containingClassName, typePool.r);

          ImmutableSet.Builder<Name> b = ImmutableSet.builder();
          for (TypeSpecification superType
              // Even if the start type is a concrete type we still need to
              // walk interfaces to get default methods and static methods.
              : typePool.r.superTypesTransitiveOf(typeSpec)) {
            if (superType.rawName.equals(typeSpec.rawName)) {
              continue;
            }

            Optional<TypeInfo> tio = typePool.r.resolve(superType.rawName);
            if (!tio.isPresent()) { continue; }
            TypeInfo ti = tio.get();
            Map<Name, TypeBinding> typeParamMap = null;
            for (MemberInfo mi : ti.getDeclaredMembers()) {
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
                  b.add(mi.canonName);
                }
              }
            }
          }
          return b.build();
        }

      });

  private ImmutableList<TypeSpecification> erasedSignatureOf(
      CallableInfo ci, Map<Name, TypeBinding> substMap) {
    ImmutableList.Builder<TypeSpecification> erasedTypes =
        ImmutableList.builder();

    ImmutableList<TypeSpecification> formalTypes = ci.getFormalTypes();
    for (TypeSpecification ft : formalTypes) {
      TypeSpecification ts = ft.subst(Functions.forMap(substMap, null));
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
      List<ParameterizedMember<MI>> out = Lists.newArrayList();

      void search(TypeSpecification declaringType) {
        if (!typesSeen.add(declaringType.rawName)) {
          return;
        }
        Optional<TypeInfo> tio = typePool.r.resolve(declaringType.rawName);
        if (!tio.isPresent()) {
          return;
        }
        TypeInfo ti = tio.get();

        for (MemberInfo mi : ti.getDeclaredMembers()) {
          if (!cancelled.contains(mi.canonName)
              && memberType.isInstance(mi)
              && mi.canonName.identifier.equals(memberName)
              && mi.accessibleFrom(scope, typePool.r)) {
            out.add(new ParameterizedMember<>(
                declaringType, memberType.cast(mi)));
            cancelled.addAll(cancelledBy(mi));
          }
        }
        if (memberType == CallableInfo.class
            && Name.isSpecialMethodIdentifier(memberName)) {
          // Special methods are not inherited from super-types.
          return;
        }
        for (TypeSpecification superType
            // Even if the start type is a concrete type we still need to walk
            // interfaces to get default methods and static methods.
            : typePool.r.superTypesTransitiveOf(declaringType)) {
          search(superType);
        }
      }
    }

    Search search = new Search();
    search.search(containingType);

    ImmutableList.Builder<ParameterizedMember<MI>> b = ImmutableList.builder();
    // We're not guaranteed to visit non-bridge methods before their bridge
    // methods, so re-filter here.
    for (ParameterizedMember<MI> m : search.out) {
      if (!search.cancelled.contains(m.member.canonName)) {
        b.add(m);
      }
    }
    return b.build();
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
