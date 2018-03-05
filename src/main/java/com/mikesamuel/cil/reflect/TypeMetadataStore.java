package com.mikesamuel.cil.reflect;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Allows reflecting over declared types.
 */
public final class TypeMetadataStore {
  private TypeMetadataStore() {
    // Static API
  }

  static final ConcurrentMap<String, TypeMetadata> typeMetadata =
      new ConcurrentHashMap<>();
  /**
   * Maps names of packages, classes, methods, fields, etc. to annotations on
   * those.
   */
  static final ConcurrentMap<String, List<AnnotationUseMetadata>>
     annotationUses = new ConcurrentHashMap<>();


  // Methods to register type metadata
  /**
   * Builds a type declaration that will be added to the store on store().
   *
   * @param modifiers bit-field of {@link java.lang.reflect.Modifier} bits.
   * @param name the fully-qualified method name with variant.
   */
  public static TypeDeclarationBuilder type(int modifiers, String name) {
    return new TypeDeclarationBuilder(modifiers, name) {

      @Override
      void store(TypeMetadata md) {
        TypeMetadataStore.typeMetadata.putIfAbsent(md.name, md);
      }

    };
  }

  /**
   * Builds an annotation use that will be added to the store on store().
   *
   * @param name the annotation type name.
   */
  public static AnnotationBuilder annotate(String name) {
    return new AnnotationBuilder(name);
  }
}
