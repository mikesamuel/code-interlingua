package com.mikesamuel.cil.reflect;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import com.google.common.collect.ImmutableList;

/** A builder for {@link AnnotationUseMetadata} */
public final class AnnotationBuilder {
  private final String name;
  private final ImmutableList.Builder<Value> values =
      ImmutableList.builder();

  AnnotationBuilder(String name) {
    this.name = name;
  }

  /** A builder for the list of annotation element values. */
  public ElementValuesBuilder<AnnotationBuilder> values() {
    return new ElementValuesBuilder<AnnotationBuilder>() {
      @Override
      @SuppressWarnings("synthetic-access")
      public AnnotationBuilder valuesComplete() {
        AnnotationBuilder.this.values.addAll(this.values.build());
        return AnnotationBuilder.this;
      }
    };
  }

  /**
   * Puts an annotation use defined by previously method calls into the
   * store.
   */
  public void store() {
    List<AnnotationUseMetadata> annots = TypeMetadataStore.annotationUses.computeIfAbsent(
        name,
        new Function<String, List<AnnotationUseMetadata>>() {

          @Override
          public List<AnnotationUseMetadata> apply(String annotName) {
            return Collections.synchronizedList(
                new ArrayList<AnnotationUseMetadata>());
          }

        });
    annots.add(new AnnotationUseMetadata(name, values.build()));
  }
}