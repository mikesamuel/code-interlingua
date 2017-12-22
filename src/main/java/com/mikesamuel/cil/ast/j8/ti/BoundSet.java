package com.mikesamuel.cil.ast.j8.ti;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.mikesamuel.cil.ast.meta.JavaLang;
import com.mikesamuel.cil.ast.meta.StaticType;
import com.mikesamuel.cil.ast.meta.TypeSpecification;
import com.mikesamuel.cil.ast.meta.StaticType.PrimitiveType;
import com.mikesamuel.cil.ast.meta.StaticType.TypePool;
import com.mikesamuel.cil.ast.meta.StaticType.TypePool.ReferenceType;
import com.mikesamuel.cil.util.LogUtils;

/**
 * Maps inference variables to upper and lower bounds on other type variables
 * as defined in 18.1.3.
 */
final class BoundSet {
  /**
   * Covers both bounds of the form
   * <ul>
   *   <li>S =  T, where at least one of S or T is an inference variable: S is the same as T.</li>
   *   <li>S <: T, where at least one of S or T is an inference variable: S is a sub-type of T.</li>
   *   <li>S <= T, where at least one of S or T is an inference variable: TODO.</li>
   * </ul>
   */
  final ImmutableSet<Bound> bounds;
  /**
   * True if {@link ConstConstraingFormula#FALSE false} was admitted
   * meaning that there are no solutions within bounds.
   */
  final boolean isBoundable;
  /**
   * Bounds of the form throws &alpha;: The inference variable &alpha; appears
   * in a throws clause.
   */
  final ImmutableSet<InferenceVariable> thrown;

  BoundSet(
      Iterable<? extends Bound> bounds,
      boolean isBoundable,
      Iterable<? extends InferenceVariable> thrown) {
    this.bounds = ImmutableSet.copyOf(bounds);
    this.isBoundable = isBoundable;
    this.thrown = ImmutableSet.copyOf(thrown);
  }

  public BoundSet merge(BoundSet b) {
    BoundSet a = this;

    ImmutableSet<Bound> mergedBounds =
        ImmutableSet.<Bound>builder()
        .addAll(a.bounds)
        .addAll(b.bounds)
        .build();

    ImmutableSet<InferenceVariable> mergedThrown =
        ImmutableSet.<InferenceVariable>builder()
        .addAll(a.thrown)
        .addAll(b.thrown)
        .build();

    return new BoundSet(
        mergedBounds, a.isBoundable && b.isBoundable, mergedThrown);
  }

  BoundSet withThrown(Set<? extends InferenceVariable> typeParametersThrown) {
    if (typeParametersThrown.isEmpty()) {
      return this;
    }
    return new BoundSet(
        bounds, isBoundable,
        Sets.union(this.thrown, typeParametersThrown));
  }

  Optional<ImmutableMap<InferenceVariable, ReferenceType>> getResolutions() {
    if (!isBoundable) {
      return Optional.absent();
    }
    ImmutableMap.Builder<InferenceVariable, ReferenceType> b =
        ImmutableMap.builder();
    for (Bound bound : bounds) {
      if (bound instanceof Resolution) {
        Resolution r = (Resolution) bound;
        b.put(r.var, r.resolution);
      }
    }
    return Optional.of(b.build());
  }

  BoundSet resolve(Theta theta) {
    // https://docs.oracle.com/javase/specs/jls/se8/html/jls-18.html#jls-18.4
    // Given a bound set that does not contain the bound false, a subset of
    // the inference variables mentioned by the bound set may be resolved.
    // This means that a satisfactory instantiation may be added to the set
    // for each inference variable, until all the requested variables have
    // instantiations.

    BoundSet bs = this;
    for (;;) {
      ImmutableMap<InferenceVariable, ReferenceType> insts =
          bs.instantiateSome(theta);
      if (insts == null) {
        // incorporate false
        return new BoundSet(bs.bounds, false, bs.thrown);
      }
      if (insts.isEmpty()) {
        break;
      }
      ImmutableList.Builder<Bound> newBounds = ImmutableList.builder();
      for (Bound b : bs.bounds) {
        newBounds.add(b.subst(insts));
      }
      for (Map.Entry<InferenceVariable, ReferenceType> e : insts.entrySet()) {
        newBounds.add(new Resolution(e.getKey(), e.getValue()));
      }
      bs = new BoundSet(newBounds.build(), bs.isBoundable, bs.thrown);
    }
    return bs;
  }

  private ImmutableMap<InferenceVariable, ReferenceType> instantiateSome(
      Theta theta) {
    if (!isBoundable) {
      return null;
    }
    ResolutionOrder order;
    {
      ResolutionOrder.Builder orderBuilder = ResolutionOrder.builder(this);
      for (Bound b : this.bounds) {
        b.buildResolutionOrderGraph(orderBuilder);
      }
      order = orderBuilder.build();
    }

    TypePool thetaTypePool = theta.thetaTypePool;

    Multimap<InferenceVariable, Bound> boundsByMention =
        LinkedHashMultimap.create();
    Set<InferenceVariable> resolved = new LinkedHashSet<>();
    List<CaptureRelation> captureRelations = new ArrayList<>();
    for (Bound b : bounds) {
      if (b instanceof Resolution) {
        resolved.add(((Resolution) b).var);
      } else if (b instanceof CaptureRelation) {
        captureRelations.add((CaptureRelation) b);
      } else {
        for (InferenceVariable v : b.mentioned()) {
          boundsByMention.put(v, b);
        }
      }
    }

    ImmutableMap.Builder<InferenceVariable, ReferenceType> instantiations =
        ImmutableMap.builder();

    for (ResolutionOrder.Clique clique : order.cliquesInResolutionOrder) {
      // Otherwise, let { α1, ..., αn } be a non-empty subset of
      // uninstantiated variables in V.
      ImmutableSet<InferenceVariable> vars = clique.vars();
      Set<InferenceVariable> unresolved = new LinkedHashSet<>(vars);
      unresolved.removeAll(resolved);
      vars = ImmutableSet.copyOf(unresolved);
      if (vars.isEmpty()) { continue; }

      boolean allCaptured = false;
      for (CaptureRelation cr : captureRelations) {
        if (cr.alphas.containsAll(unresolved)) {
          allCaptured = true;
          break;
        }
      }

      if (!allCaptured) {
        // If the bound set does not contain a bound of the form
        // G<..., αi, ...> = capture(G<...>) for all i (1 ≤ i ≤ n), then a
        // candidate instantiation Ti is defined for each αi:
        for (InferenceVariable var : vars) {
          if (resolved.contains(var)) { continue; }
          List<ReferenceType> lowerBounds = new ArrayList<>();
          List<ReferenceType> upperBounds = new ArrayList<>();
          for (Bound b : boundsByMention.get(var)) {
            if (b instanceof SimpleBound) {
              SimpleBound sb = (SimpleBound) b;
              if (sb.right.equals(var) && sb.left instanceof NominalType) {
                StaticType t = ((NominalType) sb.left).t;
                if (InferenceVariable.isProperType(t)
                    && !thetaTypePool.T_NULL.equals(t)) {
                  lowerBounds.add(maybeBox(t, thetaTypePool));
                }
              } else if (sb.left.equals(var)
                         && sb.right instanceof NominalType) {
                StaticType t = ((NominalType) sb.right).t;
                if (InferenceVariable.isProperType(t)
                    && !thetaTypePool.T_NULL.equals(t)) {
                  upperBounds.add(maybeBox(t, thetaTypePool));
                }
              }
            }
          }

          StaticType r;
          if (!lowerBounds.isEmpty()) {
            // If αi has one or more proper lower bounds, L1, ..., Lk, then
            // Ti = lub(L1, ..., Lk) (§4.10.4).
            r = thetaTypePool.leastUpperBound(lowerBounds);
          }
          // Otherwise, if the bound set contains throws αi, and the proper
          // upper bounds of αi are, at most, Exception, Throwable, and
          // Object, then Ti = RuntimeException.
          else if (thrown.contains(var)
                   && onlyContainsExceptionOrSuperTypes(upperBounds)) {
            r = thetaTypePool.type(
                JavaLang.JAVA_LANG_RUNTIMEEXCEPTION, theta.pos, theta.logger);
          }
          // Otherwise, where αi has proper upper bounds U1, ..., Uk,
          // Ti = glb(U1, ..., Uk) (§5.1.10).
          else if (!upperBounds.isEmpty()) {
            TypeSpecification ts = upperBounds.get(0).typeSpecification;
            for (int i = 1, n = upperBounds.size(); i < n; ++i) {
              ts = thetaTypePool.glb(ts, upperBounds.get(i).typeSpecification);
            }
            r = thetaTypePool.type(ts, theta.pos, theta.logger);
          } else {
            LogUtils.log(
                theta.logger, Level.SEVERE, theta.pos,
                "Cannot compute bounds for " + var + " : "
                + theta.reverse.get(var), null);
            continue;
          }
          if (r instanceof ReferenceType) {
            instantiations.put(var, (ReferenceType) r);
          } else {
            LogUtils.log(
                theta.logger, Level.SEVERE, theta.pos,
                "Cannot bind " + var + " : " + theta.reverse.get(var)
                + " to non reference type " + r, null);
          }
        }
        break;
      } else {
        throw new Error("TODO resolve");
      }
    }
    return instantiations.build();
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("BoundSet{");
    if (!this.isBoundable) { sb.append(" !boundable"); }
    if (!this.bounds.isEmpty()) {
      sb.append(" bounds").append(bounds);
    }
    if (!this.thrown.isEmpty()) {
      sb.append(" thrown").append(thrown);
    }
    sb.append("}");
    return sb.toString();
  }

  private static ReferenceType maybeBox(StaticType t, TypePool typePool) {
    if (t instanceof PrimitiveType) {
      return (ReferenceType) typePool.type(
          TypeSpecification.unparameterized(((PrimitiveType) t).wrapperType),
          null, null);
    }
    return (ReferenceType) t;
  }

  private static final ImmutableSet<TypeSpecification> EXCEPTION_AND_SUPERS =
      ImmutableSet.<TypeSpecification>of(
          JavaLang.JAVA_LANG_OBJECT,
          JavaLang.JAVA_LANG_THROWABLE,
          JavaLang.JAVA_LANG_EXCEPTION);

  private static boolean onlyContainsExceptionOrSuperTypes(
      Iterable<? extends ReferenceType> ts) {
    for (ReferenceType t : ts) {
      if (!EXCEPTION_AND_SUPERS.contains(t.typeSpecification)) {
        return false;
      }
    }
    return true;
  }
}