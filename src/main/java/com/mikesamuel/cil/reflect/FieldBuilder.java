package com.mikesamuel.cil.reflect;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;

/** A builder for field metadata. */
public abstract class FieldBuilder extends MemberBuilder<FieldMetadata> {
  private TypeReference type = TypeReference.T_VOID;
  private Optional<FieldAdapter> adapter = Optional.absent();

  FieldBuilder(int modifiers, String name) {
    super(modifiers, name);
  }

  /** */
  public FieldBuilder adapter(FieldAdapter fieldAdapter) {
    this.adapter = Optional.of(fieldAdapter);
    return this;
  }

  /** */
  public TypeReferenceBuilder<FieldBuilder> type() {
    return new TypeReferenceBuilder<FieldBuilder>() {

      @Override
      @SuppressWarnings("synthetic-access")
      protected FieldBuilder t(TypeReference t) {
        type = t;
        return FieldBuilder.this;
      }

    };
  }

  @Override
  protected FieldMetadata toMemberMetadata() {
    Preconditions.checkState(!type.equals(TypeReference.T_VOID));
    return new FieldMetadata(modifiers, name, type, adapter);
  }
}
