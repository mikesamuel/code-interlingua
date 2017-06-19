package com.mikesamuel.cil.xlate.common;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.Sets;
import com.google.common.collect.TreeMultimap;
import com.mikesamuel.cil.ast.meta.CallableInfo;
import com.mikesamuel.cil.ast.meta.MemberInfo;
import com.mikesamuel.cil.ast.meta.Name;
import com.mikesamuel.cil.ast.meta.TypeInfo;
import com.mikesamuel.cil.ast.meta.TypeInfoResolver;
import com.mikesamuel.cil.parser.SourcePosition;
import com.mikesamuel.cil.util.LogUtils;

/**
 * Collects normal Java 8 class names and maps them to flattened type names.
 * <p>
 * A flat type name is either
 * <ol>
 *   <li>A top-level class or interface type name.
 *   <li>A type variable scoped to a flat type declaration.
 *   <li>A type variable scoped to a method or constructor
 *       declared in a flat type.
 * </ol>
 * <p>
 * Flat types cannot be inner or anonymous.
 */
final class FlatTypes {
  final Logger logger;
  final TypeInfoResolver r;
  private boolean recording;

  FlatTypes(Logger logger, TypeInfoResolver r) {
    this.logger = logger;
    this.r = r;
  }

  /** True if recording by default on fetch. */
  final boolean isRecording() {
    return recording;
  }

  final void setRecording(boolean newRecording) {
    this.recording = newRecording;
  }


  /**
   * Map hierarchical (bumpy) class type names to flat class type names.
   * <p>
   * This is advisory until disambiguation but is guaranteed to be 1:1.
   */
  private final BiMap<BName, FName> typeNameMap = HashBiMap.create();


  /** Maps bumpy class names to info about their type parameters. */
  private final Map<FName, FlatParamInfo> paramInfo = new LinkedHashMap<>();

  /**
   * Types that may not be in a substitution map for substMaps.
   */
  private final Set<BName> unprocessedBumpyTypeParameters =
      Sets.newLinkedHashSet();

  private final Set<BName> processedBumpyTypeParameters =
      Sets.newLinkedHashSet();

  /**
   * Maps the names of flat type parameter names to bumpy ones.
   */
  private final Map<FName, BName> flatToBumpyTypeParameterNames =
      Maps.newLinkedHashMap();

  /**
   * The flat type name corresponding to the given bumpy name.
   * This is not guaranteed stable after {@link #disambiguate()}.
   */
  FName getFlatTypeName(BName bumpyTypeName) {
    // Type parameters need to be renamed in a way that takes into account
    // the context that allows outer type parameters to be wedged among an
    // inner types parameters.
    Preconditions.checkArgument(bumpyTypeName.name.type == Name.Type.CLASS);
    FName flatTypeName = typeNameMap.get(bumpyTypeName);
    if (flatTypeName == null) {
      if (recording) {
        recordType(bumpyTypeName);
        flatTypeName = typeNameMap.get(bumpyTypeName);
      } else {
        throw new IllegalArgumentException(bumpyTypeName.toString());
      }
    }
    return flatTypeName;
  }

  FName getFlatContextName(BName bumpyContextName) {
    switch (bumpyContextName.name.type) {
      case CLASS:
        return getFlatTypeName(bumpyContextName);
      case METHOD:
        return FName.of(
            getFlatTypeName(bumpyContextName.parent()).name
            .method(
                bumpyContextName.name.identifier,
                bumpyContextName.name.variant));
      case FIELD: case LOCAL:
        return FName.of(
            getFlatContextName(bumpyContextName.parent()).name
            .child(
                bumpyContextName.name.identifier,
                bumpyContextName.name.type));
      case AMBIGUOUS:
      case PACKAGE:
      case TYPE_PARAMETER:
        break;
    }
    throw new AssertionError(bumpyContextName);
  }

  BName getBumpyTypeName(FName flatTypeName) {
    if (flatTypeName.name.type == Name.Type.CLASS) {
      return Preconditions.checkNotNull(
          typeNameMap.inverse().get(flatTypeName), flatTypeName);
    } else {
      Preconditions.checkArgument(
          flatTypeName.name.type == Name.Type.TYPE_PARAMETER);
      processAllBumpyTypeParameters();
      return flatToBumpyTypeParameterNames.get(flatTypeName);
    }
  }

  static final class FlatParamInfo {
    final ImmutableList<FName> flatParametersInOrder;
    final ImmutableList<BName> bumpyParametersInOrder;
    /**
     * Maps from type parameters in scope inside a particular bumpy
     * type to flat type parameter names in scope in the equivalent flat type.
     */
    final ImmutableMap<BName, FName> substMap;

    FlatParamInfo(
        ImmutableList<BName> bumpyParametersInOrder,
        ImmutableList<FName> flatParametersInOrder,
        ImmutableMap<BName, FName> substMap) {
      this.flatParametersInOrder = flatParametersInOrder;
      this.bumpyParametersInOrder = bumpyParametersInOrder;
      this.substMap = substMap;
      Preconditions.checkArgument(
          bumpyParametersInOrder.size() == flatParametersInOrder.size());
      Preconditions.checkArgument(
          bumpyParametersInOrder.size() == substMap.size());
    }
  }

  static final FlatParamInfo EMPTY_FLAT_PARAM_INFO = new FlatParamInfo(
      ImmutableList.of(), ImmutableList.of(), ImmutableMap.of());

  FlatParamInfo getFlatParamInfo(BName contextName) {
    return getFlatParamInfo(getFlatContextName(contextName));
  }

  /**
   * Information about the type parameters present on a flattened type or method
   * declaration.
   */
  FlatParamInfo getFlatParamInfo(FName contextName) {
    Preconditions.checkArgument(
        contextName.name.type == Name.Type.CLASS
        && contextName.name.parent.type == Name.Type.PACKAGE
        || contextName.name.type == Name.Type.METHOD);

    processAllBumpyTypeParameters();

    FlatParamInfo info = paramInfo.get(contextName);
    return info != null ? info : EMPTY_FLAT_PARAM_INFO;
  }


  private void processAllBumpyTypeParameters() {
    if (!unprocessedBumpyTypeParameters.isEmpty()) {
      // Return to zero state.
      unprocessedBumpyTypeParameters.addAll(processedBumpyTypeParameters);
      processedBumpyTypeParameters.clear();
      paramInfo.clear();

      ImmutableSet<BName> sortedBumpyTypeNames = ImmutableSortedSet.copyOf(
          typeNameMap.keySet());

      ParameterFlattener pf = new ParameterFlattener(
          sortedBumpyTypeNames,
          unprocessedBumpyTypeParameters);
      ImmutableMap<FName, FlatParamInfo> justProcessed = pf.flatten();
      paramInfo.putAll(justProcessed);
      for (FlatParamInfo fpi : justProcessed.values()) {
        for (int i = 0, n = fpi.bumpyParametersInOrder.size(); i < n; ++i) {
          flatToBumpyTypeParameterNames.put(
              fpi.flatParametersInOrder.get(i),
              fpi.bumpyParametersInOrder.get(i));
        }
      }

      processedBumpyTypeParameters.addAll(unprocessedBumpyTypeParameters);
      unprocessedBumpyTypeParameters.clear();
    }
  }

  private final class ParameterFlattener {
    final Map<BName, Builder> containerToBuilder = new LinkedHashMap<>();
    final Set<BName> haveParent = new TreeSet<>();

    ParameterFlattener(
        Iterable<? extends BName> types,
        Iterable<? extends BName> typeParameters) {
      // Map a parent to its type parameters.
      for (BName typ : types) {
        if (!containerToBuilder.containsKey(typ)) {
          containerToBuilder.put(typ, new Builder());
        }
      }
      for (BName tp : typeParameters) {
        Preconditions.checkState(tp.name.type == Name.Type.TYPE_PARAMETER);
        BName parent = tp.parent();
        if (!containerToBuilder.containsKey(parent)) {
          containerToBuilder.put(parent, new Builder());
        }
      }

      // For each class, compile FlatParamInfo, renaming parameter names to
      // avoid duplication, and making sure to avoid introducing new conflicts
      // with methods.
      for (BName containerName :
           ImmutableList.copyOf(containerToBuilder.keySet())) {
        requireHaveParent(containerName);
      }
    }

    ImmutableMap<FName, FlatParamInfo> flatten() {
      ImmutableMap.Builder<FName, FlatParamInfo> b = ImmutableMap.builder();
      for (Map.Entry<BName, Builder> e : containerToBuilder.entrySet()) {
        BName nm = e.getKey();
        b.put(getFlatContextName(nm), e.getValue().build(nm));
      }
      return b.build();
    }

    private void requireHaveParent(BName containerName) {
      if (!haveParent.add(containerName)) { return; }

      Builder b = containerToBuilder.get(containerName);
      if (b == null) {
        containerToBuilder.put(containerName, b = new Builder());
      }

      switch (containerName.name.type) {
        case CLASS:
          Optional<TypeInfo> lastClass = r.resolve(containerName.name);
          if (!lastClass.isPresent()) {
            LogUtils.log(
                logger, Level.WARNING, (SourcePosition) null,
                "Missing type info for " + containerName, null);
            return;
          }
          b.prependParameters(lastClass.get().parameters);
          ancestor_loop:
          for (BName anc = containerName.parent();
               anc != null; anc = anc.parent()) {
            switch (anc.name.type) {
              case PACKAGE:
                break ancestor_loop;
              case CLASS:
                if (lastClass.isPresent()) {
                  TypeInfo ti = lastClass.get();
                  if (!Modifier.isStatic(ti.modifiers)) {
                    requireHaveParent(anc);
                    lastClass = r.resolve(anc.name);
                    if (lastClass.isPresent()) {
                      b.prependParameters(lastClass.get().parameters);
                    }
                    continue;
                  }
                }

                break ancestor_loop;
              case METHOD:
                // If it's static, then it's just because the containing method
                // is static, but the method's type parameter's are still in
                // scope.
                Optional<CallableInfo> ciOpt = r.resolveCallable(anc.name);
                if (ciOpt.isPresent()) {
                  CallableInfo ci = ciOpt.get();
                  b.prependParameters(ci.typeParameters);
                  if (!Modifier.isStatic(ci.modifiers)) {
                    continue;
                  }
                }
              break;
              case AMBIGUOUS:
              case FIELD:
              case LOCAL:
              case TYPE_PARAMETER:
                break;
            }
            throw new AssertionError(anc);
          }
          return;
        case METHOD:
          Optional<CallableInfo> ciOpt = r.resolveCallable(containerName.name);
          if (ciOpt.isPresent()) {
            b.prependParameters(ciOpt.get().typeParameters);
          }
          return;
        case AMBIGUOUS:
        case FIELD:
        case LOCAL:
        case PACKAGE:
        case TYPE_PARAMETER:
          break;
      }
      throw new AssertionError(containerName);
    }

    final class Builder {
      /** Bumpy parameters. */
      private final List<BName> params = new LinkedList<>();

      void prependParameters(List<? extends Name> typeParameters) {
        params.addAll(
            0, Lists.transform(typeParameters, BName.OF));
      }

      FlatParamInfo build(BName bumpyContainerName) {
        if (params.isEmpty()) {
          return EMPTY_FLAT_PARAM_INFO;
        }

        if (bumpyContainerName.name.type == Name.Type.METHOD) {
          // Just rebase.  No need to flatten.
          ImmutableList.Builder<FName> flatNames =
              ImmutableList.builder();
          ImmutableMap.Builder<BName, FName> bumpyNameToFlatName =
              ImmutableMap.builder();
          FName flatMethodName = FName.of(
              getFlatTypeName(bumpyContainerName.parent()).name
              .method(
                  bumpyContainerName.name.identifier,
                  bumpyContainerName.name.variant));
          for (BName param : params) {
            Preconditions.checkState(param.parent().equals(bumpyContainerName));
            FName flatName = FName.of(flatMethodName.name.child(
                param.name.identifier, param.name.type));
            flatNames.add(flatName);
            bumpyNameToFlatName.put(param, flatName);
          }
          return new FlatParamInfo(
              ImmutableList.copyOf(params),
              flatNames.build(),
              bumpyNameToFlatName.build());
        }
        Preconditions.checkArgument(
            bumpyContainerName.name.type == Name.Type.CLASS);

        // Make sure that no parameter name masks another parameter name.
        // We have a second constraint besides duplicate identifiers.
        // If the container has a method in it that uses a type variable from
        // its container, then we should not break that method.

        // For example,
        //   class C<X>
        //     class D<X> {
        //       class E {
        //         <X1 super X>
        //         List<X1> f() {
        //           return new List<X>();
        //   } } } }

        // If we flattened this to
        //     class C$D$E<X, X1> {
        //       <X1 super X1>
        //       X1 f() {
        //         return new List<X1>();
        //     } }
        // we have failed to preserve reference structure since the method's
        // <X1> masks the renamed X1 leading to the curious method type
        // parameter declaration
        //    <X1 super X1>

        Set<String> exclusions = Sets.newHashSet();
        Optional<TypeInfo> tiOpt = r.resolve(bumpyContainerName.name);
        assert tiOpt.isPresent();
        for (MemberInfo mi : tiOpt.get().getDeclaredMembers()) {
          if (mi instanceof CallableInfo && !Modifier.isStatic(mi.modifiers)) {
            CallableInfo ci = (CallableInfo) mi;
            for (Name p : ci.typeParameters) {
              exclusions.add(p.identifier);
            }
          }
        }

        FName flatContainerName = getFlatTypeName(bumpyContainerName);
        // We've flattened the class.  Don't allow a renamed type parameter to
        // mask the class name.
        exclusions.add(flatContainerName.name.identifier);


        // Now that we know what not to rename to, rename.
        ImmutableList.Builder<FName> flatNamesInReverse =
            ImmutableList.builder();
        ImmutableMap.Builder<BName, FName> bumpyNameToFlatName =
            ImmutableMap.builder();
        // By building in reverse, we preserve the innermost names where
        // possible which are most-often used.
        for (int i = params.size(); --i >= 0;) {
          BName bumpyParamName = params.get(i);
          int counter = 0;
          String flatIdentifier = bumpyParamName.name.identifier;
          while (exclusions.contains(flatIdentifier)) {
            flatIdentifier = bumpyParamName.name.identifier + "_" + counter++;
          }
          exclusions.add(flatIdentifier);
          FName flatParamName = FName.of(flatContainerName.name.child(
              flatIdentifier, bumpyParamName.name.type));
          flatNamesInReverse.add(flatParamName);
          bumpyNameToFlatName.put(bumpyParamName, flatParamName);
        }
        return new FlatParamInfo(
            ImmutableList.copyOf(params),
            flatNamesInReverse.build().reverse(),
            bumpyNameToFlatName.build());
      }
    }

  }

  void disambiguate() {
    // Construct a 1:1 map that gives preference to identity mappings for
    // public types.
    BiMap<BName, FName> unambiguous = HashBiMap.create();

    ImmutableSet<BName> sortedBumpyTypeNames = ImmutableSortedSet.copyOf(
        typeNameMap.keySet());

    // Reverse the mapping
    SetMultimap<FName, BName> flatToBumpy = TreeMultimap.create();
    for (BName bumpy : sortedBumpyTypeNames) {
      FName flat = flatRootOf(bumpy);
      boolean added = flatToBumpy.put(flat, bumpy);
      Preconditions.checkState(added);  // hash should be equiv to cmp
    }

    for (FName possibleFlatName : flatToBumpy.keySet()) {
      Set<BName> bumpyNames = flatToBumpy.get(possibleFlatName);
      if (bumpyNames.size() == 1) {
        FName dupe = unambiguous.put(
            Iterables.getOnlyElement(bumpyNames), possibleFlatName);
        Preconditions.checkState(dupe == null);
        continue;
      }

      // Choose unambiguous names for each.
      // First, see if there is at most one public type so that we can
      // preserve public names.
      BName unqualifiedBumpyName = null;
      for (BName bumpyName : bumpyNames) {
        Optional<TypeInfo> tiOpt = r.resolve(
            bumpyName.name.getContainingClass());
        if (tiOpt.isPresent() && Modifier.isPublic(tiOpt.get().modifiers)) {
          if (unqualifiedBumpyName == null) {
            unqualifiedBumpyName = bumpyName;
          } else {
            LogUtils.log(
                logger, Level.SEVERE, (SourcePosition) null,
                "Cannot flatten public type " + bumpyName + " to "
                    + possibleFlatName + " since that would conflict with "
                    + unqualifiedBumpyName,
                    null);
          }
        }
      }
      if (unqualifiedBumpyName == null) {
        unqualifiedBumpyName = bumpyNames.iterator().next();
      }

      // Now make sure that all bumpyNames appear as keys in unambiguous,
      // qualifying names as necessary.
      for (BName bumpyName : bumpyNames) {
        FName flatName;
        if (unqualifiedBumpyName.equals(bumpyName)) {
          flatName = possibleFlatName;
        } else {
          flatName = uniqueIn(
              Sets.union(
                  ImmutableSet.of(possibleFlatName),
                  unambiguous.values()),
              possibleFlatName);
          LogUtils.log(
              logger, Level.INFO, (SourcePosition) null,
              "Flattening type " + bumpyName + " to " + flatName,
              null);
        }
        unambiguous.put(bumpyName, flatName);
      }
    }

    // Swap in the unambiguous names.
    typeNameMap.clear();
    typeNameMap.putAll(unambiguous);

    paramInfo.clear();
    unprocessedBumpyTypeParameters.addAll(processedBumpyTypeParameters);
    processedBumpyTypeParameters.clear();
    flatToBumpyTypeParameterNames.clear();
    // Next call to processAllBumpyTypeParameters will rebuild the substitution
    // maps with the right containing type names.
  }

  private static FName uniqueIn(Set<? super FName> s, FName root) {
    int counter = 1;
    FName candidate = root;
    while (s.contains(candidate)) {
      candidate = FName.of(
          root.name.parent
          .child(root.name.identifier + "_" + counter, root.name.type));
      ++counter;
    }
    return candidate;
  }

  /**
   * Flattens types: {@code foo.bar.Baz<X>.Boo<Y>} &rarr;
   * {@code foo.bar.Baz$Boo<X, T>}.
   */
  void recordType(BName bumpyTypeName) {
    switch (bumpyTypeName.name.type) {
      case CLASS:
        if (!typeNameMap.containsKey(bumpyTypeName)) {
          FName flatTypeNameRoot = flatRootOf(bumpyTypeName);
          FName flatTypeName = uniqueIn(typeNameMap.values(), flatTypeNameRoot);
          typeNameMap.put(bumpyTypeName, flatTypeName);

          Optional<TypeInfo> tiOpt = r.resolve(bumpyTypeName.name);
          if (tiOpt.isPresent()) {
            TypeInfo ti = tiOpt.get();
            for (Name bumpyTypeParam : ti.parameters) {
              recordType(BName.of(bumpyTypeParam));
            }
            for (MemberInfo mi : ti.getDeclaredMembers()) {
              if (mi instanceof CallableInfo) {
                CallableInfo ci = (CallableInfo) mi;
                for (Name bumpyTypeParam : ci.typeParameters) {
                  recordType(BName.of(bumpyTypeParam));
                }
              }
            }
          }
        }
        return;
      case TYPE_PARAMETER:
        if (!processedBumpyTypeParameters.contains(bumpyTypeName)) {
          unprocessedBumpyTypeParameters.add(bumpyTypeName);
        }
        return;
      case FIELD:  // Primitive type
        return;
      case AMBIGUOUS:
      case LOCAL:
      case METHOD:
      case PACKAGE:
        // Not a type name.
        break;
    }
    throw new AssertionError(bumpyTypeName);
  }

  private static FName flatRootOf(BName bumpyTypeName) {
    if (bumpyTypeName.name.parent.type == Name.Type.PACKAGE) {
      // We're done.  Just record the fact that it exists.
      return FName.of(bumpyTypeName.name);
    } else {
      List<String> parts = new ArrayList<>();
      Name nm = bumpyTypeName.name;
      Preconditions.checkState(nm.type == Name.Type.CLASS);
      for (; nm.type != Name.Type.PACKAGE; nm = nm.parent) {
        parts.add(nm.identifier);
      }
      return FName.of(nm.child(
          Joiner.on('$').join(Lists.reverse(parts)),
          Name.Type.CLASS));
    }
  }
}
