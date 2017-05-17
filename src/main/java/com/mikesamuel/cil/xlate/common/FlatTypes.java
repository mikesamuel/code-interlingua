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
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
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
   * Map hierarchical class type names to flat class type names.
   * <p>
   * This is advisory until disambiguation when we make sure this is a proper
   * 1:1 mapping.
   */
  private final Map<Name, Name> flatTypeNames = new LinkedHashMap<>();

  private final Map<Name, FlatParamInfo> paramInfo = new LinkedHashMap<>();

  /**
   * Types that may not be in a substitution map for substMaps.
   */
  private final Set<Name> unprocessedBumpyTypeParameters =
      Sets.newLinkedHashSet();

  private final Set<Name> processedBumpyTypeParameters =
      Sets.newLinkedHashSet();

  Name getFlatTypeName(Name bumpyTypeName) {
    // Type parameters need to be renamed in a way that takes into account
    // the context that allows outer type parameters to be wedged among an
    // inner types parameters.
    Preconditions.checkArgument(bumpyTypeName.type == Name.Type.CLASS);
    Name flatTypeName = flatTypeNames.get(bumpyTypeName);
    if (flatTypeName == null) {
      if (recording) {
        recordType(bumpyTypeName);
        flatTypeName = flatTypeNames.get(bumpyTypeName);
      } else {
        throw new IllegalArgumentException(bumpyTypeName.toString());
      }
    }
    return flatTypeName;
  }

  static final class FlatParamInfo {
    final ImmutableList<Name> flatParametersInOrder;
    final ImmutableList<Name> bumpyParametersInOrder;
    /**
     * Maps from type parameters in scope inside a particular bumpy
     * type to flat type parameter names in scope in the equivalent flat type.
     */
    final ImmutableMap<Name, Name> substMap;

    FlatParamInfo(
        ImmutableList<Name> bumpyParametersInOrder,
        ImmutableList<Name> flatParametersInOrder,
        ImmutableMap<Name, Name> substMap) {
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

  /**
   * Information about the type parameters present on a flattened type or method
   * declaration.
   */
  FlatParamInfo getFlatParamInfo(Name contextName) {
    Preconditions.checkArgument(
        contextName.type == Name.Type.CLASS
        || contextName.type == Name.Type.METHOD);
    if (!unprocessedBumpyTypeParameters.isEmpty()) {
      // Return to zero state.
      unprocessedBumpyTypeParameters.addAll(processedBumpyTypeParameters);
      processedBumpyTypeParameters.clear();
      paramInfo.clear();

      ParameterFlattener pf = new ParameterFlattener(
          flatTypeNames.keySet(),
          unprocessedBumpyTypeParameters);
      paramInfo.putAll(pf.flatten());

      processedBumpyTypeParameters.addAll(unprocessedBumpyTypeParameters);
      unprocessedBumpyTypeParameters.clear();
    }

    FlatParamInfo info = paramInfo.get(contextName);
    return info != null ? info : EMPTY_FLAT_PARAM_INFO;
  }

  private final class ParameterFlattener {
    final Map<Name, Builder> containerToBuilder = new LinkedHashMap<>();
    final Set<Name> haveParent = new TreeSet<>();

    ParameterFlattener(
        Iterable<? extends Name> types,
        Iterable<? extends Name> typeParameters) {
      // Map a parent to its type parameters.
      for (Name typ : types) {
        if (!containerToBuilder.containsKey(typ)) {
          containerToBuilder.put(typ, new Builder());
        }
      }
      for (Name tp : typeParameters) {
        Preconditions.checkState(tp.type == Name.Type.TYPE_PARAMETER);
        if (!containerToBuilder.containsKey(tp.parent)) {
          containerToBuilder.put(tp.parent, new Builder());
        }
      }

      for (Name containerName :
           ImmutableList.copyOf(containerToBuilder.keySet())) {
        requireHaveParent(containerName);
      }

      // For each class, compile FlatParamInfo, renaming parameter names to avoid
      // duplication, and making sure to avoid introducing new conflicts with
      // methods.
    }

    ImmutableMap<Name, FlatParamInfo> flatten() {
      ImmutableMap.Builder<Name, FlatParamInfo> b = ImmutableMap.builder();
      for (Map.Entry<Name, Builder> e : containerToBuilder.entrySet()) {
        Name nm = e.getKey();
        b.put(nm, e.getValue().build(nm));
      }
      return b.build();
    }

    private void requireHaveParent(Name containerName) {
      if (!haveParent.add(containerName)) { return; }

      Builder b = containerToBuilder.get(containerName);
      if (b == null) {
        containerToBuilder.put(containerName, b = new Builder());
      }

      switch (containerName.type) {
        case CLASS:
          Optional<TypeInfo> lastClass = r.resolve(containerName);
          if (!lastClass.isPresent()) {
            LogUtils.log(
                logger, Level.WARNING, (SourcePosition) null,
                "Missing type info for " + containerName, null);
            return;
          }
          b.prependParameters(lastClass.get().parameters);
          ancestor_loop:
          for (Name anc = containerName.parent; anc != null; anc = anc.parent) {
            switch (anc.type) {
              case PACKAGE:
                break ancestor_loop;
              case CLASS:
                if (lastClass.isPresent()) {
                  TypeInfo ti = lastClass.get();
                  if (!Modifier.isStatic(ti.modifiers)) {
                    requireHaveParent(anc);
                    lastClass = r.resolve(anc);
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
                Optional<CallableInfo> ciOpt = r.resolveCallable(anc);
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
            throw new AssertionError(anc.type);
          }
          return;
        case METHOD:
          Optional<CallableInfo> ciOpt = r.resolveCallable(containerName);
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
      private final List<Name> params = new LinkedList<>();

      void prependParameters(List<? extends Name> typeParameters) {
        params.addAll(0, typeParameters);
      }

      FlatParamInfo build(Name bumpyContainerName) {
        if (params.isEmpty()) {
          return EMPTY_FLAT_PARAM_INFO;
        }

        if (bumpyContainerName.type == Name.Type.METHOD) {
          // Just rebase.  No need to flatten.
          ImmutableList.Builder<Name> flatNames =
              ImmutableList.builder();
          ImmutableMap.Builder<Name, Name> bumpyNameToFlatName =
              ImmutableMap.builder();
          Name flatMethodName = getFlatTypeName(bumpyContainerName.parent)
              .method(
                  bumpyContainerName.identifier,
                  bumpyContainerName.variant);
          for (Name param : params) {
            Preconditions.checkState(param.parent.equals(bumpyContainerName));
            Name flatName = flatMethodName.child(param.identifier, param.type);
            flatNames.add(flatName);
            bumpyNameToFlatName.put(param, flatName);
          }
          return new FlatParamInfo(
              ImmutableList.copyOf(params),
              flatNames.build(),
              bumpyNameToFlatName.build());
        }
        Preconditions.checkArgument(bumpyContainerName.type == Name.Type.CLASS);

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
        Optional<TypeInfo> tiOpt = r.resolve(bumpyContainerName);
        assert tiOpt.isPresent();
        for (MemberInfo mi : tiOpt.get().declaredMembers) {
          if (mi instanceof CallableInfo && !Modifier.isStatic(mi.modifiers)) {
            CallableInfo ci = (CallableInfo) mi;
            for (Name p : ci.typeParameters) {
              exclusions.add(p.identifier);
            }
          }
        }

        Name flatContainerName = getFlatTypeName(bumpyContainerName);
        // We've flattened the class.  Don't allow a renamed type parameter to
        // mask the class name.
        exclusions.add(flatContainerName.identifier);


        // Now that we know what not to rename to, rename.
        ImmutableList.Builder<Name> flatNamesInReverse =
            ImmutableList.builder();
        ImmutableMap.Builder<Name, Name> bumpyNameToFlatName =
            ImmutableMap.builder();
        // By building in reverse, we preserve the innermost names where
        // possible which are most-often used.
        for (int i = params.size(); --i >= 0;) {
          Name bumpyParamName = params.get(i);
          int counter = 0;
          String flatIdentifier = bumpyParamName.identifier;
          while (exclusions.contains(flatIdentifier)) {
            flatIdentifier = bumpyParamName.identifier + "_" + counter++;
          }
          exclusions.add(flatIdentifier);
          Name flatParamName = flatContainerName.child(
              flatIdentifier, bumpyParamName.type);
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
    // Construct a 1:1 map
    Map<Name, Name> unambiguous = new LinkedHashMap<>();

    for (Name.Type t
         : new Name.Type[] { Name.Type.CLASS, Name.Type.TYPE_PARAMETER }) {
      // Reverse the mapping
      SetMultimap<Name, Name> flatToBumpy = TreeMultimap.create();
      for (Map.Entry<Name, Name> e : flatTypeNames.entrySet()) {
        Name flat = e.getValue();
        Name bumpy = e.getKey();
        if (flat.type == t) {
          if (t == Name.Type.TYPE_PARAMETER) {
            // remap parent type if a type parameter.
            Name parentRemapped = Preconditions.checkNotNull(
                unambiguous.get(bumpy.parent));
            if (!(parentRemapped.equals(flat.parent))) {
              Name remapped = parentRemapped.child(flat.identifier, flat.type);
              flat = remapped;
            }
          }
          boolean added = flatToBumpy.put(flat, bumpy);
          Preconditions.checkState(added);  // hash should be equiv to cmp
        }
      }

      for (Name possibleFlatName : flatToBumpy.keySet()) {
        Set<Name> bumpyNames = flatToBumpy.get(possibleFlatName);
        if (bumpyNames.size() == 1) {
          Name dupe = unambiguous.put(
              Iterables.getOnlyElement(bumpyNames), possibleFlatName);
          Preconditions.checkState(dupe == null);
          continue;
        }

        // Choose unambiguous names for each.
        // First, see if there is at most one public type so that we can
        // preserve public names.
        Name unqualifiedBumpyName = null;
        for (Name bumpyName : bumpyNames) {
          Optional<TypeInfo> tiOpt = r.resolve(bumpyName.getContainingClass());
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
        int counter = 1;
        for (Name bumpyName : bumpyNames) {
          Name flatName;
          if (unqualifiedBumpyName.equals(bumpyName)) {
            flatName = possibleFlatName;
          } else {
            Name candidate;
            do {
              candidate = possibleFlatName.parent
                  .child(possibleFlatName.identifier + "_" + counter,
                      possibleFlatName.type);
              ++counter;
            } while (flatTypeNames.containsKey(candidate));
            flatName = candidate;
            LogUtils.log(
                logger, Level.INFO, (SourcePosition) null,
                "Flattening type " + bumpyName + " to " + flatName,
                null);
          }
          unambiguous.put(bumpyName, flatName);
        }
      }
    }

    // Swap in the unambiguous names.
    flatTypeNames.clear();
    flatTypeNames.putAll(unambiguous);

    paramInfo.clear();
    unprocessedBumpyTypeParameters.addAll(processedBumpyTypeParameters);
    processedBumpyTypeParameters.clear();
    // Next call to getSubstMap will rebuild the substitution maps with the
    // right containing type names.
  }

  /**
   * Flattens types: {@code foo.bar.Baz<X>.Boo<Y>} &rarr;
   * {@code foo.bar.Baz$Boo<X, T>}.
   */
  void recordType(Name bumpyTypeName) {
    switch (bumpyTypeName.type) {
      case CLASS:
        if (!flatTypeNames.containsKey(bumpyTypeName)) {
          Name flatTypeName;
          if (bumpyTypeName.parent.type == Name.Type.PACKAGE) {
            // We're done.  Just record the fact that it exists.
            flatTypeName = bumpyTypeName;
          } else {
            List<String> parts = new ArrayList<>();
            Name nm = bumpyTypeName;
            Preconditions.checkState(nm.type == Name.Type.CLASS);
            for (; nm.type != Name.Type.PACKAGE; nm = nm.parent) {
              parts.add(nm.identifier);
            }
            flatTypeName = nm.child(
                Joiner.on('$').join(Lists.reverse(parts)),
                Name.Type.CLASS);
          }
          flatTypeNames.put(bumpyTypeName, flatTypeName);
        }
        return;
      case TYPE_PARAMETER:
        if (!processedBumpyTypeParameters.contains(bumpyTypeName)) {
          unprocessedBumpyTypeParameters.add(bumpyTypeName);
        }
        return;
      case AMBIGUOUS:
      case FIELD:
      case LOCAL:
      case METHOD:
      case PACKAGE:
        // Not a type name.
        break;
    }
    throw new AssertionError(bumpyTypeName);
  }

}
