package com.mikesamuel.cil.ast.meta;

import javax.annotation.Nullable;

import com.google.common.base.Preconditions;

/**
 * A name that can represent anything referred to by a name production.
 */
public final class Name implements Comparable<Name> {
  /**
   * The preceding elements in the name if any.
   * In a unambiguous name, this will only be null for the default package.
   * <p>
   * In an ambiguous name, it may be null to indicate that we don't know which
   * tree (default package, imported package, inherited scope, current scope)
   * is the name rooted in,
   */
  public final @Nullable Name parent;
  /**
   * The item referred to.
   * Empty indicates the lack of an identifier and is only used for the default
   * package.  Anonymous classes are assigned ordinal names early on.
   */
  public final String identifier;
  /**
   * Among overloadable referents, an index which uniquely identifies the
   * particular variant.  Until the class member pass runs, we can't use
   * method descriptors.
   * <p>
   * See {@link com.mikesamuel.cil.ast.mixins.CallableDeclaration#getMethodVariant caveat}.
   */
  public final @Nullable int variant;
  /** The type of thing to which identifier refers. */
  public final Type type;


  /**
   * The name for the package used in compilation units that contain no package
   * declaration.
   */
  public static final Name DEFAULT_PACKAGE = new Name(
      null, "", 0, Type.PACKAGE);
  /**
   * "{@code <init>}" reserved for use by the JVM spec as the name for
   * special methods that are invoked to initialize an instance.
   */
  public static final String CTOR_INSTANCE_INITIALIZER_SPECIAL_NAME = "<init>";
  /**
   * "{@code <clinit>}" reserved for use by the JVM spec as the name for
   * the special method that are invoked during class initialization.
   */
  public static final String STATIC_INITIALIZER_SPECIAL_NAME = "<clinit>";

  /**
   * True if identifier is a special method name reserved for code invoked via
   * the <i>invokespecial</i> bytecode.
   */
  public static boolean isSpecialMethodIdentifier(String identifier) {
    return CTOR_INSTANCE_INITIALIZER_SPECIAL_NAME.equals(identifier)
        || STATIC_INITIALIZER_SPECIAL_NAME.equals(identifier);
  }

  private Name(
      @Nullable Name parent, String identifier,
      @Nullable int variant, Type type) {
    Preconditions.checkArgument(type != null);
    // Packages cannot have non-package parents.
    Preconditions.checkArgument(
        type != Type.PACKAGE || parent == null
        || parent.type == Type.PACKAGE || parent.type == Type.AMBIGUOUS);
    // Only the default package does not have a parent.
    Preconditions.checkArgument(
        type != Type.PACKAGE || (parent == null) == (identifier.length() == 0));
    // Classes can only have class, method, or package parents.
    Preconditions.checkArgument(
        type != Type.CLASS
        || parent == null || parent.type == Type.PACKAGE
        || parent.type == Type.CLASS || parent.type == Type.AMBIGUOUS
        || parent.type == Type.METHOD);
    // Type parameters can only have class or method parents.
    Preconditions.checkArgument(
        type != Type.TYPE_PARAMETER
        || parent == null || parent.type == Type.CLASS
        || parent.type == Type.AMBIGUOUS
        || parent.type == Type.METHOD);
    // Only the default package can be identifierless.
    Preconditions.checkArgument(
        identifier.length() != 0 || (type == Type.PACKAGE && parent == null));
    // Only methods are overridable.
    Preconditions.checkArgument(
        (type == Type.METHOD ? variant > 0 : variant == 0));

    this.parent = parent;
    this.identifier = Preconditions.checkNotNull(identifier);
    this.variant = variant;
    this.type = Preconditions.checkNotNull(type);
  }

  /**
   * Constructs a Name with this as the parent name.
   */
  public Name child(String childIdentifier, Type childType) {
    return new Name(this, childIdentifier, 0, childType);
  }

  /**
   * Constructs a method name with this as the parent name.
   */
  public Name method(String methodName, int methodVariant) {
    return new Name(this, methodName, methodVariant, Type.METHOD);
  }

  /**
   * Name which can represent an unambiguous local variable or
   * type parameter name, an ambiguous field or method reference,
   * or an unqualified class name.
   */
  public static Name root(String childIdentifier, Type childType) {
    return new Name(null, childIdentifier, 0, childType);
  }

  /**
   * This name, but with the given parent.
   */
  public Name reparent(@Nullable Name newParent) {
    if (parent == newParent) { return this; }
    return new Name(newParent, identifier, variant, type);
  }

  /**
   * The type of thing referred to by part of a name.
   */
  public enum Type {
    /** Name for a package name element. */
    PACKAGE(false, false, false),
    /**
     * Name for a {@code class}, {@code enum}, {@code interface},
     * or {@code @interface} declaration.
     */
    CLASS(true, false, false),
    /** Name for a field. */
    FIELD(false, true, false),
    /** Name for a method, constructor, or initializer block. */
    METHOD(false, false, true),
    /** Includes method & constructor parameters. */
    LOCAL(false, true, false),
    /** Name for a type parameter declaration. */
    TYPE_PARAMETER(true, false, false),
    /**
     * Type for a name part that has not been disambiguated.
     * See the disambiguation pass for more detail.
     */
    AMBIGUOUS(false, false, false),
    ;

    /**
     * True if the name is the name of a class type, interface type,
     * or a type parameter.
     */
    public final boolean isType;

    /**
     * True if there could be a value associated with the name.
     */
    public final boolean isReadable;

    /**
     *
     */
    public final boolean isCallable;

    Type(boolean isType, boolean isReadable, boolean isCallable) {
      this.isType = isType;
      this.isReadable = isReadable;
      this.isCallable = isCallable;
    }
  }


  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result
           + ((identifier == null) ? 0 : identifier.hashCode());
    result = prime * result + variant;
    result = prime * result + ((parent == null) ? 0 : parent.hashCode());
    result = prime * result + ((type == null) ? 0 : type.hashCode());
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
    Name other = (Name) obj;
    if (type != other.type) {
      return false;
    }
    if (identifier == null) {
      if (other.identifier != null) {
        return false;
      }
    } else if (!identifier.equals(other.identifier)) {
      return false;
    }
    if (variant != other.variant) {
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

  @Override
  public int compareTo(Name nm) {
    int delta = parent != null
        ? nm.parent != null ? parent.compareTo(nm.parent) : 1
        : nm.parent == null ? 0 : -1;
    if (delta != 0) { return delta; }
    delta = this.variant - nm.variant;
    if (delta != 0) { return delta; }
    delta = this.type.compareTo(nm.type);
    if (delta != 0) { return delta; }
    return this.identifier.compareTo(nm.identifier);
  }

  @Override
  public String toString() {
    return toInternalNameString();
  }

  /**
   * Ambiguous name form {@code foo.bar.baz} where there is a dot between each
   * pair of names.
   */
  public String toDottedString() {
    StringBuilder sb = new StringBuilder();
    appendDottedString(sb);
    return sb.toString();
  }

  /**
   * A string similar to the internal name string used by the JVM specification
   * to refer to classes, methods, and fields.
   * <p>
   * {@code /} is used before the default package which is different from the
   * JVM spec.  This allows disambuating the local variable reference {@code x}
   * from a class {@code x} in the default package.
   * <p>
   * {@code /} is used between packages, and between a package and a class.
   * {@code $} is used between classes.
   * {@code .} is used before a field or a method, and a method is followed by
   * {@code ()}.
   * {@code <...>} surround type parameters.
   */
  public String toInternalNameString() {
    StringBuilder sb = new StringBuilder();
    appendInternalNameString(sb);
    return sb.toString();
  }

  /**
   * Ambiguous name form {@code foo.bar.baz} where there is a dot between each
   * pair of names.
   */
  public void appendDottedString(StringBuilder sb) {
    if (parent != null) {
      parent.appendDottedString(sb);
      if (!parent.equals(DEFAULT_PACKAGE)) {
        // This means that there is a dot at the end of the name of an anonymous
        // class.
        sb.append('.');
      }
    }
    if (identifier != null) {
      sb.append(identifier);
    }
  }


  /**
   * A string similar to the internal name string used by the JVM specification
   * to refer to classes, methods, and fields.
   * <p>
   * {@code /} is used before the default package which is different from the
   * JVM spec.  This allows disambiguating the local variable reference
   * {@code x} and a class {@code /x} in the default package.
   * <p>
   * {@code /} is used before ambiguous names.
   * {@code /} is used between packages, and between a package and a class.
   * {@code $} is used between classes.
   * {@code .} is used before a field or a method, and a method is followed by
   * {@code ()} are used around a method variant.
   * {@code <...>} surround type parameters.
   * {@code :} is used between a method and local variables scoped to its body.
   */
  public void appendInternalNameString(StringBuilder sb) {
    if (parent != null) {
      parent.appendInternalNameString(sb);
    }
    appendInternalNamePart(
        parent != null ? parent.type : null,
        identifier, type, variant, sb);
  }

  /**
   * Appends a single part of an internal name to the given buffer.
   *
   * @param parentType the type of the parent that is at the end of the
   *      buffer content or null if there is no parent.
   */
  public static void appendInternalNamePart(
      @Nullable Name.Type parentType, String identifier, Name.Type type,
      int variant, StringBuilder sb) {
    String before = null;
    String after = null;
    switch (type) {
      case AMBIGUOUS:
        if (parentType != null) {
          before = "\ufe56";
        }
        break;
      case CLASS:
        if (parentType == null
            || parentType == Type.CLASS || parentType == Type.METHOD) {
          before = "$";
        }
        break;
      case FIELD:
        before = ".";
        break;
      case LOCAL:
        before = ":";
        break;
      case METHOD:
        if (parentType != null) {
          before = ".";
        }
        after = "(" + variant + ")";
        break;
      case PACKAGE:
        after = "/";
        break;
      case TYPE_PARAMETER:
        before = "<";
        if (parentType != null) {
          switch (parentType) {
            case PACKAGE:
              break;
            default:
              before = ".<";
              break;
          }
        }
        after = ">";
        break;
    }
    if (before != null) {
      sb.append(before);
    }
    if (identifier != null) {
      int appended = 0, length = identifier.length();
      for (int i = 0; i < length; ++i) {
        char ch = identifier.charAt(i);
        if (ch == '$') {
          sb.append(identifier, appended, i).append("\\$");
          appended = i + 1;
        }
      }
      sb.append(identifier, appended, length);
    }
    if (after != null) {
      sb.append(after);
    }
  }

  /**
   * Assuming this name represents a type, the containing type if any.
   */
  public Name getOuterType() {
    if (!type.isType) { return null; }
    Name ancestor;
    for (ancestor = parent; ancestor != null && ancestor.type == Type.METHOD;
        ancestor = ancestor.parent) {
      // Just skipping method containers to find a class container.
    }
    return ancestor != null && ancestor.type.isType ? ancestor : null;
  }

  /**
   * The package containing this name if any.
   */
  public Name getPackage() {
    Name nm = this;
    while (nm != null && nm.type != Type.PACKAGE) {
      nm = nm.parent;
    }
    return nm;
  }

  /**
   * The class containing this name if any.
   */
  public Name getContainingClass() {
    Name nm = this;
    while (nm != null && nm.type != Type.CLASS) {
      nm = nm.parent;
    }
    return nm;
  }

  /**
   * The top level class containing this name if any.
   */
  public Name getTopLevelClass() {
    Name nm = this;
    while (nm != null
        && (nm.type != Type.CLASS
        || (nm.parent != null && nm.parent.type != Type.PACKAGE))) {
      nm = nm.parent;
    }
    return nm;
  }

  /**
   * A Java binary name.
   */
  public String toBinaryName() {
    Preconditions.checkState(type == Name.Type.CLASS, this);
    StringBuilder sb = new StringBuilder();
    appendBinaryName(this, sb);
    return sb.toString();
  }

  private static void appendBinaryName(Name name, StringBuilder sb) {
    if (!(name.parent == null || Name.DEFAULT_PACKAGE.equals(name.parent))) {
      appendBinaryName(name.parent, sb);
      switch (name.parent.type) {
        case PACKAGE:
          sb.append('.');
          break;
        case CLASS:
          sb.append('$');
          break;
        case METHOD:
          break;
        default:
          throw new AssertionError(name.parent.type);
      }
    }
    if (name.type != Name.Type.METHOD) {
      Preconditions.checkNotNull(name.identifier);
      sb.append(name.identifier);
    }
  }

  /**
   * Converts a class type to a type descriptor form: {@code Lfoo/bar/Name;}.
   */
  public String toTypeDescriptor() {
    StringBuilder sb = new StringBuilder();
    appendTypeDescriptor(sb);
    return sb.toString();
  }

  /**
   * Converts a class type to a type descriptor form: {@code Lfoo/bar/Name;}.
   */
  public void appendTypeDescriptor(StringBuilder sb) {
    Preconditions.checkState(this.type == Name.Type.CLASS);
    sb.append('L');
    toDescriptor(this, sb);
    sb.append(';');
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

}
