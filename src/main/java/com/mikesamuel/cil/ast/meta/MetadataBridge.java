package com.mikesamuel.cil.ast.meta;

import com.mikesamuel.cil.ast.meta.ExpressionNameResolver
    .DeclarationPositionMarker;

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
  MemberInfo bridgeMemberInfo(MemberInfo x);

  /** Transforms input from a source to that appropriate for a destination. */
  String bridgeMethodDescriptor(String x);

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
      public String bridgeMethodDescriptor(String x) {
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
  }

}
