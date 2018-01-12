package com.mikesamuel.cil.xlate.common;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Nullable;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.mikesamuel.cil.ast.j8.J8BaseLeafNode;
import com.mikesamuel.cil.ast.j8.J8BaseNode;
import com.mikesamuel.cil.ast.j8.J8NodeType;
import com.mikesamuel.cil.ast.j8.J8NodeVariant;
import com.mikesamuel.cil.ast.j8.J8TypeDeclaration;
import com.mikesamuel.cil.ast.jmin.JminBaseNode;
import com.mikesamuel.cil.ast.jmin.JminNodeType;
import com.mikesamuel.cil.ast.jmin.JminNodeVariant;
import com.mikesamuel.cil.ast.jmin.JminTypeDeclaration;
import com.mikesamuel.cil.ast.meta.CallableInfo;
import com.mikesamuel.cil.ast.meta.JavaLang;
import com.mikesamuel.cil.ast.meta.MemberInfo;
import com.mikesamuel.cil.ast.meta.MethodDescriptor;
import com.mikesamuel.cil.ast.meta.Name;
import com.mikesamuel.cil.ast.meta.StaticType;
import com.mikesamuel.cil.ast.meta.TypeInfo;
import com.mikesamuel.cil.ast.meta.TypeInfoResolver;
import com.mikesamuel.cil.ast.meta.TypeSpecification;
import com.mikesamuel.cil.ast.mixins.TypeDeclaration;
import com.mikesamuel.cil.ast.passes.NameAllocator;
import com.mikesamuel.cil.ast.passes.TypeNodeFactory;
import com.mikesamuel.cil.parser.Positioned;
import com.mikesamuel.cil.parser.SourcePosition;
import com.mikesamuel.cil.util.LogUtils;

/**
 * Translates {@linkplain com.mikesamuel.cil.ast.j8 j8} parse trees to
 * {@linkplain com.mikesamuel.cil.ast.jmin jmin} parse trees.
 * <p>
 * This translates a Java 8 parse tree to a minimal java parse tree preserving
 * semantics except for some around reflection.
 * <p>
 * Differences
 * <ul>
 *   <li>There are no anonymous or inner classes.
 *     We assume the FlattenPass has run.</li>
 *   <li>One type declaration per compilation unit.
 *     This property is also assured by the FlattenPass.</li>
 *   <li>No specialized enum instances.
 *     We assume the DespecializeEnumPass has run.
 *     <ul>
 *       <li>Some backends may want to treat <i>enum</i> values as object types
 *         with methods the way idiomatic Java does.
 *       <li>Some may want to treat <i>enum</i> values as symbolic names for
 *         integers the way idiomatic C++ does.
 *       <li>Some, like Go, treat <i>enum</i> values as symbolic names with
 *         methods.
 *     </ul>
 *   <li>Replace reflective operations with ones that lookup into side-tables.
 *     TODO: Inline reflection.
 * </ul>
 */
public final class J8ToJmin {

  final Logger logger;
  final StaticType.TypePool typePool;
  final TypeNodeFactory factory;

  J8ToJmin(Logger logger, StaticType.TypePool typePool) {
    this.logger = logger;
    this.typePool = typePool;
    this.factory = new TypeNodeFactory(logger, typePool);
  }

  /** Translates one compilation unit. */
  public final
  ImmutableList<com.mikesamuel.cil.ast.jmin.CompilationUnitNode> translate(
      ImmutableList<com.mikesamuel.cil.ast.j8.CompilationUnitNode> cus) {
    Translator translator = new Translator();
    for (com.mikesamuel.cil.ast.j8.CompilationUnitNode cu : cus) {
      translator.xlate(cu);
    }

    translator.finish();

    ImmutableList.Builder<com.mikesamuel.cil.ast.jmin.CompilationUnitNode> b
        = ImmutableList.builder();
    for (com.mikesamuel.cil.ast.jmin.TypeDeclarationNode decl
         : translator.getTypeDeclarations()) {
      Optional<com.mikesamuel.cil.ast.jmin.CompilationUnitNode> cuOpt =
          makeCompilationUnit(decl);
      if (cuOpt.isPresent()) {
        b.add(cuOpt.get());
      }
    }
    return b.build();
  }


  private
  Optional<com.mikesamuel.cil.ast.jmin.CompilationUnitNode> makeCompilationUnit(
      com.mikesamuel.cil.ast.jmin.TypeDeclarationNode typeDeclaration) {
    Optional<JminTypeDeclaration> td =
        typeDeclaration.finder(JminTypeDeclaration.class)
        .exclude(JminTypeDeclaration.class)
        .findOne();

    TypeInfo ti = null;
    if (td.isPresent()) {
      ti = td.get().getDeclaredTypeInfo();
    }
    if (ti == null) {
      LogUtils.log(
          logger, Level.SEVERE, typeDeclaration,
          "Missing package metadata for type", null);
      return Optional.absent();
    }

    Preconditions.checkState(
        ti.canonName.parent.type == Name.Type.PACKAGE,
        "Not flat", ti.canonName);
    Name packageName = ti.canonName.parent;
    if (Name.DEFAULT_PACKAGE.equals(packageName)) {
      LogUtils.log(
          logger, Level.SEVERE, td.get(),
          "Cannot translate classes in the default package", null);
      return Optional.absent();
    }

    // Figure out the declarations package and construct a package declaration.
    // Then add the type declaration.
    com.mikesamuel.cil.ast.jmin.PackageDeclarationNode packageDeclaration =
        com.mikesamuel.cil.ast.jmin.PackageDeclarationNode.Variant.Declaration
        .buildNode(toPackage(packageName, typeDeclaration.getSourcePosition()));

    return Optional.of(
        com.mikesamuel.cil.ast.jmin.CompilationUnitNode.Variant
        .PackageDeclarationTypeDeclaration.buildNode(
            packageDeclaration, typeDeclaration));
  }


  enum Mode {
    COLLECT,
    ONCE_MORE_WITH_FEELING,
  }


  final class Translator {
    /** A guess at the node being translated for error reporting. */
    private SourcePosition current;
    private TypeInfo currentDeclaration;
    private final List<com.mikesamuel.cil.ast.jmin.TypeDeclarationNode>
        typeDeclarations = new ArrayList<>();

    void finish() {
      // Done
    }

    void error(Positioned p, String msg) {
      LogUtils.log(
          logger, Level.SEVERE,
          p != null ? p : current,
          msg, null);
    }

    StaticType type(TypeSpecification ts, SourcePosition pos) {
      return typePool.type(ts, pos != null ? pos : current, logger);
    }

    ImmutableList<com.mikesamuel.cil.ast.jmin.TypeDeclarationNode>
        getTypeDeclarations() {
      return ImmutableList.copyOf(typeDeclarations);
    }

    JminBaseNode xlate(J8BaseNode inp) {
      J8BaseNode node8 = before(inp);

      Supplier<ImmutableList<JminBaseNode>> childrenSupplier =
          new Supplier<ImmutableList<JminBaseNode>>() {
        @Override
        public ImmutableList<JminBaseNode> get() {
          ImmutableList.Builder<JminBaseNode> b =
              ImmutableList.builder();
          for (int i = 0, n = node8.getNChildren(); i < n; ++i) {
            JminBaseNode xlatedChild = xlate(node8.getChild(i));
            if (xlatedChild != null) {
              b.add(xlatedChild);
            }
          }
          return b.build();
        }
      };

      J8NodeVariant v8 = node8.getVariant();

      JminBaseNode nodem;

      Function<ImmutableList<JminBaseNode>, JminBaseNode> defaultBuilder
          = new Function<ImmutableList<JminBaseNode>, JminBaseNode>() {

            @Override
            public JminBaseNode apply(
                ImmutableList<JminBaseNode> xlatedChildren) {
              JminNodeVariant vm = VARIANT_MAP.get(v8);
              if (vm != null) {
                if (node8 instanceof J8BaseLeafNode) {
                  Preconditions.checkState(xlatedChildren.isEmpty());
                  return vm.buildNode(node8.getValue());
                } else {
                  return vm.buildNode(xlatedChildren);
                }
              } else {
                throw new Error("No builder for " + v8);
              }
            }
          };

      Xlater xlater = XLATERS.get(v8);
      if (xlater == null) {
        xlater = USE_DEFAULT_BUILDER;
      }
      nodem = xlater.xlate(this, node8, childrenSupplier, defaultBuilder);

      if (nodem != null) {
        nodem.copyMetadataFrom(node8);
      }
      if (nodem instanceof JminTypeDeclaration) {
        switch (nodem.getNodeType()) {
          case NormalClassDeclaration:
            this.typeDeclarations.add(
                com.mikesamuel.cil.ast.jmin.TypeDeclarationNode
                .Variant.ClassDeclaration
                .buildNode(
                    com.mikesamuel.cil.ast.jmin.ClassDeclarationNode
                    .Variant.NormalClassDeclaration
                    .buildNode(nodem)));
            break;
          case EnumDeclaration:
            this.typeDeclarations.add(
                com.mikesamuel.cil.ast.jmin.TypeDeclarationNode
                .Variant.ClassDeclaration
                .buildNode(
                    com.mikesamuel.cil.ast.jmin.ClassDeclarationNode
                    .Variant.EnumDeclaration
                    .buildNode(nodem)));
            break;
          case NormalInterfaceDeclaration:
            this.typeDeclarations.add(
                com.mikesamuel.cil.ast.jmin.TypeDeclarationNode
                .Variant.InterfaceDeclaration
                .buildNode(
                    com.mikesamuel.cil.ast.jmin.InterfaceDeclarationNode
                    .Variant.NormalInterfaceDeclaration
                    .buildNode(nodem)));
            break;
          case TypeParameter:
            break;
          default:
            throw new AssertionError(nodem);
        }
      }
      return after(node8, nodem);
    }

    J8BaseNode before(J8BaseNode node8) {
      SourcePosition pos = node8.getSourcePosition();
      if (pos != null) { current = pos; }
      if (node8 instanceof TypeDeclaration) {
        J8TypeDeclaration td8 = (J8TypeDeclaration) node8;
        TypeInfo ti = td8.getDeclaredTypeInfo();
        if (ti != null && ti.canonName.type == Name.Type.CLASS) {
          if (this.currentDeclaration != null) {
            LogUtils.log(
                logger, Level.SEVERE, node8,
                "Unexpected nested type declaration."
                + "  Did you run the flatten pass?",
                null);
          }
          this.currentDeclaration = ti;
        }
      }
      return node8;
    }

    JminBaseNode after(J8BaseNode node8, JminBaseNode nodem) {
      if (node8 instanceof TypeDeclaration) {
        TypeInfo ti = ((TypeDeclaration<?, ?, ?>) node8).getDeclaredTypeInfo();
        if (ti != null && ti.canonName.type == Name.Type.CLASS) {
          this.currentDeclaration = null;
        }
      }
      return nodem;
    }

    J8ToJmin getMinner() {
      return J8ToJmin.this;
    }

    TypeInfo getCurrentDeclaration() {
      return currentDeclaration;
    }

    MinTypeNodeFactory getTypeNodeFactory(Positioned pos) {
      return new MinTypeNodeFactory(logger, typePool, pos);
    }
  }

  static abstract class Xlater {
    abstract @Nullable
    JminBaseNode xlate(
        Translator xlator,
        J8BaseNode n8,
        Supplier<ImmutableList<JminBaseNode>> childrenSupplier,
        Function<ImmutableList<JminBaseNode>, JminBaseNode> builder);
  }

  static abstract class SimpleXlater extends Xlater {
    abstract @Nullable
    JminBaseNode xlate(
        Translator xlator,
        J8BaseNode n8,
        Supplier<ImmutableList<JminBaseNode>> childrenSupplier);

    final @Nullable @Override
    JminBaseNode xlate(
        Translator xlator,
        J8BaseNode n8,
        Supplier<ImmutableList<JminBaseNode>> childrenSupplier,
        Function<ImmutableList<JminBaseNode>, JminBaseNode> builder) {
      return xlate(xlator, n8, childrenSupplier);
    }
}

  static final Xlater IGNORE_XLATER = new Xlater() {

    @Override
    public @Nullable JminBaseNode xlate(
        Translator xlator,
        J8BaseNode n8, Supplier<ImmutableList<JminBaseNode>> children,
        Function<ImmutableList<JminBaseNode>, JminBaseNode> defaultBuilder) {
      return null;
    }

  };

  static final Xlater USE_DEFAULT_BUILDER = new Xlater() {

    @Override
    public JminBaseNode xlate(
        Translator xlator, J8BaseNode n8,
        Supplier<ImmutableList<JminBaseNode>> childrenSupplier,
        Function<ImmutableList<JminBaseNode>, JminBaseNode> builder) {
      return builder.apply(childrenSupplier.get());
    }

  };


  static final Xlater DROP_UNLESS_HAS_CHILDREN = new Xlater() {

    @Override
    public JminBaseNode xlate(
        Translator xlator, J8BaseNode n8,
        Supplier<ImmutableList<JminBaseNode>> childrenSupplier,
        Function<ImmutableList<JminBaseNode>, JminBaseNode> defaultBuilder) {
      ImmutableList<JminBaseNode> children = childrenSupplier.get();
      if (children.isEmpty()) { return null; }
      return defaultBuilder.apply(children);
    }

  };

  static final Xlater EVALUATE_AND_DROP = new Xlater() {

    @Override
    public JminBaseNode xlate(
        Translator xlator, J8BaseNode n8,
        Supplier<ImmutableList<JminBaseNode>> childrenSupplier,
        Function<ImmutableList<JminBaseNode>, JminBaseNode> defaultBuilder) {
      childrenSupplier.get();
      return null;
    }

  };

  static final
  ImmutableMap<J8NodeVariant, Xlater> XLATERS =
      ImmutableMap.<J8NodeVariant, Xlater>builder()
      .put(
          com.mikesamuel.cil.ast.j8.AnnotationNode.Variant.MarkerAnnotation,
          new SimpleXlater() {

            @Override
            public JminBaseNode xlate(
                Translator xlator, J8BaseNode n8,
                Supplier<ImmutableList<JminBaseNode>> childrenSupplier) {
              ImmutableList<JminBaseNode> children = childrenSupplier.get();
              return com.mikesamuel.cil.ast.jmin.AnnotationNode.Variant
                  .NormalAnnotation.buildNode(children);
            }

          })
      .put(
          com.mikesamuel.cil.ast.j8.
          AnnotationNode.Variant.SingleElementAnnotation,
          new SimpleXlater() {

            @Override
            public JminBaseNode xlate(
                Translator xlator, J8BaseNode n8,
                Supplier<ImmutableList<JminBaseNode>> childrenSupplier) {
              ImmutableList<JminBaseNode> children = childrenSupplier.get();
              return com.mikesamuel.cil.ast.jmin.AnnotationNode.Variant
                  .NormalAnnotation.buildNode(children);
            }
          })
      .put(   // TODO: is this necessary not that FlattenPass is its own pass
          com.mikesamuel.cil.ast.j8.
          ClassMemberDeclarationNode.Variant.ClassDeclaration,
          // The controller will capture the type declarations so that
          // flat types translated from inner types are given their own
          // compilation unit.
          EVALUATE_AND_DROP)
      .put(   // TODO: is this necessary not that FlattenPass is its own pass
          com.mikesamuel.cil.ast.j8.
          ClassMemberDeclarationNode.Variant.InterfaceDeclaration,
          // The controller will capture the type declarations so that
          // flat types translated from inner types are given their own
          // compilation unit.
          EVALUATE_AND_DROP)
      .put(
          com.mikesamuel.cil.ast.j8.
          ClassMemberDeclarationNode.Variant.Sem,
          IGNORE_XLATER)
      .put(
          com.mikesamuel.cil.ast.j8
          .ClassOrInterfaceTypeNode.Variant
          .ClassOrInterfaceTypeDotAnnotationIdentifierTypeArguments,
          new SimpleXlater() {

            @Override
            public JminBaseNode xlate(
                Translator xlator, J8BaseNode n8,
                Supplier<ImmutableList<JminBaseNode>> children) {
              com.mikesamuel.cil.ast.j8.ClassOrInterfaceTypeNode t8 =
                  (com.mikesamuel.cil.ast.j8.ClassOrInterfaceTypeNode) n8;
              StaticType t = t8.getStaticType();
              if (!(t instanceof StaticType.TypePool.ClassOrInterfaceType)) {
                xlator.error(
                    n8,
                    t == null
                    ? "Missing type information for " + t8
                    : t + " is not a class or interface type");
                t = xlator.type(
                    JavaLang.JAVA_LANG_OBJECT, n8.getSourcePosition());
              }
              MinTypeNodeFactory factory = xlator.getTypeNodeFactory(n8);
              return factory.toClassOrInterfaceType(t.typeSpecification);
            }

          })
      .put(
          com.mikesamuel.cil.ast.j8
          .ConstructorBodyNode.Variant
          .LcExplicitConstructorInvocationBlockStatementsRc,
          new SimpleXlater() {

            @Override
            public JminBaseNode xlate(
                Translator xlator,
                J8BaseNode n8, Supplier<ImmutableList<JminBaseNode>> children) {
              // Make sure there is an explicit constructor invocation.
              com.mikesamuel.cil.ast.jmin.ExplicitConstructorInvocationNode inv
                  = null;
              com.mikesamuel.cil.ast.jmin.BlockStatementsNode stmts = null;
              for (JminBaseNode child : children.get()) {
                switch (child.getNodeType()) {
                  case ExplicitConstructorInvocation:
                    inv = (com.mikesamuel.cil.ast.jmin
                           .ExplicitConstructorInvocationNode) child;
                    break;
                  case BlockStatements:
                    stmts = (com.mikesamuel.cil.ast.jmin
                             .BlockStatementsNode) child;
                    break;
                  default:
                    throw new AssertionError(child);
                }
              }
              if (inv == null) {
                inv = com.mikesamuel.cil.ast.jmin
                    .ExplicitConstructorInvocationNode
                    .Variant
                    .TypeArgumentsSuperLpArgumentListRpSem
                    .buildNode();
              }
              ImmutableList.Builder<JminBaseNode> b = ImmutableList.builder();
              b.add(inv);
              if (stmts != null) { b.add(stmts); }
              return com.mikesamuel.cil.ast.jmin.ConstructorBodyNode.Variant
                  .LcExplicitConstructorInvocationBlockStatementsRc
                  .buildNode(b.build());
            }

          })
      .put(
          com.mikesamuel.cil.ast.j8
          .CompilationUnitNode.Variant
          .PackageDeclarationImportDeclarationTypeDeclaration,
          new SimpleXlater() {

            @Override
            public JminBaseNode xlate(
                Translator xlator,
                J8BaseNode n8, Supplier<ImmutableList<JminBaseNode>> children) {
              // Filter out imports.
              ImmutableList<JminBaseNode> filteredChildren =
                  ImmutableList.copyOf(
                      Iterables.filter(
                          children.get(),
                          nodeTypeIn(
                              JminNodeType.PackageDeclaration,
                              JminNodeType.TypeDeclaration)));
              return com.mikesamuel.cil.ast.jmin.CompilationUnitNode.Variant
                  .PackageDeclarationTypeDeclaration.buildNode(
                      filteredChildren);
            }

          })
      .put(
          com.mikesamuel.cil.ast.j8.EnumConstantNode.Variant.Declaration,
          new SimpleXlater() {

            @Override
            public JminBaseNode xlate(
                Translator xlator, J8BaseNode n8,
                Supplier<ImmutableList<JminBaseNode>> childrenSupplier) {
              Iterable<JminBaseNode> children = childrenSupplier.get();
              children = Iterables.filter(
                  children,
                  Predicates.not(nodeTypeIn(JminNodeType.ClassBody)));
              return com.mikesamuel.cil.ast.jmin.EnumConstantNode
                  .Variant.Declaration.buildNode(children);
            }

          })
      .put(
          com.mikesamuel.cil.ast.j8.ExplicitConstructorInvocationNode.Variant
          .PrimaryDotTypeArgumentsSuperLpArgumentListRpSem,
          new SimpleXlater() {

            @Override
            public JminBaseNode xlate(
                Translator xlator, J8BaseNode n8,
                Supplier<ImmutableList<JminBaseNode>> childrenSupplier) {
              xlator.error(
                  n8, "Qualified super constructor call not flattened");
              return com.mikesamuel.cil.ast.jmin
                  .ExplicitConstructorInvocationNode.Variant
                  .TypeArgumentsSuperLpArgumentListRpSem.buildNode();
            }
          })
      .put(
          com.mikesamuel.cil.ast.j8.ExpressionAtomNode.Variant.Super,
          new SimpleXlater() {

            @Override
            public JminBaseNode xlate(Translator xlator, J8BaseNode n8,
                Supplier<ImmutableList<JminBaseNode>> childrenSupplier) {
              com.mikesamuel.cil.ast.j8.TypeNameNode outerType =
                  n8.firstChildWithType(
                      com.mikesamuel.cil.ast.j8.TypeNameNode.class);
              if (outerType != null) {
                TypeInfo ti = outerType.getReferencedTypeInfo();
                TypeInfo cd = xlator.getCurrentDeclaration();
                if (ti == null) {
                  xlator.error(
                      n8,
                      "Missing type info for qualified `super`: "
                      + outerType.getTextContent("."));
                } else if (cd == null || !ti.canonName.equals(cd.canonName)) {
                  xlator.error(
                      n8,
                      "Qualified `super` out of scope: "
                      + outerType.getTextContent("."));
                }
              }
              return com.mikesamuel.cil.ast.jmin.ExpressionAtomNode.Variant
                  .Super.buildNode();
            }

          })
      .put(
          com.mikesamuel.cil.ast.j8.ExpressionAtomNode.Variant.This,
          new SimpleXlater() {

            @Override
            public JminBaseNode xlate(Translator xlator, J8BaseNode n8,
                Supplier<ImmutableList<JminBaseNode>> childrenSupplier) {
              com.mikesamuel.cil.ast.j8.TypeNameNode outerType =
                  n8.firstChildWithType(
                      com.mikesamuel.cil.ast.j8.TypeNameNode.class);
              if (outerType != null) {
                TypeInfo ti = outerType.getReferencedTypeInfo();
                TypeInfo cd = xlator.getCurrentDeclaration();
                if (ti == null) {
                  xlator.error(
                      n8,
                      "Missing type info for qualified `this`: "
                      + outerType.getTextContent("."));
                } else if (cd == null || !ti.canonName.equals(cd.canonName)) {
                  xlator.error(
                      n8,
                      "Qualified `this` out of scope: "
                      + outerType.getTextContent("."));
                }
              }
              return com.mikesamuel.cil.ast.jmin.ExpressionAtomNode.Variant
                  .This.buildNode();
            }

          })
      .put(
          com.mikesamuel.cil.ast.j8
          .FormalParameterListNode.Variant
          .FormalParametersComLastFormalParameter,
          new SimpleXlater() {

            @Override
            public JminBaseNode xlate(
                Translator xlator, J8BaseNode n8,
                Supplier<ImmutableList<JminBaseNode>> childrenSupplier) {
              ImmutableList<JminBaseNode> children = childrenSupplier.get();
              ImmutableList.Builder<JminBaseNode> formals
                  = ImmutableList.builder();
              for (JminBaseNode child : children) {
                JminNodeType nt = child.getNodeType();
                if (nt == JminNodeType.FormalParameterList) {
                  // Receiver parameters are dropped elsewhere,
                  // variadiac parameters are retyped elsewhere,
                  // but the FormalParameters intermediate list is rewrapped
                  // as a FormalParameterListNode so we unpack it here.
                  formals.addAll(child.getChildren());
                } else {
                  formals.add(child);
                }
              }
              return com.mikesamuel.cil.ast.jmin.FormalParameterListNode
                  .Variant.FormalParameterComFormalParameter
                  .buildNode(formals.build());
            }

          })
      .put(
          com.mikesamuel.cil.ast.j8
          .FormalParameterListNode.Variant.LastFormalParameter,
          new SimpleXlater() {

            @Override
            public JminBaseNode xlate(Translator xlator, J8BaseNode n8,
                Supplier<ImmutableList<JminBaseNode>> childrenSupplier) {
              return com.mikesamuel.cil.ast.jmin.FormalParameterListNode
                  .Variant.FormalParameterComFormalParameter
                  .buildNode(childrenSupplier.get());
            }

          })
      .put(
          com.mikesamuel.cil.ast.j8
          .FormalParametersNode.Variant.FormalParameterComFormalParameter,
          new SimpleXlater() {

            @Override
            public JminBaseNode xlate(
                Translator xlator, J8BaseNode n8,
                Supplier<ImmutableList<JminBaseNode>> childrenSupplier) {
              return com.mikesamuel.cil.ast.jmin.FormalParameterListNode
                  .Variant.FormalParameterComFormalParameter
                  .buildNode(childrenSupplier.get());
            }

          })
      .put(
          com.mikesamuel.cil.ast.j8
          .FormalParametersNode.Variant.ReceiverParameterComFormalParameter,
          new SimpleXlater() {

            @Override
            public JminBaseNode xlate(
                Translator xlator, J8BaseNode n8,
                Supplier<ImmutableList<JminBaseNode>> childrenSupplier) {
              ImmutableList<JminBaseNode> children = childrenSupplier.get();
              if (children.isEmpty()) { return null; }
              return com.mikesamuel.cil.ast.jmin.FormalParameterListNode
                  .Variant.FormalParameterComFormalParameter
                  .buildNode(children);
            }

          })
      .put(
          com.mikesamuel.cil.ast.j8.IfStatementNode.Variant
          .IfLpExpressionRpStatementNotElse,
          new SimpleXlater() {

            @Override
            public JminBaseNode xlate(
                Translator xlator, J8BaseNode n8,
                Supplier<ImmutableList<JminBaseNode>> childrenSupplier) {
              ImmutableList<JminBaseNode> children = childrenSupplier.get();
              return com.mikesamuel.cil.ast.jmin.IfStatementNode.Variant
                  .IfLpExpressionRpStatementElseStatement.buildNode(
                      Iterables.concat(
                          children,
                          ImmutableList.of(
                              com.mikesamuel.cil.ast.jmin.StatementNode.Variant
                              .Block.buildNode(
                                  com.mikesamuel.cil.ast.jmin.BlockNode.Variant
                                  .LcBlockStatementsRc.buildNode()))));
            }

          })
      .put(
          com.mikesamuel.cil.ast.j8
          .ImportDeclarationNode.Variant.SingleTypeImportDeclaration,
           IGNORE_XLATER)
      .put(
          com.mikesamuel.cil.ast.j8
          .ImportDeclarationNode.Variant.TypeImportOnDemandDeclaration,
          IGNORE_XLATER)
      .put(
          com.mikesamuel.cil.ast.j8
          .ImportDeclarationNode.Variant.SingleStaticImportDeclaration,
          IGNORE_XLATER)
      .put(
          com.mikesamuel.cil.ast.j8
          .ImportDeclarationNode.Variant.StaticImportOnDemandDeclaration,
          IGNORE_XLATER)
      .put(
          com.mikesamuel.cil.ast.j8.
          InterfaceMemberDeclarationNode.Variant.ClassDeclaration,
          // The controller will capture the type declarations so that
          // flat types translated from inner types are given their own
          // compilation unit.
          EVALUATE_AND_DROP)
      .put(
          com.mikesamuel.cil.ast.j8.
          InterfaceMemberDeclarationNode.Variant.InterfaceDeclaration,
          // The controller will capture the type declarations so that
          // flat types translated from inner types are given their own
          // compilation unit.
          EVALUATE_AND_DROP)
      .put(
          com.mikesamuel.cil.ast.j8.
          InterfaceMemberDeclarationNode.Variant.Sem,
          IGNORE_XLATER)
      .put(
          com.mikesamuel.cil.ast.j8
          .LastFormalParameterNode.Variant.FormalParameter,
          new SimpleXlater() {

            @Override
            public JminBaseNode xlate(Translator xlator, J8BaseNode n8,
                Supplier<ImmutableList<JminBaseNode>> childrenSupplier) {
              ImmutableList<JminBaseNode> children = childrenSupplier.get();
              Preconditions.checkState(children.size() == 1);
              return children.get(0);
            }

          })
      .put(
          com.mikesamuel.cil.ast.j8
          .LastFormalParameterNode.Variant.Variadic,
          new SimpleXlater() {

            @Override
            public JminBaseNode xlate(Translator xlator, J8BaseNode n8,
                Supplier<ImmutableList<JminBaseNode>> childrenSupplier) {
              throw new Error("TODO");  // TODO
            }

          })
      .put(
          com.mikesamuel.cil.ast.j8.MarkerAnnotationNode.Variant.AtTypeName,
          new SimpleXlater() {

            @Override
            public JminBaseNode xlate(
                Translator xlator, J8BaseNode n8,
                Supplier<ImmutableList<JminBaseNode>> childrenSupplier) {
              return com.mikesamuel.cil.ast.jmin.NormalAnnotationNode.Variant
                  .AtTypeNameLpElementValuePairListRp
                  .buildNode(childrenSupplier.get());
            }

          })
      .put(
          com.mikesamuel.cil.ast.j8
          .MethodDeclaratorNode.Variant.MethodNameLpFormalParameterListRpDims,
          new SimpleXlater() {

            @Override
            public JminBaseNode xlate(Translator xlator, J8BaseNode n8,
                Supplier<ImmutableList<JminBaseNode>> childrenSupplier) {
              return com.mikesamuel.cil.ast.jmin
                  .MethodDeclaratorNode.Variant.MethodNameLpFormalParameterListRp
                  .buildNode(childrenSupplier.get());
            }

          })
      .put(
          com.mikesamuel.cil.ast.j8
          .NormalClassDeclarationNode.Variant.Declaration,
          new SimpleXlater() {

            @Override
            public JminBaseNode xlate(
                Translator xlator, J8BaseNode n8,
                Supplier<ImmutableList<JminBaseNode>> childrenSupplier) {
              ImmutableList<JminBaseNode> children = childrenSupplier.get();
              for (int i = 0, n = children.size(); i < n; ++i) {
                JminNodeType nt = children.get(i).getNodeType();
                if (nt == JminNodeType.Superclass) { break; }
                if (nt != JminNodeType.JavaDocComment
                    && nt != JminNodeType.Modifier
                    && nt != JminNodeType.SimpleTypeName
                    && nt != JminNodeType.TypeParameters) {
                  MinTypeNodeFactory factory = xlator.getTypeNodeFactory(n8);
                  com.mikesamuel.cil.ast.jmin.SuperclassNode extendsObject =
                      com.mikesamuel.cil.ast.jmin.SuperclassNode.Variant
                      .ExtendsClassType.buildNode(
                          factory.toClassType(JavaLang.JAVA_LANG_OBJECT));
                  children = ImmutableList.<JminBaseNode>builder()
                      .addAll(children.subList(0, i))
                      .add(extendsObject)
                      .addAll(children.subList(i, n))
                      .build();
                  break;
                }
              }
              return com.mikesamuel.cil.ast.jmin.NormalClassDeclarationNode
                  .Variant.Declaration.buildNode(children);
            }

          })
      .put(
           com.mikesamuel.cil.ast.j8.
           PackageDeclarationNode.Variant.Declaration,
           new SimpleXlater() {

             @Override
             public JminBaseNode xlate(
                 Translator xlator, J8BaseNode n8,
                 Supplier<ImmutableList<JminBaseNode>> children) {
               return com.mikesamuel.cil.ast.jmin.
                   PackageDeclarationNode.Variant.Declaration.buildNode(
                       Iterables.filter(
                           children.get(),
                           nodeTypeIn(JminNodeType.PackageName)));
             }

          })
      .put(
          com.mikesamuel.cil.ast.j8.ReceiverParameterNode.Variant
          .AnnotationUnannTypeSimpleTypeNameDotThis,
          IGNORE_XLATER)
      .put(
          com.mikesamuel.cil.ast.j8.SingleElementAnnotationNode.Variant
          .AtTypeNameLpElementValueRp,
          new SimpleXlater() {

            @Override
            public JminBaseNode xlate(
                Translator xlator, J8BaseNode n8,
                Supplier<ImmutableList<JminBaseNode>> children) {
              ImmutableList.Builder<JminBaseNode> b = ImmutableList.builder();
              com.mikesamuel.cil.ast.jmin.TypeNameNode annotName = null;
              for (JminBaseNode c : children.get()) {
                if (c instanceof com.mikesamuel.cil.ast.jmin.TypeNameNode) {
                  annotName = (com.mikesamuel.cil.ast.jmin.TypeNameNode) c;
                }
                if (c instanceof com.mikesamuel.cil.ast.jmin.ElementValueNode) {
                  TypeInfo ati = annotName != null
                      ? annotName.getReferencedTypeInfo()
                      : null;
                  if (ati == null) {
                    xlator.error(n8, "Missing type info for annotation name");
                    continue;
                  }
                  com.mikesamuel.cil.ast.jmin.ElementValueNode evn =
                      (com.mikesamuel.cil.ast.jmin.ElementValueNode) c;
                  Name elementName = null;
                  for (MemberInfo mi : ati.getDeclaredMembers()) {
                    if (Modifier.isPublic(mi.modifiers)
                        && !Modifier.isStatic(mi.modifiers)
                        && mi instanceof CallableInfo) {
                      CallableInfo ci = (CallableInfo) mi;
                      MethodDescriptor md = ci.getDescriptor();
                      if (md != null && md.formalTypes.isEmpty()) {
                        elementName = mi.canonName;
                        break;
                      }
                    }
                  }
                  if (elementName == null) {
                    xlator.error(n8, "Cannot find element name");
                    continue;
                  }
                  com.mikesamuel.cil.ast.jmin.IdentifierNode ident =
                      MinTypeNodeFactory.toIdentifier(elementName);
                  com.mikesamuel.cil.ast.jmin.ElementValuePairNode pair =
                      com.mikesamuel.cil.ast.jmin.ElementValuePairNode.Variant
                      .IdentifierEqElementValue.buildNode(
                          ident, evn);
                  com.mikesamuel.cil.ast.jmin.ElementValuePairListNode pairList =
                      com.mikesamuel.cil.ast.jmin.ElementValuePairListNode.Variant
                      .ElementValuePairComElementValuePair.buildNode(pair);
                  b.add(pairList);
                } else {
                  b.add(c);
                }
              }

              return com.mikesamuel.cil.ast.jmin.NormalAnnotationNode.Variant
                  .AtTypeNameLpElementValuePairListRp.buildNode(b.build());
            }

          })
      .put(
          com.mikesamuel.cil.ast.j8
          .TypeDeclarationNode.Variant.Sem,
          IGNORE_XLATER)
      .put(
          com.mikesamuel.cil.ast.j8
          .VariableDeclaratorIdNode.Variant.IdentifierDims,
          new SimpleXlater() {

            @Override
            public JminBaseNode xlate(
                Translator xlator, J8BaseNode n8,
                Supplier<ImmutableList<JminBaseNode>> childrenSupplier) {
              ImmutableList<JminBaseNode> children = childrenSupplier.get();
              com.mikesamuel.cil.ast.jmin.IdentifierNode ident =
                  (com.mikesamuel.cil.ast.jmin.IdentifierNode) children.get(0);
              if (children.size() != 1) {
                xlator.error(
                    n8, "Trailing [] in variable declaration."
                    + "  Did you forget to run the defragment types pass?");
              }

              return com.mikesamuel.cil.ast.jmin.VariableDeclaratorIdNode
                  .Variant.Identifier.buildNode(ident);
            }

          })
      .build();  // TODO

  static com.mikesamuel.cil.ast.jmin.PackageNameNode toPackage(
      Name pkg, SourcePosition pos) {
    ImmutableList.Builder<com.mikesamuel.cil.ast.jmin.IdentifierNode> b
        = ImmutableList.builder();
    for (Name nm = pkg; !Name.DEFAULT_PACKAGE.equals(nm); nm = nm.parent) {
      Preconditions.checkNotNull(nm);
      com.mikesamuel.cil.ast.jmin.IdentifierNode ident = toIdent(nm);
      ident.setSourcePosition(pos);
      b.add(ident);
    }
    return com.mikesamuel.cil.ast.jmin.PackageNameNode.Variant
        .IdentifierDotIdentifier
        .buildNode(b.build().reverse());
  }

  static com.mikesamuel.cil.ast.jmin.IdentifierNode toIdent(Name nm) {
    return com.mikesamuel.cil.ast.jmin.IdentifierNode.Variant.Builtin
        .buildNode(nm.identifier)
        .setNamePartType(nm.type);
  }

  static final ImmutableMap<J8NodeVariant, JminNodeVariant> VARIANT_MAP;
  static {
    ImmutableMap.Builder<J8NodeVariant, JminNodeVariant> b =
        ImmutableMap.builder();

    Map<String, JminNodeType> jminNodeTypes = Maps.newHashMap();
    for (JminNodeType tmin : JminNodeType.values()) {
      JminNodeType dupe = jminNodeTypes.put(tmin.name(), tmin);
      Preconditions.checkState(dupe == null);
    }

    Map<String, JminNodeVariant> jminNodeVariants = Maps.newHashMap();
    for (J8NodeType t8 : J8NodeType.values()) {
      JminNodeType tmin = jminNodeTypes.get(t8.name());
      if (tmin == null) {
        // TODO: check t8 against whitelist of handled
        continue;
      }
      jminNodeVariants.clear();
      for (Enum<? extends JminNodeVariant> vmin
           : tmin.getVariantType().getEnumConstants()) {
        JminNodeVariant dupe = jminNodeVariants
            .put(vmin.name(), (JminNodeVariant) vmin);
        Preconditions.checkState(dupe == null);
      }

      StringBuilder sb8 = new StringBuilder();
      StringBuilder sbmin = new StringBuilder();
      for (Enum<? extends J8NodeVariant> v8e
           : t8.getVariantType().getEnumConstants()) {
        JminNodeVariant vmin = jminNodeVariants.get(v8e.name());
        if (vmin == null) {
          // TODO: check v8 against whitelist of handled
          continue;
        }

        J8NodeVariant v8 = (J8NodeVariant) v8e;
        sb8.setLength(0);
        v8.getParSer().appendShallowStructure(sb8);
        sbmin.setLength(0);
        vmin.getParSer().appendShallowStructure(sbmin);

        if (sb8.toString().equals(sbmin.toString())) {
          b.put(v8, vmin);
        } else {
          // TODO: check v8 against whitelist of handled
          continue;
        }
      }
    }
    VARIANT_MAP = b.build();
  }

  static Predicate<JminBaseNode> nodeTypeIn(
      JminNodeType t, JminNodeType... ts) {
    return new Predicate<JminBaseNode>() {

      EnumSet<JminNodeType> types = EnumSet.of(t, ts);

      @Override
      public boolean apply(JminBaseNode nmin) {
        return types.contains(nmin.getNodeType());
      }
    };
  }

  /**
   * Information about the scope in which we're xlating fragments.
   */
  static final class Scope {
    final J8TypeDeclaration typeDecl;
    final Name name;
    final NameAllocator nameAllocator;

    Scope(J8TypeDeclaration typeDecl, Name name, TypeInfoResolver r) {
      this.typeDecl = typeDecl;
      this.name = name;
      this.nameAllocator = NameAllocator.create(
          ImmutableList.of(typeDecl), Predicates.alwaysFalse(), r);
    }
  }
}
