package com.mikesamuel.cil.ast.passes;

import javax.annotation.Nullable;

import com.google.common.base.Preconditions;

/**
 * A name that can represent anything referred to by a name production.
 */
public final class Name {
  /**
   * The preceding elements in the name if any.
   * In a disambiguated name, this will only be null for a local variable,
   * type parameter, or the default package.
   */
  public final @Nullable Name parent;
  /**
   * The item referred to.
   * Null indicates the lack of an identifier, as in the default package and in
   * the name of an anonymous class.
   */
  public final @Nullable String identifier;
  /** The type of thing to which identifier refers. */
  public final Type type;


  /**
   * The name for the package used in compilation units that contain no package
   * declaration.
   */
  public static final Name DEFAULT_PACKAGE = new Name(null, null, Type.PACKAGE);

  private Name(
      @Nullable Name parent, @Nullable String identifier, @Nullable Type type) {
    Preconditions.checkArgument(type != null);
    // Packages cannot have non-package parents.
    Preconditions.checkArgument(
        type != Type.PACKAGE || parent == null
        || parent.type == Type.PACKAGE || parent.type == Type.AMBIGUOUS);
    // Classes can only have class or package parents.
    Preconditions.checkArgument(
        type != Type.CLASS
        || parent == null || parent.type == Type.PACKAGE
        || parent.type == Type.CLASS || parent.type == Type.AMBIGUOUS);
    // Methods cannot have children.
    Preconditions.checkArgument(parent == null || parent.type != Type.METHOD);
    // Locals and type parameters must have a null parent.
    Preconditions.checkArgument(
        parent == null || !(type == Type.TYPE_PARAMETER || type == Type.LOCAL));
    // Only the default package and anonymous classes can be identifierless.
    Preconditions.checkArgument(
        identifier != null || (type == Type.PACKAGE && parent == null)
        || type == Type.CLASS);

    this.parent = parent;
    this.identifier = identifier;
    this.type = type;
  }

  /**
   * Constructs a Name with this as the parent name.
   */
  public Name child(@Nullable String childIdentifier, Type childType) {
    return new Name(this, childIdentifier, childType);
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
    if (identifier == null) {
      if (other.identifier != null) {
        return false;
      }
    } else if (!identifier.equals(other.identifier)) {
      return false;
    }
    if (parent == null) {
      if (other.parent != null) {
        return false;
      }
    } else if (!parent.equals(other.parent)) {
      return false;
    }
    if (type != other.type) {
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
          before = "?";
        }
        break;
      case CLASS:
        if (parent.type == Type.CLASS) {
          before = "$";
        } else {
          before = "/";
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
        after = "()";
        break;
      case PACKAGE:
        if (parent != null || identifier != null) {
          before = "/";
        }
        break;
      case TYPE_PARAMETER:
        before = "<";
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
}
