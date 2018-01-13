package com.mikesamuel.cil.ast.meta;

import com.google.common.collect.ImmutableList;
import com.mikesamuel.cil.ast.meta.ExpressionNameResolver
    .DeclarationPositionMarker;
import com.mikesamuel.cil.ast.meta.Name.Type;

/**
 * Transforms node metadata when copying from a source to a destination.
 */
public interface MetadataBridge {

  /** Transforms input from a source to that appropriate for a destination. */
  DeclarationPositionMarker bridgeDeclarationPositionMarker(
      DeclarationPositionMarker x);

  /** Transforms input from a source to that appropriate for a destination. */
  Name bridgeDeclaredExpressionName(Name x);


  /** Transforms input from a source to that appropriate for a destination. */
  ExpressionNameResolver bridgeExpressionNameResolver(ExpressionNameResolver x);

  /** Transforms input from a source to that appropriate for a destination. */
  default CallableInfo bridgeCallableInfo(CallableInfo x) {
    return (CallableInfo) bridgeMemberInfo(x);
  }

  /** Transforms input from a source to that appropriate for a destination. */
  MemberInfo bridgeMemberInfo(MemberInfo x);

  /** Transforms input from a source to that appropriate for a destination. */
  ImmutableList<MemberInfo> bridgeImmutableListMemberInfo(
      ImmutableList<MemberInfo> x);

  /** Transforms input from a source to that appropriate for a destination. */
  MethodDescriptor bridgeMethodDescriptor(MethodDescriptor x);

  /** Transforms input from a source to that appropriate for a destination. */
  int bridgeMethodVariant(int x);

  /** Transforms input from a source to that appropriate for a destination. */
  Name.Type bridgeNamePartType(Name.Type x);

  /** Transforms input from a source to that appropriate for a destination. */
  Name bridgeReferencedExpressionName(Name x);

  /** Transforms input from a source to that appropriate for a destination. */
  StaticType bridgeStaticType(StaticType x);

  /** Transforms input from a source to that appropriate for a destination. */
  TypeInfo bridgeTypeInfo(TypeInfo x);

  /** Transforms input from a source to that appropriate for a destination. */
  TypeNameResolver bridgeTypeNameResolver(TypeNameResolver x);

  /** Transforms input from a source to that appropriate for a destination. */
  TypeSpecification bridgeTypeSpecification(TypeSpecification x);


  /** Singletons */
  public static class Bridges {
    /** Returns the input as is. */
    public static final MetadataBridge IDENTITY = new MetadataBridge() {

      @Override
      public String toString() {
        return "(identity)";
      }

      @Override
      public DeclarationPositionMarker bridgeDeclarationPositionMarker(
          DeclarationPositionMarker x) {
        return x;
      }

      @Override
      public Name bridgeDeclaredExpressionName(Name x) {
        return x;
      }

      @Override
      public ExpressionNameResolver bridgeExpressionNameResolver(
          ExpressionNameResolver x) {
        return x;
      }

      @Override
      public MemberInfo bridgeMemberInfo(MemberInfo x) {
        return x;
      }

      @Override
      public ImmutableList<MemberInfo> bridgeImmutableListMemberInfo(
          ImmutableList<MemberInfo> x) {
        return x;
      }

      @Override
      public MethodDescriptor bridgeMethodDescriptor(MethodDescriptor x) {
        return x;
      }

      @Override
      public int bridgeMethodVariant(int x) {
        return x;
      }

      @Override
      public Name.Type bridgeNamePartType(Name.Type x) {
        return x;
      }

      @Override
      public Name bridgeReferencedExpressionName(Name x) {
        return x;
      }

      @Override
      public StaticType bridgeStaticType(StaticType x) {
        return x;
      }

      @Override
      public TypeInfo bridgeTypeInfo(TypeInfo x) {
        return x;
      }

      @Override
      public TypeNameResolver bridgeTypeNameResolver(TypeNameResolver x) {
        return x;
      }

      @Override
      public TypeSpecification bridgeTypeSpecification(TypeSpecification x) {
        return x;
      }
    };

    /**
     * A bridge that maps all non-parse metadata to the zero value for the type.
     */
    public static final MetadataBridge ZERO = new MetadataBridge() {

      @Override
      public DeclarationPositionMarker bridgeDeclarationPositionMarker(
          DeclarationPositionMarker x) {
        return null;
      }

      @Override
      public Name bridgeDeclaredExpressionName(Name x) {
        return null;
      }

      @Override
      public ExpressionNameResolver bridgeExpressionNameResolver(
          ExpressionNameResolver x) {
        return null;
      }

      @Override
      public MemberInfo bridgeMemberInfo(MemberInfo x) {
        return null;
      }

      @Override
      public ImmutableList<MemberInfo> bridgeImmutableListMemberInfo(
          ImmutableList<MemberInfo> x) {
        return null;
      }

      @Override
      public MethodDescriptor bridgeMethodDescriptor(MethodDescriptor x) {
        return null;
      }

      @Override
      public int bridgeMethodVariant(int x) {
        return 0;
      }

      @Override
      public Type bridgeNamePartType(Type x) {
        return null;
      }

      @Override
      public Name bridgeReferencedExpressionName(Name x) {
        return null;
      }

      @Override
      public StaticType bridgeStaticType(StaticType x) {
        return null;
      }

      @Override
      public TypeInfo bridgeTypeInfo(TypeInfo x) {
        return null;
      }

      @Override
      public TypeNameResolver bridgeTypeNameResolver(TypeNameResolver x) {
        return null;
      }

      @Override
      public TypeSpecification bridgeTypeSpecification(TypeSpecification x) {
        return null;
      }

    };
  }

}
