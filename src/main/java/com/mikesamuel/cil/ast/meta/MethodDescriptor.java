package com.mikesamuel.cil.ast.meta;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

/**
 * A Java method descriptor which bundles the name of the containing class,
 * erased parameters, and return type.
 *
 * @see <a href="https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-4.html#jvms-4.3.3">JVM 4.3.3</a>
 */
public final class MethodDescriptor {

  /** The erased parameter types. */
  public final ImmutableList<TypeSpecification> formalTypes;
  /** The erased return type. */
  public final TypeSpecification returnType;

  private MethodDescriptor(
      ImmutableList<TypeSpecification> formalTypes,
      TypeSpecification returnType) {
    this.formalTypes = formalTypes;
    this.returnType = returnType;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append('(');
    for (TypeSpecification formalType : formalTypes) {
      append(sb, formalType);
    }
    sb.append(')');
    append(sb, returnType);
    return sb.toString();
  }

  private static void append(StringBuilder sb, TypeSpecification typ) {
    for (int i = 0; i < typ.nDims; ++i) {
      sb.append('[');
    }
    switch (typ.rawName.type) {
      case CLASS:
        typ.rawName.appendTypeDescriptor(sb);
        return;
      case FIELD:
        sb.append(PRIMITIVE_FIELD_TYPES.get(typ.rawName).charValue());
        return;
      case AMBIGUOUS:
      case LOCAL:
      case METHOD:
      case PACKAGE:
      case TYPE_PARAMETER:
        break;
    }
    throw new AssertionError(typ);
  }

  private static final ImmutableMap<Name, Character> PRIMITIVE_FIELD_TYPES
      = ImmutableMap.<Name, Character>builder()
      .put(StaticType.T_VOID.typeSpecification.rawName, 'V')
      .put(StaticType.T_BOOLEAN.typeSpecification.rawName, 'Z')
      .put(StaticType.T_BYTE.typeSpecification.rawName, 'B')
      .put(StaticType.T_CHAR.typeSpecification.rawName, 'C')
      .put(StaticType.T_DOUBLE.typeSpecification.rawName, 'D')
      .put(StaticType.T_FLOAT.typeSpecification.rawName, 'F')
      .put(StaticType.T_INT.typeSpecification.rawName, 'I')
      .put(StaticType.T_LONG.typeSpecification.rawName, 'J')
      .put(StaticType.T_SHORT.typeSpecification.rawName, 'S')
      .build();


  /** A builder for method descriptors. */
  public static Builder builder() { return new Builder(); }


  /** Mutable builder for method descriptors. */
  public static final class Builder {
    private ImmutableList.Builder<TypeSpecification> formals =
        ImmutableList.builder();
    private TypeSpecification returnType = StaticType.T_VOID.typeSpecification;

    /**
     * @param typeName the name of an array type.
     * @return this
     */
    public Builder withReturnType(Name typeName, int arrayDimensionality) {
      Preconditions.checkArgument(isConcreteTypeName(typeName));
      Preconditions.checkArgument(arrayDimensionality >= 0);
      returnType = TypeSpecification.unparameterized(typeName)
          .withNDims(arrayDimensionality);
      return this;
    }

    /**
     * @param typeName the name of an array type.
     * @return this
     */
    public Builder addFormalParameter(Name typeName, int arrayDimensionality) {
      Preconditions.checkArgument(isConcreteTypeName(typeName), typeName);
      Preconditions.checkArgument(arrayDimensionality >= 0);
      this.formals.add(
          TypeSpecification.unparameterized(typeName)
          .withNDims(arrayDimensionality));
      return this;
    }

    /** Constructs an immutable method descriptor. */
    @SuppressWarnings("synthetic-access")
    public MethodDescriptor build() {
      return new MethodDescriptor(formals.build(), returnType);
    }

    private static boolean isConcreteTypeName(Name typeName) {
      // TODO: Approximate.   Check primitive type names in set.
      return typeName.type == Name.Type.CLASS
          || typeName.type == Name.Type.FIELD
          && "TYPE".equals(typeName.identifier);
    }

  }

  /**
   * Maps type names using the given bridge.
   */
  public MethodDescriptor map(MetadataBridge b) {
    if (b == MetadataBridge.Bridges.IDENTITY) { return this; }
    MethodDescriptor.Builder bridged = builder();
    for (TypeSpecification formal : formalTypes) {
      TypeSpecification bridgedFormal = b.bridgeTypeSpecification(formal);
      bridged.addFormalParameter(bridgedFormal.rawName, bridgedFormal.nDims);
    }
    TypeSpecification bridgedRtype = b.bridgeTypeSpecification(returnType);
    bridged.withReturnType(bridgedRtype.rawName, bridgedRtype.nDims);
    return bridged.build();
  }


  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + formalTypes.hashCode();
    result = prime * result + returnType.hashCode();
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
    MethodDescriptor other = (MethodDescriptor) obj;
    if (formalTypes == null) {
      if (other.formalTypes != null) {
        return false;
      }
    } else if (!formalTypes.equals(other.formalTypes)) {
      return false;
    }
    if (returnType == null) {
      if (other.returnType != null) {
        return false;
      }
    } else if (!returnType.equals(other.returnType)) {
      return false;
    }
    return true;
  }
}
