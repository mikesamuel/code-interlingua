package com.mikesamuel.cil.reflect;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;

/** A builder for {@link TypeMetadata} */
public abstract class TypeDeclarationBuilder {
  private final int modifiers;
  private final String name;
  private final ImmutableList.Builder<String> typeParameters
      = ImmutableList.builder();
  private final ImmutableList.Builder<String> innerTypes
      = ImmutableList.builder();
  private Optional<String> outerType = Optional.absent();
  private final ImmutableList.Builder<TypeReference> superTypes
      = ImmutableList.builder();
  private final ImmutableList.Builder<ConstructorMetadata> constructors
      = ImmutableList.builder();
  private final ImmutableList.Builder<FieldMetadata> fields
      = ImmutableList.builder();
  private final ImmutableList.Builder<MethodMetadata> methods
      = ImmutableList.builder();

  TypeDeclarationBuilder(int modifiers, String name) {
    this.modifiers = modifiers;
    this.name = name;
  }

  /** */
  public TypeDeclarationBuilder param(String paramName) {
    this.typeParameters.add(paramName);
    return this;
  }

  /** */
  public TypeDeclarationBuilder innerType(String typeName) {
    this.innerTypes.add(typeName);
    return this;
  }

  /** */
  public TypeDeclarationBuilder outerType(String typeName) {
    this.outerType = Optional.of(typeName);
    return this;
  }

  /** */
  public TypeReferenceBuilder<TypeDeclarationBuilder> superType() {
    TypeDeclarationBuilder outer = this;
    return new TypeReferenceBuilder<TypeDeclarationBuilder>() {

      @Override
      @SuppressWarnings("synthetic-access")
      protected TypeDeclarationBuilder t(TypeReference t) {
        outer.superTypes.add(t);
        return outer;
      }

    };
  }

  /** */
  public ConstructorBuilder ctor(int mods, String memberName, String descriptor) {
    return new ConstructorBuilder(mods, memberName, descriptor) {
      @Override
      @SuppressWarnings("synthetic-access")
      protected TypeDeclarationBuilder endMember(ConstructorMetadata md) {
        TypeDeclarationBuilder.this.constructors.add(md);
        return TypeDeclarationBuilder.this;
      }
    };
  }

  /** */
  public MethodBuilder method(int mods, String memberName, String descriptor) {
    return new MethodBuilder(mods, memberName, descriptor) {
      @Override
      @SuppressWarnings("synthetic-access")
      protected TypeDeclarationBuilder endMember(MethodMetadata md) {
        TypeDeclarationBuilder.this.methods.add(md);
        return TypeDeclarationBuilder.this;
      }
    };
  }

  /** */
  public FieldBuilder field(int mods, String memberName) {
    return new FieldBuilder(mods, memberName) {
      @Override
      @SuppressWarnings("synthetic-access")
      protected TypeDeclarationBuilder endMember(FieldMetadata md) {
        TypeDeclarationBuilder.this.fields.add(md);
        return TypeDeclarationBuilder.this;
      }
    };
  }

  /**
   * Puts a type definition defined by previous methods into the store.
   */
  public void store() {
    store(new TypeMetadata(
        modifiers, name, typeParameters.build(), superTypes.build(),
        outerType, innerTypes.build(),
        constructors.build(), fields.build(), methods.build()));
  }

  abstract void store(TypeMetadata md);
}
