package com.mikesamuel.cil.reflect;

import com.google.common.collect.ImmutableList;

/**
 * Metadata about the use of an annotation on an accessible declaration.
 */
public final class AnnotationUseMetadata {
  /** The name of the annotation. */
  public final String name;
  /** The parameter values. */
  public final ImmutableList<Value> values;

  AnnotationUseMetadata(String name, Iterable<? extends Value> values) {
    this.name = name;
    this.values = ImmutableList.copyOf(values);
  }
}
