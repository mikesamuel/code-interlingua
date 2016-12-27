package com.mikesamuel.cil.ast.meta;

import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.logging.Logger;

import javax.annotation.Nullable;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.mikesamuel.cil.ast.AssignmentNode;
import com.mikesamuel.cil.ast.AssignmentOperatorNode;
import com.mikesamuel.cil.ast.FloatingPointTypeNode;
import com.mikesamuel.cil.ast.IntegralTypeNode;
import com.mikesamuel.cil.ast.NumericTypeNode;
import com.mikesamuel.cil.ast.PrimitiveTypeNode;
import com.mikesamuel.cil.ast.meta.TypeSpecification.TypeBinding;
import com.mikesamuel.cil.parser.SourcePosition;

/**
 *
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

  @Override
  public abstract boolean equals(Object o);

  @Override
  public abstract int hashCode();

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
    final String name;
    final Name wrapperType;

    private PrimitiveType(
        String name, @Nullable Name wrapperType, @Nullable Name specType) {
      super(new TypeSpecification(
          specType != null
          ? specType
          : wrapperType.child("TYPE", Name.Type.FIELD)));
      this.name = name;
      this.wrapperType = wrapperType;
    }
  }

  /**
   * A numeric primitive type.
   */
  public static final class NumericType extends PrimitiveType {
    final boolean isFloaty;
    final int byteWidth;
    final boolean isSigned;

    private NumericType(
        String name, boolean isFloaty, int byteWidth,
        boolean isSigned, Name wrapperType) {
      super(name, wrapperType, null);
      this.isFloaty = isFloaty;
      this.byteWidth = byteWidth;
      this.isSigned = isSigned;
    }

    @Override
    public String toString() {
      return name;
    }

    @Override
    public boolean equals(Object o) {
      return o instanceof NumericType && name.equals(((NumericType) o).name);
    }

    @Override
    public int hashCode() {
      return name.hashCode();
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
    public boolean equals(Object o) {
      return o == this;
    }

    @Override
    public int hashCode() {
      return System.identityHashCode(this);
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

  };

  private static final class OneOffType extends PrimitiveType {

    private OneOffType(
        String name, @Nullable Name wrapperType, @Nullable Name specTypeName) {
      super(name, wrapperType, specTypeName);
    }

    @Override
    public String toString() {
      return name;
    }

    @Override
    public boolean equals(Object o) {
      return o instanceof OneOffType && name.equals(((OneOffType) o).name);
    }

    @Override
    public int hashCode() {
      return name.hashCode();
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
      JAVA_LANG.child("Void", Name.Type.CLASS).child("TYPE", Name.Type.FIELD));

  /** Type {@code byte} */
  public static final PrimitiveType T_BOOLEAN = new OneOffType(
      "boolean", JAVA_LANG.child("Boolean", Name.Type.CLASS), null);

  /** Type {@code byte} */
  public static final NumericType T_BYTE = new NumericType(
      "byte", false, 1, true,
      JAVA_LANG.child("Byte", Name.Type.CLASS));

  /** Type {@code short} */
  public static final NumericType T_SHORT = new NumericType(
      "short", false, 2, true,
      JAVA_LANG.child("Short", Name.Type.CLASS));

  /** Type {@code char} */
  public static final NumericType T_CHAR = new NumericType(
      "char", false, 2, false,
      JAVA_LANG.child("Character", Name.Type.CLASS));

  /** Type {@code int} */
  public static final NumericType T_INT = new NumericType(
      "int", false, 4, true,
      JAVA_LANG.child("Integer", Name.Type.CLASS));

  /** Type {@code long} */
  public static final NumericType T_LONG = new NumericType(
      "long", false, 8, true,
      JAVA_LANG.child("Long", Name.Type.CLASS));

  /** Type {@code float} */
  public static final NumericType T_FLOAT = new NumericType(
      "float", true, 4, true,
      JAVA_LANG.child("Float", Name.Type.CLASS));

  /** Type {@code double} */
  public static final NumericType T_DOUBLE = new NumericType(
      "double", true, 8, true,
      JAVA_LANG.child("Double", Name.Type.CLASS));

  private static final Set<Name> ARRAY_SUPER_TYPES = ImmutableSet.of(
      JAVA_LANG_OBJECT,
      JAVA_LANG_CLONEABLE,
      JAVA.child("io", Name.Type.PACKAGE).child("Serializable", Name.Type.CLASS)
      );

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
          tspec, new Function<TypeSpecification, StaticType>() {
            @Override
            public StaticType apply(TypeSpecification ts) {
              if (ts.nDims > 0) {
                TypeSpecification elementSpec = new TypeSpecification(
                    ts.typeName, ts.bindings, ts.nDims - 1);
                return new ArrayType(ts, type(elementSpec, pos, logger));
              }

              // Check that the type exists.
              if (!ts.typeName.type.isType) {
                // Primitive types should not reach here due to cache seeding
                // above, but malformed types like int<String> might.
                logger.severe(
                    (pos != null ? pos + ": " : "")
                    + " type name " + ts.typeName + " does not specify a type");
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
                logger.severe(
                    (pos != null ? pos + ": " : "")
                    + " type name " + ts.typeName + " does not specify a type");
                return ERROR_TYPE;
              }
              TypeInfo ti = tiOpt.get();
              // Check type parameter count and bounds.
              boolean bindingsOk = true;
              if (!ts.bindings.isEmpty()) {
                // Not a raw type
                if (ts.bindings.size() != ti.parameters.size()) {
                  logger.severe(
                      (pos != null ? pos + ": " : "")
                      + " type " + ts
                      + " has the wrong number of type parameters");
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

          Optional<TypeSpecification> superTypeOpt = info.superType;
          if (!superTypeOpt.isPresent()
              && Modifier.isInterface(info.modifiers)) {
            // Interfaces implicitly have Object as the super type.
            superTypeOpt = Optional.of(TypeSpecification.JAVA_LANG_OBJECT);
          }

          for (TypeSpecification ts :
            Iterables.concat(superTypeOpt.asSet(), info.interfaces)) {
            if (!typeParameterBindings.isEmpty()) {  // Not a raw type
              ts = ts.subst(info.parameters, this.typeParameterBindings);
            }
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
        if (t instanceof ClassOrInterfaceType) {
          ClassOrInterfaceType ct = (ClassOrInterfaceType) t;
          if (info.canonName.equals(ct.info.canonName)) {
            // This single-class-loader-esque assumption is valid for all code
            // compiled together.
            if (this.typeParameterBindings.equals(ct.typeParameterBindings)) {
              return Cast.SAME;
              // TODO: parameter variance
              // TODO: really
              // TODO: really
              // TODO: really
              // TODO: really
              // TODO: really
              // TODO: really
              // TODO: really
              // TODO: really
              // TODO: really
            } else if (this.typeParameterBindings.isEmpty()) {
              // Optimistic raw type assumptions.
              return Cast.CONFIRM_SAFE;
            } else if (ct.typeParameterBindings.isEmpty()) {
              return Cast.CONFIRM_UNCHECKED;
            } else {
              return Cast.DISJOINT;
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
                return Cast.CONFIRM_CHECKED;
              case CONFIRM_SAFE:
              case CONFIRM_CHECKED:  // Is this right?
              case CONFIRM_UNCHECKED:
                return castToCommonSuper;
              case CONVERTING_LOSSLESS:
              case CONVERTING_LOSSY:
              case DISJOINT:
              case BOX:
              case UNBOX:
                throw new AssertionError(castToCommonSuper);
            }
          }

          // TODO: Do we need to work on the
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
    public int hashCode() {
      final int prime = 31;
      int result = 1;
      result = prime * result + info.canonName.hashCode();
      result = prime * result + r.hashCode();
      result = prime * result + typeParameterBindings.hashCode();
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
      ClassOrInterfaceType other = (ClassOrInterfaceType) obj;
      if (info == null) {
        if (other.info != null) {
          return false;
        }
      } else if (!info.canonName.equals(other.info.canonName)) {
        return false;
      }
      if (getPool() != other.getPool()) {
        return false;
      }

      if (typeParameterBindings == null) {
        if (other.typeParameterBindings != null) {
          return false;
        }
      } else if (!typeParameterBindings.equals(other.typeParameterBindings)) {
        return false;
      }
      return true;
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
      public String toString() {
        return elementType + "[]";
      }

      @Override
      public boolean equals(Object o) {
        return (o instanceof ArrayType)
            && elementType.equals(((ArrayType) o).elementType);
      }

      @Override
      public int hashCode() {
        return elementType.hashCode() ^ 0xe20d66a3;
      }

      @Override
      public Cast assignableFrom(StaticType t) {
        if (ERROR_TYPE.equals(t)) {
          return Cast.CONFIRM_UNCHECKED;
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
    }
  }
}
