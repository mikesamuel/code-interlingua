package com.mikesamuel.cil.ast.meta;

import com.google.common.collect.ImmutableList;
import com.mikesamuel.cil.ast.meta.TypeSpecification.TypeBinding;

/**
 * Symbolic constants for things in the {@code java.lang} package.
 */
public final class JavaLang {
  private JavaLang() {
    // static API
  }

  /** */
  public static final Name JAVA =
      Name.DEFAULT_PACKAGE.child("java", Name.Type.PACKAGE);

  /** */
  public static final Name JAVA_LANG =
      JAVA.child("lang", Name.Type.PACKAGE);

  /** The package specification for {@code java.lang}. */
  public static final PackageSpecification PKG = new PackageSpecification(
      JAVA_LANG);

  /** */
  public static final TypeSpecification JAVA_LANG_OBJECT =
      new TypeSpecification(PKG, "Object", Name.Type.CLASS);

  /** */
  public static final TypeSpecification JAVA_LANG_CLONEABLE =
      new TypeSpecification(PKG, "Cloneable", Name.Type.CLASS);

  /** */
  public static final TypeSpecification JAVA_LANG_STRING =
      new TypeSpecification(PKG, "String", Name.Type.CLASS);

  /** */
  public static final TypeSpecification JAVA_LANG_CLASS =
      new TypeSpecification(
          PKG, "Class", Name.Type.CLASS,
          ImmutableList.of(TypeBinding.WILDCARD), 0);

  /** */
  public static final TypeSpecification JAVA_LANG_VOID =
      new TypeSpecification(PKG, "Void", Name.Type.CLASS);

  /** */
  public static final TypeSpecification JAVA_LANG_ENUM = new TypeSpecification(
          PKG, "Enum", Name.Type.CLASS);  // TODO: missing type parameter

  /** */
  public static final TypeSpecification JAVA_LANG_ANNOTATION_ANNOTATION =
      new TypeSpecification(
          new PackageSpecification(
              JAVA_LANG.child("annotation", Name.Type.PACKAGE)),
          "Annotation", Name.Type.CLASS);

  /** */
  public static final TypeSpecification JAVA_LANG_AUTOCLOSEABLE =
      new TypeSpecification(PKG, "AutoCloseable", Name.Type.CLASS);
}
