package com.mikesamuel.cil.ast.meta;

import java.util.BitSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.logging.Logger;

import javax.annotation.Nullable;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.mikesamuel.cil.ast.AssignmentNode;
import com.mikesamuel.cil.ast.AssignmentOperatorNode;
import com.mikesamuel.cil.ast.FloatingPointTypeNode;
import com.mikesamuel.cil.ast.IntegralTypeNode;
import com.mikesamuel.cil.ast.NumericTypeNode;
import com.mikesamuel.cil.ast.PrimitiveTypeNode;
import com.mikesamuel.cil.ast.meta.TypeSpecification.TypeBinding;
import com.mikesamuel.cil.ast.meta.TypeSpecification.Variance;
import com.mikesamuel.cil.parser.SourcePosition;

/**
 * Represents a Java type.
 */
@SuppressWarnings("synthetic-access")
public abstract class StaticType {
  /** The specification for this type. */
  public final TypeSpecification typeSpecification;

  private StaticType(TypeSpecification spec) {
    this.typeSpecification = spec;
  }

  @Override
  public abstract String toString();

  /**
   * A JVM descriptor fragment.
   * @see <a href="https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-4.html#jvms-4.3.3">JVM spec 4.3.3</a>
   */
  public abstract String toDescriptor();

  /**
   * A type that does not use any generic elements.
   */
  public abstract StaticType toErasedType();

  @Override
  public final boolean equals(Object o) {
    return o instanceof StaticType
        && this.typeSpecification.equals(((StaticType) o).typeSpecification);
  }

  @Override
  public final int hashCode() {
    return typeSpecification.hashCode();
  }

  /**
   * The operation needed to convert from an output of type t to an input of
   * this type.
   */
  public abstract Cast assignableFrom(StaticType t);

  /**
   * Extracts a primitive type from a node.
   */
  public static PrimitiveType fromParseTree(PrimitiveTypeNode pt) {
    switch (pt.getVariant()) {
      case AnnotationBoolean:
        return T_BOOLEAN;
      case AnnotationNumericType:
        NumericTypeNode ntn = pt.firstChildWithType(NumericTypeNode.class);
        if (ntn == null) {
          throw new IllegalArgumentException("Missing numeric type");
        }
        switch (ntn.getVariant()) {
          case FloatingPointType:
            FloatingPointTypeNode ftn = ntn.firstChildWithType(
                FloatingPointTypeNode.class);
            if (ftn == null) {
              throw new IllegalArgumentException("Missing float type");
            }
            switch (ftn.getVariant()) {
              case Double:
                return T_DOUBLE;
              case Float:
                return T_FLOAT;
            }
            throw new AssertionError(ftn.getVariant());
          case IntegralType:
            IntegralTypeNode itn = pt.firstChildWithType(
                IntegralTypeNode.class);
            switch (itn.getVariant()) {
              case Byte:
                return T_BYTE;
              case Char:
                return T_CHAR;
              case Int:
                return T_INT;
              case Long:
                return T_LONG;
              case Short:
                return T_SHORT;
            }
            throw new AssertionError(itn.getVariant());
        }
        throw new AssertionError(ntn.getValue());
    }
    throw new AssertionError(pt.getValue());
  }


  /**
   * Type relationships.
   */
  public enum Cast {
    /** Types are incompatible. */
    DISJOINT,
    /** Types are equivalent. */
    SAME,
    /**
     * For example, a cast from {@code int} to {@code short} that can lead to
     * loss of precision.
     * Conversions between {@code short} and {@code char} are considered lossy
     * because of reinterpetation of the MSB.
     */
    CONVERTING_LOSSY(true),
    /** For example, a cast from {@code short} to {@code int}. */
    CONVERTING_LOSSLESS,
    /** For example, a cast from {@code String} to {@code Object}. */
    CONFIRM_SAFE,
    /**
     * For example, a cast from {@code Object} to {@code String}
     * that can fail at runtime.
     */
    CONFIRM_CHECKED(true),
    /**
     * For example, a cast from {@code List} to {@code List<String>}
     * that is generic-type unsound.
     */
    CONFIRM_UNCHECKED,
    /**
     * For example, a conversion from {@code int} to {@code /java/lang/Integer}.
     */
    BOX,
    /**
     * For example, a conversion from {@code /java/lang/Integer} to {@code int}.
     */
    UNBOX,
    ;

    /**
     * Whether the type conversion can be done implicitly.
     * There are some wrinkles to this.
     * Specifically in an {@link AssignmentNode} where the
     * {@link AssignmentOperatorNode} is not {@code =},
     * {@link #CONVERTING_LOSSY} operations can occur implicitly.
     */
    public final boolean explicit;

    Cast() {
      this(false);
    }

    Cast(boolean explicit) {
      this.explicit = explicit;
    }
  }

  /** Base type for primitive types. */
  public static abstract class PrimitiveType extends StaticType {
    /** Keyword that specifies this type. */
    public final String name;
    /** The wrapper class. */
    public final @Nullable Name wrapperType;
    final String descriptorFragment;

    private PrimitiveType(
        String name, @Nullable Name wrapperType, @Nullable Name specType,
        String descriptorFragment) {
      super(new TypeSpecification(
          specType != null
          ? specType
          : wrapperType.child("TYPE", Name.Type.FIELD)));
      this.name = name;
      this.wrapperType = wrapperType;
      this.descriptorFragment = descriptorFragment;
    }

    @Override
    public PrimitiveType toErasedType() {
      return this;
    }

    @Override
    public String toDescriptor() {
      return descriptorFragment;
    }
  }

  /**
   * A numeric primitive type.
   */
  public static final class NumericType extends PrimitiveType {
    /** True iff a floating point type. */
    public final boolean isFloaty;
    /** sizeof this type. */
    public final int byteWidth;
    /** True iff the type is signed. */
    public final boolean isSigned;

    private NumericType(
        String name, boolean isFloaty, int byteWidth,
        boolean isSigned, Name wrapperType,
        String descriptorFragment) {
      super(name, wrapperType, null, descriptorFragment);
      this.isFloaty = isFloaty;
      this.byteWidth = byteWidth;
      this.isSigned = isSigned;
    }

    @Override
    public String toString() {
      return name;
    }

    @Override
    public Cast assignableFrom(StaticType t) {
      if (t == ERROR_TYPE) {
        return Cast.UNBOX;
      }
      if (t instanceof NumericType) {
        NumericType nt = (NumericType) t;
        if (this.name.equals(nt.name)) {
          return Cast.SAME;
        }
        if (this.isFloaty == nt.isFloaty) {
          return this.byteWidth < nt.byteWidth
              || (this.byteWidth == nt.byteWidth
                  && this.isSigned != nt.isSigned)
              ? Cast.CONVERTING_LOSSY  // double -> float, long -> int
              : Cast.CONVERTING_LOSSLESS; // float -> double, int -> long
        } else {
          return this.isFloaty
              ? Cast.CONVERTING_LOSSLESS
              : Cast.CONVERTING_LOSSY;
        }
      }
      if (t instanceof TypePool.ClassOrInterfaceType
          && ((TypePool.ClassOrInterfaceType) t)
             .getBaseTypeName().equals(wrapperType)) {
        return Cast.UNBOX;
      }
      return Cast.DISJOINT;
    }
  }

  private static final Name JAVA =
      Name.DEFAULT_PACKAGE.child("java", Name.Type.PACKAGE);

  private static final Name JAVA_LANG =
      JAVA.child("lang", Name.Type.PACKAGE);

  static final Name JAVA_LANG_OBJECT =
      JAVA_LANG.child("Object", Name.Type.CLASS);

  private static final Name JAVA_LANG_CLONEABLE =
      JAVA_LANG.child("Cloneable", Name.Type.CLASS);

  /** A type returned when a type is malformed. */
  public static StaticType ERROR_TYPE = new StaticType(
      new TypeSpecification(
          Name.DEFAULT_PACKAGE
          .child("error", Name.Type.PACKAGE)
          .child("ErrorType", Name.Type.CLASS)
          .child("TYPE", Name.Type.FIELD))) {

    @Override
    public String toString() {
      return ERROR_TYPE.typeSpecification.toString();
    }

    @Override
    public Cast assignableFrom(StaticType t) {
      if (t == this) { return Cast.SAME; }
      if (t == T_VOID) { return Cast.DISJOINT; }
      if (t instanceof PrimitiveType) {
        return Cast.BOX;
      }
      if (t instanceof TypePool.ReferenceType) {
        return Cast.CONFIRM_UNCHECKED;
      }
      throw new AssertionError(t);
    }

    @Override
    public String toDescriptor() {
      return "X";
    }

    @Override
    public StaticType toErasedType() {
      return this;
    }
  };

  private static final class OneOffType extends PrimitiveType {

    private OneOffType(
        String name, @Nullable Name wrapperType, @Nullable Name specTypeName,
        String descriptorFragment) {
      super(name, wrapperType, specTypeName, descriptorFragment);
    }

    @Override
    public String toString() {
      return name;
    }

    @Override
    public Cast assignableFrom(StaticType t) {
      if (this.equals(t)) {
        return Cast.SAME;
      }
      if (t == ERROR_TYPE && !"void".equals(name)) {
        return Cast.UNBOX;
      }
      if (wrapperType != null && t instanceof TypePool.ClassOrInterfaceType
          && ((TypePool.ClassOrInterfaceType) t).getBaseTypeName()
              .equals(wrapperType)) {
        return Cast.UNBOX;
      }
      return Cast.DISJOINT;
    }
  }

  /** Type {@code byte} */
  public static final PrimitiveType T_VOID = new OneOffType(
      "void", null,
      JAVA_LANG.child("Void", Name.Type.CLASS).child("TYPE", Name.Type.FIELD),
      "V");

  /** Type {@code byte} */
  public static final PrimitiveType T_BOOLEAN = new OneOffType(
      "boolean", JAVA_LANG.child("Boolean", Name.Type.CLASS), null,
      "Z");

  /** Type {@code byte} */
  public static final NumericType T_BYTE = new NumericType(
      "byte", false, 1, true,
      JAVA_LANG.child("Byte", Name.Type.CLASS), "B");

  /** Type {@code short} */
  public static final NumericType T_SHORT = new NumericType(
      "short", false, 2, true,
      JAVA_LANG.child("Short", Name.Type.CLASS), "S");

  /** Type {@code char} */
  public static final NumericType T_CHAR = new NumericType(
      "char", false, 2, false,
      JAVA_LANG.child("Character", Name.Type.CLASS), "C");

  /** Type {@code int} */
  public static final NumericType T_INT = new NumericType(
      "int", false, 4, true,
      JAVA_LANG.child("Integer", Name.Type.CLASS), "I");

  /** Type {@code long} */
  public static final NumericType T_LONG = new NumericType(
      "long", false, 8, true,
      JAVA_LANG.child("Long", Name.Type.CLASS), "J");

  /** Type {@code float} */
  public static final NumericType T_FLOAT = new NumericType(
      "float", true, 4, true,
      JAVA_LANG.child("Float", Name.Type.CLASS), "F");

  /** Type {@code double} */
  public static final NumericType T_DOUBLE = new NumericType(
      "double", true, 8, true,
      JAVA_LANG.child("Double", Name.Type.CLASS), "D");

  private static final Set<Name> ARRAY_SUPER_TYPES = ImmutableSet.of(
      JAVA_LANG_OBJECT,
      JAVA_LANG_CLONEABLE,
      JAVA.child("io", Name.Type.PACKAGE).child("Serializable", Name.Type.CLASS)
      );

  /**
   * All the primitive types.
   */
  public static final ImmutableList<PrimitiveType> PRIMITIVE_TYPES =
      ImmutableList.of(
          T_BOOLEAN,
          T_BYTE,
          T_CHAR,
          T_SHORT,
          T_INT,
          T_FLOAT,
          T_LONG,
          T_DOUBLE);

  /**
   * Maintains a mapping of names to class or interface types so that we can
   * memoize sub-type checks.
   * <p>
   * This is roughly analogous to a class loader but we need not handle
   * parent loaders since {@code javac} assumes (until {@code -source 9}) that
   * all classes compiled together can address one another modulo visibility.
   */
  public static final class TypePool {
    /** Used to resolve names to type info. */
    public final TypeInfoResolver r;

    /** */
    public TypePool(TypeInfoResolver r) {
      this.r = r;
    }

    /**
     * Special type for the {@code null} value which is a reference bottom type.
     */
    public final ReferenceType T_NULL = new NullType();

    private final Map<TypeSpecification, StaticType> pool =
        Maps.newHashMap();
    {
      // Seed the pool so that type(spec) works.
      pool.put(T_VOID.typeSpecification,     T_VOID);
      pool.put(T_BOOLEAN.typeSpecification,  T_BOOLEAN);
      pool.put(T_BYTE.typeSpecification,     T_BYTE);
      pool.put(T_SHORT.typeSpecification,    T_SHORT);
      pool.put(T_CHAR.typeSpecification,     T_CHAR);
      pool.put(T_INT.typeSpecification,      T_INT);
      pool.put(T_LONG.typeSpecification,     T_LONG);
      pool.put(T_FLOAT.typeSpecification,    T_FLOAT);
      pool.put(T_DOUBLE.typeSpecification,   T_DOUBLE);
      pool.put(ERROR_TYPE.typeSpecification, ERROR_TYPE);
      pool.put(T_NULL.typeSpecification,     T_NULL);
    }

    /**
     * @param tspec specifices the type to return.
     * @param pos to use with any log messages.
     * @param logger receives error messages related to
     */
    public StaticType type(
        TypeSpecification tspec,
        @Nullable SourcePosition pos, @Nullable Logger logger) {
      return pool.computeIfAbsent(
          tspec.canon(r),
          new Function<TypeSpecification, StaticType>() {
            @Override
            public StaticType apply(TypeSpecification ts) {
              if (ts.nDims > 0) {
                TypeSpecification elementSpec = ts.withNDims(ts.nDims - 1);
                StaticType elType = type(elementSpec, pos, logger);
                if (ERROR_TYPE.equals(elType)) {
                  return ERROR_TYPE;
                }
                // We canonicalized the type specification above so we need
                // not do it here.
                return new ArrayType(ts, elType);
              }

              // Check that the type exists.
              if (!ts.typeName.type.isType) {
                // Primitive types should not reach here due to cache seeding
                // above, but malformed types like int<String> might.
                if (logger != null) {
                  logger.severe(
                      (pos != null ? pos + ": " : "") + " type name "
                      + ts.typeName + " does not specify a type");
                }
                if (!ts.bindings.isEmpty()) {
                  TypeSpecification withoutBindings = new TypeSpecification(
                      ts.typeName);
                  if (pool.containsKey(withoutBindings)) {
                    return pool.get(withoutBindings);
                  }
                }
                return ERROR_TYPE;
              }

              Optional<TypeInfo> tiOpt = r.resolve(ts.typeName);
              if (!tiOpt.isPresent()) {
                if (logger != null) {
                  logger.severe(
                      (pos != null ? pos + ": " : "") + " type name "
                      + ts.typeName + " does not specify a type");
                }
                return ERROR_TYPE;
              }
              TypeInfo ti = tiOpt.get();
              // Check type parameter count and bounds.
              boolean bindingsOk = true;
              if (!ts.bindings.isEmpty()) {
                // Not a raw type
                if (ts.bindings.size() != ti.parameters.size()) {
                  if (logger != null) {
                    logger.severe(
                        (pos != null ? pos + ": " : "")
                        + " type " + ts
                        + " has the wrong number of type parameters");
                  }
                  bindingsOk = false;
                } else {
                  // TODO: figure out how to check type bounds
                  // We may not actually need to do this since we allow
                  // common passes latitude on inputs that are not compiling
                  // java programs.
                  // This is also complicated by recursive type specifications.
                }
              }
              if (!bindingsOk) {
                TypeSpecification rawSpec = new TypeSpecification(ts.typeName);
                return type(rawSpec, pos, logger);
              }

              return new ClassOrInterfaceType(ts, ti);
            }
          });
    }

    private static final boolean DEBUG_LUB = false;

    /** https://docs.oracle.com/javase/specs/jls/se8/html/jls-4.html#jls-4.10.4 */
    public ReferenceType leastUpperBound(
        Iterable<? extends ReferenceType> typesIterable) {
      return leastUpperBound(typesIterable, Sets.newLinkedHashSet());
    }

    private ReferenceType leastUpperBound(
          Iterable<? extends ReferenceType> typesIterable,
          Set<ImmutableList<TypeSpecification>> infTypeDetect) {

      // Triple-slash comments below are direct quotes from
      // https://docs.oracle.com/javase/specs/jls/se8/html/jls-4.html#jls-4.10.4
      // "Least upper bound"

      /// lub(U1, ..., Uk), is determined as follows.
      ImmutableList<ReferenceType> u = ImmutableList.copyOf(
          Iterables.filter(typesIterable, new Predicate<ReferenceType>() {

            @Override
            public boolean apply(ReferenceType t) {
              if (T_NULL.equals(t)) { return false; }  // Null is the lub of ()
              Preconditions.checkState(t.getPool() == TypePool.this);
              return true;
            }
          }));

      int nTypes = u.size();
      if (nTypes == 0) {
        // The <null> type is a bottom reference type.
        return T_NULL;
      }
      if (nTypes == 1) {
        /// If k = 1, then the lub is the type itself: lub(U) = U.
        return u.get(0);
      }
      if (DEBUG_LUB) {
        System.err.println("u=" + u);
      }

      /// For each Ui (1 ≤ i ≤ k):
      ///   Let ST(Ui) be the set of supertypes of Ui.
      ///   Let EST(Ui), the set of erased supertypes of Ui, be:
      ///   EST(Ui) = { |W| | W in ST(Ui) } where |W| is the erasure of W.
      ImmutableList<ImmutableSet<ReferenceType>> st;
      ImmutableList<ImmutableSet<ReferenceType>> est;
      {
        ImmutableList.Builder<ImmutableSet<ReferenceType>> stb =
            ImmutableList.builder();
        ImmutableList.Builder<ImmutableSet<ReferenceType>> estb =
            ImmutableList.builder();
        for (ReferenceType ui : u) {
          ImmutableSet<ReferenceType> sti = ui.getSuperTypes();
          stb.add(sti);

          ImmutableSet.Builder<ReferenceType> estib = ImmutableSet.builder();
          for (ReferenceType w : sti) {
            // The null type is not a super-type of any type.
            Preconditions.checkState(
                !(w instanceof NullType)
                && w.getPool() == TypePool.this);
            estib.add(w.toErasedType());
          }
          estb.add(estib.build());
        }
        st = stb.build();
        est = estb.build();
      }
      if (DEBUG_LUB) {
        System.err.println("st=" + st + "\nest=" + est);
      }

      /// Let EC, the erased candidate set for U1 ... Uk, be the intersection of
      /// all the sets EST(Ui) (1 ≤ i ≤ k).
      ImmutableList<ReferenceType> ec;
      {
        Set<ReferenceType> inter = Sets.newLinkedHashSet(est.get(0));
        for (int i = 1; i < nTypes; ++i) {
          inter.retainAll(est.get(i));
        }
        ec = ImmutableList.copyOf(inter);
      }
      if (DEBUG_LUB) {
        System.err.println("ec=" + ec);
      }

      // Let MEC, the minimal erased candidate set for U1 ... Uk, be:
      //   MEC = { V | V in EC, and for all W ≠ V in EC, it is not the case that
      //           W <: V }
      ImmutableList<ReferenceType> mec;
      {
        int nec = ec.size();
        BitSet minimal = new BitSet();
        minimal.set(0, nec);  // until proven otherwise
        i_loop:
        for (int i = 0; i < nec; ++i) {
          ReferenceType eci = ec.get(i);
          for (int j = 0; j < nec; ++j) {
            if (i == j || !minimal.get(j)) { continue; }
            ReferenceType ecj = ec.get(j);
            Cast c = eci.assignableFrom(ecj);
            switch (c) {
              case BOX:
              case UNBOX:
              case CONVERTING_LOSSLESS:
              case CONVERTING_LOSSY:
              case SAME:
                throw new AssertionError(eci);
              case DISJOINT:
              case CONFIRM_CHECKED:
                break;
              case CONFIRM_UNCHECKED:
              case CONFIRM_SAFE:
                minimal.clear(i);
                if (DEBUG_LUB) {
                  System.err.println(ecj + " excludes " + eci + " by " + c);
                }
                continue i_loop;
            }
          }
        }
        ImmutableList.Builder<ReferenceType> b = ImmutableList.builder();
        for (int i = minimal.nextSetBit(0);
             i >= 0;
             i = minimal.nextSetBit(i + 1)) {
          b.add(ec.get(i));
        }
        mec = b.build();
      }
      if (DEBUG_LUB) {
        System.err.println("mec=" + mec);
      }

      /// For any element G of MEC that is a generic type:
      ///  Let the "relevant" parameterizations of G, Relevant(G), be:
      ///  Relevant(G) = { V | 1 ≤ i ≤ k: V in ST(Ui) and V = G<...> }
      ImmutableMultimap<ClassOrInterfaceType, ClassOrInterfaceType> relevant;
      {
        ImmutableMultimap.Builder<ClassOrInterfaceType, ClassOrInterfaceType> b
            = ImmutableMultimap.builder();
        for (ReferenceType meci : mec) {
          // AFAICT, array types are not "generic."
          if (!(meci instanceof ClassOrInterfaceType)) {
            continue;
          }
          ClassOrInterfaceType g = (ClassOrInterfaceType) meci;
          // TODO: Does "is generic" here, and in the definition of Best below
          // apply to raw types?
          if (g.info.parameters.isEmpty()) {
            continue;
          }
          for (int i = 0; i < nTypes; ++i) {
            for (ReferenceType v : st.get(i)) {
              if (v instanceof ClassOrInterfaceType
                  && g.typeSpecification.typeName.equals(
                      v.typeSpecification.typeName)) {
                b.put(g, (ClassOrInterfaceType) v);
              }
            }
          }
        }
        relevant = b.build();
      }
      if (DEBUG_LUB) {
        System.err.println("relevant=" + relevant);
      }

      /// Let the "candidate" parameterization of G, Candidate(G), be the most
      /// specific parameterization of the generic type G that contains all the
      /// relevant parameterizations of G:
      ///    Candidate(G) = lcp(Relevant(G))
      ImmutableMap<ClassOrInterfaceType, ClassOrInterfaceType> candidate;
      {
        ImmutableMap.Builder<ClassOrInterfaceType, ClassOrInterfaceType> b =
            ImmutableMap.builder();
        for (ClassOrInterfaceType g : relevant.keySet()) {
          b.put(g, leastContainingInvocation(relevant.get(g), infTypeDetect));
        }
        candidate = b.build();
      }
      if (DEBUG_LUB) {
        System.err.println("candidate=" + candidate);
      }

      /// Let lub(U1 ... Uk) be:
      ///  Best(W1) & ... & Best(Wr)
      ///  where Wi (1 ≤ i ≤ r) are the elements of MEC, the minimal erased
      ///  candidate set of U1 ... Uk;
      ///  and where, if any of these elements are generic, we use the candidate
      ///  parameterization (so as to recover type arguments):
      ///  Best(X) = Candidate(X) if X is generic; X otherwise.
      ImmutableList.Builder<ReferenceType> bestIntersectionElements =
          ImmutableList.builder();
      for (ReferenceType meci : mec) {
        ReferenceType best = meci;
        if (meci instanceof ClassOrInterfaceType) {
          ClassOrInterfaceType ci = (ClassOrInterfaceType) meci;
          if (!ci.info.parameters.isEmpty()) {
            best = candidate.get(ci);
          }
        }
        bestIntersectionElements.add(best);
      }
      // TODO: implement intersection types
      ImmutableList<ReferenceType> best = bestIntersectionElements.build();
      if (DEBUG_LUB) {
        System.err.println("best=" + best);
      }
      return best.get(0);
    }

    ClassOrInterfaceType leastContainingInvocation(
        Iterable<? extends ClassOrInterfaceType> types,
        Set<ImmutableList<TypeSpecification>> infTypeDetect) {
      /// lcp(), the least containing invocation, is:
      ///   lcp(S) = lcp(e1, ..., en) where ei (1 ≤ i ≤ n) in S
      ///   lcp(e1, ..., en) = lcp(lcp(e1, e2), e3, ..., en)
      ///   lcp(G<X1, ..., Xn>, G<Y1, ..., Yn>) =
      ///       G<lcta(X1, Y1), ..., lcta(Xn, Yn)>
      ///   lcp(G<X1, ..., Xn>) = G<lcta(X1), ..., lcta(Xn)>
      ImmutableList<ClassOrInterfaceType> g = ImmutableList.copyOf(
          ImmutableSet.copyOf(types));

      // This is always called with a set of types with the same erasure, so
      // there is only one G for a given call.
      Preconditions.checkArgument(!g.isEmpty());

      // The above does a pairwise reduction from the left.
      ClassOrInterfaceType t = g.get(0);
      int arity = t.typeParameterBindings.size();
      for (int i = 1, n = g.size(); i < n; ++i) {
        ClassOrInterfaceType e1 = t;
        ClassOrInterfaceType e2 = g.get(i);

        Preconditions.checkArgument(
            e1.info.canonName.equals(e2.info.canonName));
        Preconditions.checkArgument(e1.typeParameterBindings.size() == arity);
        Preconditions.checkArgument(e2.typeParameterBindings.size() == arity);
        ImmutableList.Builder<TypeSpecification.TypeBinding> bindings =
            ImmutableList.builder();
        for (int j = 0; j < arity; ++j) {
          bindings.add(lcta(
              e1.typeParameterBindings.get(j),
              e2.typeParameterBindings.get(j),
              infTypeDetect));
        }
        t = (ClassOrInterfaceType) type(
            new TypeSpecification(e1.info.canonName, bindings.build()),
            null, null);
      }

      // Last case.
      ImmutableList.Builder<TypeSpecification.TypeBinding> bindings =
          ImmutableList.builder();
      for (int j = 0; j < arity; ++j) {
        bindings.add(lcta(t.typeParameterBindings.get(j)));
      }
      return (ClassOrInterfaceType) type(
          new TypeSpecification(t.info.canonName, bindings.build()),
          null, null);
    }

    private TypeBinding lcta(TypeBinding typeBinding) {
      /// lcta(U) = ? if U's upper bound is Object,
      TypeSpecification upperBound = upperBound(typeBinding);
      if (upperBound == null
          || JAVA_LANG_OBJECT.equals(upperBound.typeName)) {
        return TypeBinding.EXTENDS_OBJECT;
      }
      /// otherwise ? extends lub(U,Object)
      // AFAICT, when U is an interface type this gives you Object & U
      // and when U is a class type it gives you U.
      return typeBinding;
    }

    private TypeBinding lcta(
        TypeBinding u, TypeBinding v,
        /// It is possible that the lub() function yields an infinite type. This
        /// is permissible, and a compiler for the Java programming language
        /// must recognize such situations and represent them appropriately
        /// using cyclic data structures.

        // We could introduce a nameless type to represent a back-cycle which
        // would allow us to treat the lub(Boolean, Number) as
        //     Serializable & #1:Comparable<? extends #1>.
        // HACK: We treat it as (Serializable & Comparable<?>)
        Set<ImmutableList<TypeSpecification>> infTypeDetect) {
      switch (u.variance) {
        case INVARIANT:
          switch (v.variance) {
            case INVARIANT:
              /// lcta(U, V) = U if U = V, otherwise ? extends lub(U, V)
              if (v.variance == Variance.INVARIANT && u.equals(v)) { return u; }
              //$FALL-THROUGH$
            case EXTENDS:
              /// lcta(U, ? extends V) = ? extends lub(U, V)
              return new TypeBinding(
                  Variance.EXTENDS,
                  lub(u.typeSpec, v.typeSpec, infTypeDetect));
            case SUPER:
              /// lcta(U, ? super V) = ? super glb(U, V)
              return new TypeBinding(
                  Variance.SUPER,
                  glb(u.typeSpec, v.typeSpec));
          }
          throw new AssertionError(v);
        case EXTENDS:
          switch (v.variance) {
            case INVARIANT:
              return lcta(v, u, infTypeDetect);
            case EXTENDS:
              /// lcta(? extends U, ? extends V) = ? extends lub(U, V)
              return new TypeBinding(
                  Variance.EXTENDS,
                  lub(u.typeSpec, v.typeSpec, infTypeDetect));
            case SUPER:
              /// lcta(? extends U, ? super V) = U if U = V, otherwise ?
              if (u.typeSpec.equals(v.typeSpec)) {
                return u;
              }
              return TypeBinding.WILDCARD;
          }
          throw new AssertionError(v);
        case SUPER:
          switch (v.variance) {
            case INVARIANT:
            case EXTENDS:
              return lcta(v, u, infTypeDetect);
            case SUPER:
              /// lcta(? super U, ? super V) = ? super glb(U, V)
              return new TypeBinding(
                  Variance.SUPER,
                  glb(u.typeSpec, v.typeSpec));
          }
          throw new AssertionError(v);
      }
      throw new AssertionError(u);
    }

    private TypeSpecification lub(
        TypeSpecification u, TypeSpecification v,
        Set<ImmutableList<TypeSpecification>> infTypeDetect) {
      StaticType ut = type(u, null, null);
      StaticType vt = type(v, null, null);
      if (ut instanceof ReferenceType && vt instanceof ReferenceType) {
        ImmutableList<TypeSpecification> both = ImmutableList.of(u, v);
        if (infTypeDetect.add(both)) {
          TypeSpecification lub = leastUpperBound(
              ImmutableSet.of(
                  (ReferenceType) ut, (ReferenceType) vt), infTypeDetect)
              .typeSpecification;
          infTypeDetect.remove(both);
          return lub;
        } else {
          // HACK: This is not correct since we want to avoid the complexity of
          // infinite types.  See note on infTypeDetect above.
          return TypeSpecification.JAVA_LANG_OBJECT;
        }
      }
      return ERROR_TYPE.typeSpecification;
    }

    private TypeSpecification glb(
        TypeSpecification u, TypeSpecification v) {
      /// glb(V1,...,Vm) is defined as V1 & ... & Vm.
      // TODO: We need to be able to represent type intersections.
      if (u.equals(v)) { return u; }
      if (u.typeName.equals(v.typeName)) {
        return u.withBindings(ImmutableList.of());
      }
      return T_NULL.typeSpecification;
    }

    private TypeSpecification upperBound(TypeBinding b) {
      if (b.variance == Variance.SUPER) {
        return TypeSpecification.JAVA_LANG_OBJECT;
      }
      TypeSpecification upperBound = b.typeSpec;
      while (upperBound != null
             && upperBound.typeName.type == Name.Type.TYPE_PARAMETER) {
        StaticType t = type(upperBound, null, null);
        if (!(t instanceof ClassOrInterfaceType)) {
          // type bounds can't extend array types or <null>
          // due to a TypeInfoResolver that lacks a mapping for a name.
          Preconditions.checkState(ERROR_TYPE.equals(t));
          return TypeSpecification.JAVA_LANG_OBJECT;
        }
        ClassOrInterfaceType rt = (ClassOrInterfaceType) t;
        upperBound = rt.typeSpecification;
      }
      return upperBound != null
          ? upperBound : TypeSpecification.JAVA_LANG_OBJECT;
    }


    /** Base type for primitive types. */
    public abstract class ReferenceType extends StaticType {
      private ReferenceType(TypeSpecification spec) {
        super(spec);
      }

      /**
       * The type pool associated with this.
       */
      public TypePool getPool() {
        return TypePool.this;
      }

      @Override
      public abstract ReferenceType toErasedType();

      private ImmutableSet<ReferenceType> superTypes;

      final ImmutableSet<ReferenceType> getSuperTypes() {
        if (superTypes == null) {
          superTypes = Preconditions.checkNotNull(buildSuperTypeSet());
        }
        return superTypes;
      }

      abstract ImmutableSet<ReferenceType> buildSuperTypeSet();
    }


    private final class NullType extends ReferenceType {

      private NullType() {
        super(new TypeSpecification(
            // Outside normal type namespace since null is a keyword.
            Name.DEFAULT_PACKAGE.child("null", Name.Type.CLASS)));
      }

      @Override
      public String toString() {
        return "<null>";
      }

      @Override
      public String toDescriptor() {
        return ERROR_TYPE.toDescriptor();
      }

      @Override
      public Cast assignableFrom(StaticType t) {
        if (t == this) {
          return Cast.SAME;
        }
        if (ERROR_TYPE.equals(t)) {
          return Cast.CONFIRM_UNCHECKED;
        }
        if (t instanceof ReferenceType) {
          return Cast.CONFIRM_CHECKED;
        }
        // The <null> type may be assignable to Integer but it does not
        // participate in unboxing.
        return Cast.DISJOINT;
      }

      @Override
      public ReferenceType toErasedType() {
        return this;
      }

      @Override
      ImmutableSet<ReferenceType> buildSuperTypeSet() {
        throw new AssertionError("Cannot enumerate super-types of <null>");
      }
    }


    /**
     * A class or interface type.
     */
    public final class ClassOrInterfaceType extends ReferenceType {
      /**
       * The type info for the type.
       */
      public final TypeInfo info;
      /**
       * Any type parameter bindings that correspond to info.parameters.
       */
      public final ImmutableList<TypeBinding> typeParameterBindings;

      private ImmutableMap<Name, ClassOrInterfaceType> superTypesTransitive;

      private ClassOrInterfaceType(
          TypeSpecification spec,
          TypeInfo info) {
        super(spec);
        this.info = info;
        this.typeParameterBindings = spec.bindings;
      }

      /** The name of the raw type. */
      public Name getBaseTypeName() {
        return info.canonName;
      }

      @Override
      public ReferenceType toErasedType() {
        TypeSpecification erasedSpec;
        if (info.canonName.type == Name.Type.CLASS) {
          if (this.typeParameterBindings.isEmpty()) {
            return this;
          } else {
            erasedSpec = typeSpecification.withBindings(ImmutableList.of());
          }
        } else {
          Preconditions.checkState(
              Name.Type.TYPE_PARAMETER == info.canonName.type);
          erasedSpec = info.superType.or(TypeSpecification.JAVA_LANG_OBJECT)
              .withBindings(ImmutableList.of());
        }
        // If the type info resolver found the type info for this, then it
        // should be able to find the type info for the erased type.
        return (ReferenceType) type(erasedSpec, null, null).toErasedType();
      }

      @Override
      public String toString() {
        if (typeParameterBindings.isEmpty()) {
          return info.canonName.toString();
        }
        StringBuilder sb = new StringBuilder();
        sb.append(info.canonName);
        sb.append('<');
        for (int i = 0, n = typeParameterBindings.size(); i < n; ++i) {
          if (i != 0) {
            sb.append(',');
          }
          sb.append(typeParameterBindings.get(i));
        }
        sb.append('>');
        return sb.toString();
      }

      Map<Name, ClassOrInterfaceType> getSuperTypesTransitive() {
        if (this.superTypesTransitive == null) {
          Map<Name, ClassOrInterfaceType> m = new LinkedHashMap<>();
          m.put(info.canonName, this);

          for (TypeSpecification ts : r.superTypesOf(this.typeSpecification)) {
            if (!m.containsKey(ts.typeName)) {
              StaticType superType = TypePool.this.type(ts, null, null);
              if (superType instanceof ClassOrInterfaceType) {  // Not error
                ClassOrInterfaceType superCT = (ClassOrInterfaceType) superType;
                m.put(ts.typeName, superCT);
                // TODO: Fails to halt if dep cycles.
                for (Map.Entry<Name, ClassOrInterfaceType> e :
                  superCT.getSuperTypesTransitive().entrySet()) {
                  m.putIfAbsent(e.getKey(), e.getValue());
                }
              }
            }
          }
          this.superTypesTransitive = ImmutableMap.copyOf(m);
        }
        return this.superTypesTransitive;
      }

      @Override
      public Cast assignableFrom(StaticType t) {
        if (ERROR_TYPE.equals(t)) {
          return Cast.CONFIRM_UNCHECKED;
        }
        if (t instanceof NullType) {
          return Cast.CONFIRM_SAFE;
        }
        if (t instanceof ClassOrInterfaceType) {
          ClassOrInterfaceType ct = (ClassOrInterfaceType) t;
          if (info.canonName.equals(ct.info.canonName)) {
            // This single-class-loader-esque assumption is valid for all code
            // compiled together.
            if (this.typeParameterBindings.equals(ct.typeParameterBindings)) {
              return Cast.SAME;
            } else if (this.typeParameterBindings.isEmpty()) {
              // Optimistic raw type assumptions.
              return Cast.CONFIRM_SAFE;
            } else if (ct.typeParameterBindings.isEmpty()) {
              return Cast.CONFIRM_UNCHECKED;
            } else {
              int n = typeParameterBindings.size();
              Preconditions.checkState(n == ct.typeParameterBindings.size());

              // Optimistically assume this, and look for reasons to downgrade
              // to disjoint or unchecked.
              Cast result = Cast.CONFIRM_SAFE;

              // Check parameters pairwise taking variance into account.
              for (int i = 0; i < n; ++i) {
                TypeBinding left = typeParameterBindings.get(i);
                TypeBinding right = ct.typeParameterBindings.get(i);
                if (left.variance == Variance.INVARIANT
                    && right.variance == Variance.INVARIANT) {
                  if (!left.equals(right)) {
                    return Cast.DISJOINT;
                  }
                  continue;
                }

                StaticType leftType = type(left.typeSpec, null, null);
                StaticType rightType = type(right.typeSpec, null, null);

                Cast pc = leftType.assignableFrom(rightType);
                switch (pc) {
                  case DISJOINT:
                    return Cast.DISJOINT;
                  case SAME:
                    if (left.variance != right.variance
                        && right.variance != Variance.INVARIANT) {
                      result = Cast.CONFIRM_UNCHECKED;
                    }
                    continue;
                  case CONFIRM_SAFE:
                    if (right.variance == Variance.SUPER
                        // Special case
                        //   X<? super Foo> y = ...;
                        //   X<? extends Object> x = y;
                        // which is safe because all parameter bindings are
                        // reference types and Object is a top for reference
                        // types.
                        && (left.variance != Variance.EXTENDS
                            || !JAVA_LANG_OBJECT.equals(
                                left.typeSpec.typeName))) {
                      result = Cast.CONFIRM_UNCHECKED;
                    } else if (left.variance != Variance.EXTENDS) {
                      return Cast.DISJOINT;
                    }
                    break;
                  case CONFIRM_CHECKED:
                    if (right.variance == Variance.EXTENDS) {
                      result = Cast.CONFIRM_UNCHECKED;
                    } else if (left.variance != Variance.SUPER) {
                      return Cast.DISJOINT;
                    }
                    break;
                  case CONFIRM_UNCHECKED:
                    result = Cast.CONFIRM_UNCHECKED;
                    break;
                  case BOX:
                  case CONVERTING_LOSSLESS:
                  case CONVERTING_LOSSY:
                  case UNBOX:
                    throw new AssertionError(
                        left + " =~= " + right + " => " + pc);
                }
              }
              return result;
            }
          }
          Map<Name, ClassOrInterfaceType> ctSuperTypes =
              ct.getSuperTypesTransitive();
          ClassOrInterfaceType ctCommonSuperType = ctSuperTypes.get(
              info.canonName);
          if (ctCommonSuperType != null) {
            Cast castToCtCommonSuper = this.assignableFrom(ctCommonSuperType);
            switch (castToCtCommonSuper) {
              case SAME:
                return Cast.CONFIRM_SAFE;
              case CONFIRM_SAFE:
              case CONFIRM_CHECKED:  // Is this right?
              case CONFIRM_UNCHECKED:
                return castToCtCommonSuper;
              case CONVERTING_LOSSLESS:
              case CONVERTING_LOSSY:
              case DISJOINT:
              case BOX:
              case UNBOX:
                throw new AssertionError(castToCtCommonSuper);
            }
          }

          Map<Name, ClassOrInterfaceType> superTypes =
              getSuperTypesTransitive();
          ClassOrInterfaceType commonSuperType = superTypes.get(
              ct.info.canonName);
          if (commonSuperType != null) {
            Cast castToCommonSuper = commonSuperType.assignableFrom(ct);
            switch (castToCommonSuper) {
              case SAME:
              case CONFIRM_UNCHECKED:
                return Cast.CONFIRM_CHECKED;
              case CONFIRM_SAFE:
              case CONFIRM_CHECKED:  // Is this right?
                return castToCommonSuper;
              case CONVERTING_LOSSLESS:
              case CONVERTING_LOSSY:
              case DISJOINT:
              case BOX:
              case UNBOX:
                throw new AssertionError(castToCommonSuper);
            }
          }

          // TODO: Do we need to work on the case where this is a type parameter
          // and use its upper bound above?
          return Cast.DISJOINT;
        }
        if (t instanceof PrimitiveType) {
          PrimitiveType pt = (PrimitiveType) t;
          if (pt.wrapperType == null) {
            return Cast.DISJOINT;
          }
          if (this.info.canonName.equals(pt.wrapperType)) {
            return Cast.BOX;
          }
          StaticType wrapperType = type(
              new TypeSpecification(pt.wrapperType), null, null);
          Cast c = assignableFrom(wrapperType);
          switch (c) {
            case BOX:
            case UNBOX:
            case CONVERTING_LOSSLESS:
            case CONVERTING_LOSSY:
              throw new AssertionError();
            case CONFIRM_CHECKED:
          case CONFIRM_UNCHECKED:
            return Cast.DISJOINT;
          case CONFIRM_SAFE:
            // E.g.   Number n = intExpression;
            return Cast.BOX;
          case DISJOINT:
            return Cast.DISJOINT;
          case SAME:
            return Cast.BOX;
        }
        throw new AssertionError(c);
      }
      if (t instanceof ArrayType) {
        if (ARRAY_SUPER_TYPES.contains(info.canonName)) {
          return Cast.CONFIRM_SAFE;
        }
        return Cast.DISJOINT;
      }
      throw new IllegalArgumentException(t.getClass().getName());
    }

    @Override
    public String toDescriptor() {
      if (info.canonName.type == Name.Type.TYPE_PARAMETER) {
        Preconditions.checkState(info.superType.isPresent());
        // Mimic type-erasure by using the upper-type bound.
        return type(info.superType.get(), null, null).toDescriptor();
      }
      StringBuilder sb = new StringBuilder();
      sb.append('L');
      toDescriptor(this.info.canonName, sb);
      return sb.append(';').toString();
    }

    private Name.Type toDescriptor(Name nm, StringBuilder sb) {
      if (nm == Name.DEFAULT_PACKAGE) {
        return null;
      }
      Name.Type parentType = toDescriptor(nm.parent, sb);
      switch (nm.type) {
        case CLASS:
          if (parentType == Name.Type.CLASS) {
            sb.append('$');
          } else if (parentType == Name.Type.PACKAGE) {
            sb.append('/');
          }
          sb.append(nm.identifier);
          return Name.Type.CLASS;
        case PACKAGE:
          if (parentType != null) {
            sb.append('/');
          }
          sb.append(nm.identifier);
          return Name.Type.PACKAGE;
        default:
          return parentType;
      }
    }

    @Override
    ImmutableSet<ReferenceType> buildSuperTypeSet() {
      return ImmutableSet.copyOf(getSuperTypesTransitive().values());
    }
  }

    /**
     * Type for an array type.
     */
    public final class ArrayType extends ReferenceType {
      /** The type of the elements of arrays with this static type. */
      public final StaticType elementType;
      /**
       * The type of the elements of the innermost array.
       * This will never be itself an array type.
       */
      public final StaticType baseElementType;
      /**
       * One greater than the dimensionality of the element type or 1 if the
       * element type is not itself an array type.
       */
      public final int dimensionality;

      private ArrayType(TypeSpecification spec, StaticType elementType) {
        super(spec);
        this.elementType = elementType;
        if (elementType instanceof ArrayType) {
          ArrayType at = (ArrayType) elementType;
          this.dimensionality = at.dimensionality + 1;
          this.baseElementType = at.baseElementType;
        } else {
          this.dimensionality = 1;
          this.baseElementType = elementType;
        }
      }

      @Override
      public ReferenceType toErasedType() {
        StaticType erasedBaseType = baseElementType.toErasedType();
        int nDims = this.typeSpecification.nDims
            - baseElementType.typeSpecification.nDims
            + erasedBaseType.typeSpecification.nDims;
        // All Array types are reference types, and the type info resolver has
        // already resolved the base type's type name to a type other than
        // error type.
        return (ReferenceType) type(new TypeSpecification(
            erasedBaseType.typeSpecification.typeName, nDims), null, null);
      }

      @Override
      public String toDescriptor() {
        return "[" + elementType.toDescriptor();
      }

      @Override
      public String toString() {
        return elementType + "[]";
      }

      @Override
      public Cast assignableFrom(StaticType t) {
        if (ERROR_TYPE.equals(t)) {
          return Cast.CONFIRM_UNCHECKED;
        }
        if (t instanceof NullType) {
          return Cast.CONFIRM_SAFE;
        }
        if (t instanceof PrimitiveType) {
          return Cast.DISJOINT;
        }
        if (t instanceof ClassOrInterfaceType) {
          ClassOrInterfaceType cit = (ClassOrInterfaceType) t;
          if (ARRAY_SUPER_TYPES.contains(cit.info.canonName)) {
            return Cast.CONFIRM_CHECKED;
          }
          return Cast.DISJOINT;
        }
        Preconditions.checkArgument(t instanceof ArrayType);
        ArrayType at = (ArrayType) t;
        int dimDelta = this.dimensionality - at.dimensionality;
        if (dimDelta == 0) {
          Cast ec = elementType.assignableFrom(at.elementType);
          switch (ec) {
            case SAME:
            case DISJOINT:
            case CONFIRM_CHECKED:
            case CONFIRM_SAFE:
            case CONFIRM_UNCHECKED:
              return ec;
            case BOX:
            case UNBOX:
            case CONVERTING_LOSSLESS:
            case CONVERTING_LOSSY:
              return Cast.DISJOINT;
          }
          throw new AssertionError(ec);
        } else {
          StaticType a = this;
          StaticType b = at;
          while (a instanceof ArrayType && b instanceof ArrayType) {
            a = ((ArrayType) a).elementType;
            b = ((ArrayType) b).elementType;
          }
          return a.assignableFrom(b);
        }
      }

      @Override
      ImmutableSet<ReferenceType> buildSuperTypeSet() {
        ImmutableSet.Builder<ReferenceType> supertypes = ImmutableSet.builder();
        for (Name superTypeName : ARRAY_SUPER_TYPES) {
          supertypes.add(
              (ReferenceType) type(
                  new TypeSpecification(superTypeName),
                  null, null));
        }
        if (elementType instanceof ReferenceType) {
          for (ReferenceType elementSuperType : getSuperTypes()) {
            if (elementType.equals(elementSuperType)) {
              continue;
            }
            supertypes.add(
                (ReferenceType) type(
                    elementSuperType.typeSpecification.arrayOf(),
                    null, null));
          }
        }
        supertypes.add(this);
        return supertypes.build();
      }
    }
  }
}
