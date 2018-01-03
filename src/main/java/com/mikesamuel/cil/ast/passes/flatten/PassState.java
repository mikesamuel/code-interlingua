package com.mikesamuel.cil.ast.passes.flatten;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.mikesamuel.cil.ast.j8.ExpressionAtomNode;
import com.mikesamuel.cil.ast.j8.ExpressionNode;
import com.mikesamuel.cil.ast.j8.FieldNameNode;
import com.mikesamuel.cil.ast.j8.IdentifierNode;
import com.mikesamuel.cil.ast.j8.J8BaseNode;
import com.mikesamuel.cil.ast.j8.J8FileNode;
import com.mikesamuel.cil.ast.j8.J8NodeType;
import com.mikesamuel.cil.ast.j8.J8TypeDeclaration;
import com.mikesamuel.cil.ast.j8.LiteralNode;
import com.mikesamuel.cil.ast.j8.LocalNameNode;
import com.mikesamuel.cil.ast.j8.NullLiteralNode;
import com.mikesamuel.cil.ast.j8.PrimaryNode;
import com.mikesamuel.cil.ast.meta.FieldInfo;
import com.mikesamuel.cil.ast.meta.JavaLang;
import com.mikesamuel.cil.ast.meta.TypeInfo;
import com.mikesamuel.cil.ast.meta.TypeInfoResolver;
import com.mikesamuel.cil.ast.meta.TypeSpecification;
import com.mikesamuel.cil.ast.passes.NameAllocator;

/**
 * Collects information about type declarations as we flatten them.
 * This allows tightly-coupled mini-passes to each provide outputs to
 * the next.
 */
final class PassState {

  final ImmutableMap<BName, FlatteningType> byBumpyName;
  final ImmutableMap<FName, FlatteningType> byFlatName;
  final ImmutableList<FlatteningType> inProcessOrder;

  PassState(Iterable<? extends FlatteningType> inProcessOrder) {
    this.inProcessOrder = ImmutableList.copyOf(inProcessOrder);
    ImmutableMap.Builder<BName, FlatteningType> bb = ImmutableMap.builder();
    ImmutableMap.Builder<FName, FlatteningType> fb = ImmutableMap.builder();
    for (FlatteningType d : this.inProcessOrder) {
      bb.put(d.bumpyName, d);
      fb.put(d.flatName, d);
    }
    this.byBumpyName = bb.build();
    this.byFlatName = fb.build();
  }

  /** A type declaration that we are in the process of flattening rewriting. */
  static final class FlatteningType {
    final J8FileNode declarationSite;
    final J8TypeDeclaration root;
    final BName bumpyName;
    final FName flatName;
    final TypeInfo typeInfo;
    final NameAllocator nameAllocator;
    final FlatTypes.FlatParamInfo flatParamInfo;
    final Map<ClosedOver, FieldInfo> closedOverMembers =
        new LinkedHashMap<>();
    final BiMap<BName, ImmutableList<FName>> memberNameToExtraArguments =
        HashBiMap.create();
    /**
     * THe keys of closedOverMembers in the order they are received by
     * adjusted constructors.
     */
    ImmutableList<ClosedOver> closedOverInOrder;
    /**
     * Stores the name of the synthesized method that initializes closed over
     * fields.
     */
    String initMethodName;
    /**
     * True if we can use simple assignment in the constructor to initialize
     * fields that store closed-over state.
     * <p>
     * This will be false when we are not sure that the super-class constructor
     * does not call methods that depend on those fields, or if variable
     * initializers (which run before the constructor body statements)
     * depend on closed over state.
     * @see <a href="https://docs.oracle.com/javase/specs/jls/se8/html/jls-12.html#jls-12.5">12.5</a>
     */
    boolean eligibleForSimpleInitialization;

    FlatteningType(
        J8FileNode declarationSite,
        J8TypeDeclaration root,
        BName bumpyName,
        FName flatName,
        FlatTypes.FlatParamInfo flatParamInfo,
        TypeInfoResolver resolver) {
      Preconditions.checkArgument(
          root.getNodeType() != J8NodeType.TypeParameter);
      this.declarationSite = declarationSite;
      this.root = root;
      this.bumpyName = bumpyName;
      this.flatName = flatName;
      this.flatParamInfo = flatParamInfo;
      this.typeInfo = resolver.resolve(bumpyName.name).get();
      this.nameAllocator = NameAllocator.create(
          (J8BaseNode) root,
          new Predicate<J8BaseNode>() {

            @Override
            public boolean apply(J8BaseNode node) {
              // Do not descend into nested type declarations since
              // we will be producing flat type declarations.
              return node != root
                  && (node instanceof J8TypeDeclaration
                      && node.getNodeType() != J8NodeType.TypeParameter);
            }

          },
          resolver);
      this.nameAllocator.exclude(
          Collections.singleton(this.flatName.name.identifier));
      this.nameAllocator.exclude(Lists.transform(
          this.flatParamInfo.flatParametersInOrder,
          new Function<FName, String>() {
            @Override
            public String apply(FName nm) {
              return nm.name.identifier;
            }
          }));
      eligibleForSimpleInitialization =
          // Initial guess which may be later revised to false based
          // on analysis of field initializers.
          JavaLang.JAVA_LANG_OBJECT.equals(typeInfo.superType.orNull());
    }
  }

  static abstract class ClosedOver implements Comparable<ClosedOver> {
    @Override public abstract boolean equals(Object o);
    @Override public abstract int hashCode();
    abstract TypeSpecification getType(TypeInfoResolver r);
    abstract String toFieldName(NameAllocator nameAllocator);
    abstract boolean implicitlyClosesOver(FlatteningType ft);
    abstract J8BaseNode toExpressionNode(
        PassState ps, FlatteningType ft, boolean isStatic);
  }

  static int defaultCompareTwoClosedOver(ClosedOver a, ClosedOver b) {
    return a.getClass().getSimpleName()
        .compareTo(b.getClass().getSimpleName());
  }


  static final class ClosedOverThisValue extends ClosedOver {
    final FlatteningType outerType;

    ClosedOverThisValue(FlatteningType outerType) {
      this.outerType = Preconditions.checkNotNull(outerType);
    }

    @Override
    TypeSpecification getType(TypeInfoResolver r) {
      return TypeSpecification.autoScoped(outerType.bumpyName.name, r);
    }

    @Override
    String toFieldName(NameAllocator nameAllocator) {
      return nameAllocator.allocateIdentifier(
          "this__" + outerType.flatName.name.identifier);
    }

    @Override
    boolean implicitlyClosesOver(FlatteningType ft) {
      return outerType.equals(ft);
    }

    @Override
    J8BaseNode toExpressionNode(
        PassState ps, FlatteningType ft, boolean isStatic) {
      if (!isStatic) {
        if (ft == this.outerType) {
          return ExpressionNode.Variant.ConditionalExpression.buildNode(
              ExpressionAtomNode.Variant.This.buildNode());
        }
        FieldInfo fi = ft.closedOverMembers.get(this);
        if (fi != null) {
          return ExpressionNode.Variant.ConditionalExpression.buildNode(
              PrimaryNode.Variant.FieldAccess.buildNode(
                  ExpressionAtomNode.Variant.This.buildNode(),
                  FieldNameNode.Variant.Identifier.buildNode(
                      IdentifierNode.Variant.Builtin.buildNode(
                          fi.canonName.identifier))));
        }
      }
      return ExpressionNode.Variant.ConditionalExpression.buildNode(
          ExpressionAtomNode.Variant.Literal.buildNode(
              LiteralNode.Variant.NullLiteral.buildNode(
                  NullLiteralNode.Variant.Null.buildNode())));
    }

    @Override
    public int compareTo(ClosedOver that) {
      if (that instanceof ClosedOverThisValue) {
        return this.outerType.flatName.compareTo(
            ((ClosedOverThisValue) that).outerType.flatName);
      }
      return defaultCompareTwoClosedOver(this, that);
    }

    @Override
    public int hashCode() {
      final int prime = 31;
      int result = 1;
      result = prime * result + outerType.hashCode();
      return result;
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) { return true; }
      if (obj == null || getClass() != obj.getClass()) { return false; }
      ClosedOverThisValue other = (ClosedOverThisValue) obj;
      return this.outerType == other.outerType;
    }

    @Override
    public String toString() {
      return "(" + outerType.bumpyName + ".this)";
    }
  }

  static final class ClosedOverLocal extends ClosedOver {
    final TypeSpecification bumpyType;
    final BName localName;

    ClosedOverLocal(TypeSpecification bumpyType, BName localName) {
      this.bumpyType = bumpyType;
      this.localName = Preconditions.checkNotNull(localName);
    }

    @Override
    TypeSpecification getType(TypeInfoResolver r) {
      return this.bumpyType;
    }

    @Override
    String toFieldName(NameAllocator nameAllocator) {
      return nameAllocator.allocateIdentifier(
          "closedOver__" + localName.name.identifier);
    }

    @Override
    boolean implicitlyClosesOver(FlatteningType ft) {
      return ft.bumpyName.name.equals(localName.name.getContainingClass());
    }

    @Override
    J8BaseNode toExpressionNode(
        PassState ps, FlatteningType ft, boolean isStatic) {
      FieldInfo fi = ft.closedOverMembers.get(this);
      if (fi != null) {
        return ExpressionNode.Variant.ConditionalExpression.buildNode(
            PrimaryNode.Variant.FieldAccess.buildNode(
                ExpressionAtomNode.Variant.This.buildNode(),
                FieldNameNode.Variant.Identifier.buildNode(
                    IdentifierNode.Variant.Builtin.buildNode(
                        fi.canonName.identifier))));
      }
      return ExpressionNode.Variant.ConditionalExpression.buildNode(
          ExpressionAtomNode.Variant.Local.buildNode(
              LocalNameNode.Variant.Identifier.buildNode(
                  IdentifierNode.Variant.Builtin.buildNode(
                      this.localName.name.identifier))));
    }

    @Override
    public int hashCode() {
      final int prime = 31;
      int result = 1;
      result = prime * result + localName.hashCode();
      return result;
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      }
      if (obj == null || getClass() != obj.getClass()) {
        return false;
      }
      ClosedOverLocal other = (ClosedOverLocal) obj;
      return this.localName.equals(other.localName);
    }

    @Override
    public int compareTo(ClosedOver that) {
      if (that instanceof ClosedOverLocal) {
        return this.localName.compareTo(((ClosedOverLocal) that).localName);
      }
      return defaultCompareTwoClosedOver(this, that);
    }

    @Override
    public String toString() {
      return "(local " + localName + ")";
    }
  }
}
