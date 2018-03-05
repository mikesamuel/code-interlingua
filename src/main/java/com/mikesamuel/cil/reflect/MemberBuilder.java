package com.mikesamuel.cil.reflect;

abstract class MemberBuilder<MD extends MemberMetadata> {
  final int modifiers;
  final String name;

  MemberBuilder(int modifiers, String name) {
    this.modifiers = modifiers;
    this.name = name;
  }

  protected abstract TypeDeclarationBuilder endMember(MD md);

  protected abstract MD toMemberMetadata();

  public final TypeDeclarationBuilder endMember() {
    return endMember(toMemberMetadata());
  }
}
