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

  /** */
  public static final TypeSpecification JAVA_LANG_OBJECT =
      new TypeSpecification(
          JAVA_LANG.child("Object", Name.Type.CLASS));

  /** */
  public static final TypeSpecification JAVA_LANG_CLONEABLE =
      new TypeSpecification(
          JAVA_LANG.child("Cloneable", Name.Type.CLASS));

  /** */
  public static final TypeSpecification JAVA_LANG_STRING =
      new TypeSpecification(JAVA_LANG.child("String", Name.Type.CLASS));

  /** */
  public static final TypeSpecification JAVA_LANG_CLASS =
      new TypeSpecification(
          JAVA_LANG.child("Class", Name.Type.CLASS),
          ImmutableList.of(TypeBinding.WILDCARD));

  /** */
  public static final TypeSpecification JAVA_LANG_VOID =
      new TypeSpecification(JAVA.child("Void", Name.Type.CLASS));


}
