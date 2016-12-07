package com.mikesamuel.cil.ast.meta;

import javax.annotation.Nullable;

import com.google.common.base.Preconditions;
import com.mikesamuel.cil.ast.traits.CallableDeclaration;

/**
 * A name that can represent anything referred to by a name production.
 */
public final class Name {
  /**
   * The preceding elements in the name if any.
   * In a disambiguated name, this will only be null for a local variable,
   * type parameter, or the default package.
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
   * Among overloadable referents, a descriptor which statically specifies the
   * overloadable.
   * <p>
   * See {@link CallableDeclaration#getMethodDescriptor caveat}.
   */
  public final @Nullable String descriptor;
  /** The type of thing to which identifier refers. */
  public final Type type;


  /**
   * The name for the package used in compilation units that contain no package
   * declaration.
   */
  public static final Name DEFAULT_PACKAGE = new Name(
      null, "", null, Type.PACKAGE);

  private Name(
      @Nullable Name parent, String identifier,
      @Nullable String descriptor, @Nullable Type type) {
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
    // Locals must have a null parent.
    Preconditions.checkArgument(parent == null || type != Type.LOCAL);
    // Only the default package can be identifierless.
    Preconditions.checkArgument(
        identifier.length() != 0 || (type == Type.PACKAGE && parent == null));
    // Only methods have descriptors.  JLS defines field descriptors but they
    // are not involved in reference resolution.
    Preconditions.checkArgument(
        descriptor == null ||
        (type == Type.METHOD && descriptor.startsWith("(")));

    this.parent = parent;
    this.identifier = Preconditions.checkNotNull(identifier);
    this.descriptor = descriptor;
    this.type = Preconditions.checkNotNull(type);
  }

  /**
   * Constructs a Name with this as the parent name.
   */
  public Name child(String childIdentifier, Type childType) {
    return new Name(this, childIdentifier, null, childType);
  }

  /**
   * Constructs a method name with this as the parent name.
   */
  public Name method(String methodName, @Nullable String methodDescriptor) {
    return new Name(this, methodName, methodDescriptor, Type.METHOD);
  }

  /**
   * Name which can represent an unambiguous local variable or
   * type parameter name, an ambiguous field or method reference,
   * or an unqualified class name.
   */
  public static Name root(String childIdentifier, Type childType) {
    return new Name(null, childIdentifier, null, childType);
  }



  /**
   * The type of thing referred to by part of a name.
   */
  public enum Type {
    /** */
    PACKAGE,
    /** */
    CLASS,
    /** */
    FIELD,
    /** */
    METHOD,
    /** Includes method & constructor parameters. */
    LOCAL,
    /** */
    TYPE_PARAMETER,
    /** */
    AMBIGUOUS,
  }


  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((identifier == null) ? 0 : identifier.hashCode());
    result = prime * result + ((descriptor == null) ? 0 : descriptor.hashCode());
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
    if (descriptor == null) {
      if (other.descriptor != null) {
        return false;
      }
    } else if (!descriptor.equals(other.descriptor)) {
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
   * {@code ()}.
   * {@code <...>} surround type parameters.
   */
  public void appendInternalNameString(StringBuilder sb) {
    if (parent != null) {
      parent.appendInternalNameString(sb);
    }

    String before = null;
    String after = null;
    switch (type) {
      case AMBIGUOUS:
        if (parent != null) {
          before = "\ufe56";
        }
        break;
      case CLASS:
        if (parent == null
            || parent.type == Type.CLASS || parent.type == Type.METHOD) {
          before = "$";
        }
        break;
      case FIELD:
        before = ".";
        break;
      case LOCAL:
        break;
      case METHOD:
        if (parent != null) {
          before = ".";
        }
        after = descriptor != null ? descriptor : "()";
        break;
      case PACKAGE:
        after = "/";
        break;
      case TYPE_PARAMETER:
        before = "<";
        if (parent != null) {
          switch (parent.type) {
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
      sb.append(identifier);
    }
    if (after != null) {
      sb.append(after);
    }
  }

  /**
   * Assuming this name represents a type, the containing type if any.
   */
  public Name getOuterType() {
    if (type != Type.CLASS) { return null; }
    Name ancestor;
    for (ancestor = parent; ancestor != null && ancestor.type == Type.METHOD;
        ancestor = ancestor.parent) {
      // Just skipping method containers to find a class container.
    }
    return ancestor != null && ancestor.type == Type.CLASS ? ancestor : null;
  }
}
