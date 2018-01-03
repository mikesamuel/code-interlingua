package com.mikesamuel.cil.ast.passes.flatten;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Nullable;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.mikesamuel.cil.ast.j8.AnnotationNode;
import com.mikesamuel.cil.ast.j8.ClassOrInterfaceTypeNode;
import com.mikesamuel.cil.ast.j8.ClassOrInterfaceTypeToInstantiateNode;
import com.mikesamuel.cil.ast.j8.IdentifierNode;
import com.mikesamuel.cil.ast.j8.J8BaseNode;
import com.mikesamuel.cil.ast.j8.J8NodeType;
import com.mikesamuel.cil.ast.j8.J8TypeReference;
import com.mikesamuel.cil.ast.j8.J8WholeType;
import com.mikesamuel.cil.ast.j8.ReferenceTypeNode;
import com.mikesamuel.cil.ast.j8.TypeArgumentListNode;
import com.mikesamuel.cil.ast.j8.TypeArgumentNode;
import com.mikesamuel.cil.ast.j8.TypeArgumentsNode;
import com.mikesamuel.cil.ast.j8.UnqualifiedClassInstanceCreationExpressionNode;
import com.mikesamuel.cil.ast.j8.WildcardBoundsNode;
import com.mikesamuel.cil.ast.meta.CallableInfo;
import com.mikesamuel.cil.ast.meta.JavaLang;
import com.mikesamuel.cil.ast.meta.MethodTypeContainer;
import com.mikesamuel.cil.ast.meta.Name;
import com.mikesamuel.cil.ast.meta.PackageSpecification;
import com.mikesamuel.cil.ast.meta.PartialTypeSpecification;
import com.mikesamuel.cil.ast.meta.StaticType;
import com.mikesamuel.cil.ast.meta.StaticType.TypePool;
import com.mikesamuel.cil.ast.meta.TypeInfo;
import com.mikesamuel.cil.ast.meta.TypeSpecification;
import com.mikesamuel.cil.ast.meta.TypeSpecification.TypeBinding;
import com.mikesamuel.cil.ast.meta.TypeSpecification.Variance;
import com.mikesamuel.cil.ast.passes.TypeNodeFactory;
import com.mikesamuel.cil.ast.passes.flatten.PassState.FlatteningType;
import com.mikesamuel.cil.parser.Positioned;
import com.mikesamuel.cil.parser.SList;
import com.mikesamuel.cil.util.LogUtils;

final class FlattenNamesMiniPass {
  final Logger logger;
  final TypePool pool;
  final TypeNodeFactory factory;

  FlattenNamesMiniPass(Logger logger, TypePool pool) {
    this.logger = logger;
    this.pool = pool;
    this.factory = new TypeNodeFactory(logger, pool);
  }

  void run(PassState ps) {
    for (PassState.FlatteningType ft : ps.inProcessOrder) {
      new TypeArgumentsRewriter(ps, ft).visit();
    }
  }

  private final Map<BName, ImmutableMap<BName, TypeBinding>>
      typeBindingsFromScope = new LinkedHashMap<>();
  private Map<BName, TypeBinding> typeBindingsFromScope(
      PassState.FlatteningType ft) {
    ImmutableMap<BName, TypeBinding> im = typeBindingsFromScope.get(
        ft.bumpyName);
    Set<Name> visited = new HashSet<>();
    if (im == null) {
      Map<BName, TypeBinding> m = new LinkedHashMap<>();
      storeBindingsFromScope(ft.typeInfo, m, visited);
      im = ImmutableMap.copyOf(m);
      typeBindingsFromScope.put(ft.bumpyName, im);
    }
    return im;
  }

  private void storeBindingsFromScope(
      TypeInfo ti, Map<BName, TypeBinding> m, Set<Name> visited) {
    if (!visited.add(ti.canonName)) { return; }
    for (TypeSpecification st
        : Iterables.concat(ti.superType.asSet(), ti.interfaces)) {
      for (PartialTypeSpecification pst = st; pst != null; pst = pst.parent()) {
        ImmutableList<TypeBinding> bs = pst.bindings();
        ImmutableList<Name> ps = parametersFor(pst.getRawName());
        if (bs.size() == ps.size()) {
          for (int i = 0, n = bs.size(); i < n; ++i) {
            m.put(
                BName.of(ps.get(i)),
                bs.get(i).subst(
                    new Function<Name, TypeBinding>() {
                      @Override public TypeBinding apply(Name nm) {
                        return m.get(BName.of(nm));
                      }
                    }));
          }
        }
      }
      Optional<TypeInfo> sti = pool.r.resolve(st.rawName);
      if (sti.isPresent()) {
        storeBindingsFromScope(sti.get(), m, visited);
      }
    }
  }

  private ImmutableList<Name> parametersFor(Name nm) {
    if (nm.type.isType) {
      Optional<TypeInfo> ti = pool.r.resolve(nm);
      if (ti.isPresent()) {
        return ti.get().parameters;
      }
    } else if (nm.type == Name.Type.METHOD) {
      Optional<TypeInfo> ti = pool.r.resolve(nm.getContainingClass());
      if (ti.isPresent()) {
        Optional<CallableInfo> ci = ti.get().declaredCallableNamed(nm);
        if (ci.isPresent()) {
          return ci.get().typeParameters;
        }
      }
    }
    return ImmutableList.of();
  }

  final class TypeArgumentsRewriter extends SingleTypeRewriter {

    TypeArgumentsRewriter(PassState ps, PassState.FlatteningType ft) {
      super(FlattenNamesMiniPass.this.logger, ps, ft);
    }

    @Override
    protected ProcessingStatus previsit(
        J8BaseNode node, @Nullable SList<Parent> pathFromRoot) {
      J8NodeType nt = node.getNodeType();
      if (!isRewritableTypeNode(node)) {
        return ProcessingStatus.CONTINUE;
      }
      BName anonymousTypeImplementationClass = null;
      if (nt == J8NodeType.ClassOrInterfaceType
          && pathFromRoot != null && pathFromRoot.prev != null
          && pathFromRoot.x.parent
            instanceof ClassOrInterfaceTypeToInstantiateNode
          && pathFromRoot.prev.x.parent
            instanceof UnqualifiedClassInstanceCreationExpressionNode) {
        UnqualifiedClassInstanceCreationExpressionNode newCall =
            (UnqualifiedClassInstanceCreationExpressionNode)
            pathFromRoot.prev.x.parent;
        // Fixup anonymous class declarations.
        ClassOrInterfaceTypeNode cit = (ClassOrInterfaceTypeNode) node;
        TypeInfo dti = newCall.getDeclaredTypeInfo();
        StaticType sti = cit.getStaticType();
        if (dti != null && sti != null
            && !sti.typeSpecification.rawName.equals(dti.canonName)) {
          anonymousTypeImplementationClass = BName.of(dti.canonName);
        }
      }
      PartialTypeSpecification bpspec = bumpySpecOf(node);
      visitOnlyAnnotationsAndTypeArguments(node);
      if (bpspec instanceof PackageSpecification) {
        return ProcessingStatus.BREAK;
      }
      TypeSpecification bspec = (TypeSpecification) bpspec;
      if (bspec == null) {
        LogUtils.log(
            logger, Level.SEVERE, node,
            "Cannot compute type to flatten for "
            + LogUtils.serialize(node),
            null);
        return ProcessingStatus.BREAK;
      }
      TypeSpecification fspec = flatSpecFor(
          bspec, node, anonymousTypeImplementationClass);
      if (bspec.rawName.equals(fspec.rawName)
          && bspec.bindings.size() == fspec.bindings().size()) {
        // T was already flat and has the right number of parameters.
        // We're all done since we visited type arguments above.
        return ProcessingStatus.BREAK;
      }
      // Rebuild, preserving annotations on the type.
      if (nt == J8NodeType.ClassOrInterfaceType) {
        ClassOrInterfaceTypeNode ftn =
            factory.toClassOrInterfaceTypeNode(fspec);
        List<AnnotationNode> annotations = new ArrayList<>();
        for (J8BaseNode c : node.getChildren()) {
          if (c instanceof AnnotationNode) {
            annotations.add((AnnotationNode) c);
          }
        }
        if (!annotations.isEmpty()) {
          int identifierIndex = ftn.finder(IdentifierNode.class).indexOf();
          if (identifierIndex >= 0) {
            ftn.replaceChildren(
                ImmutableList.<J8BaseNode>builder()
                .addAll(ftn.getChildren().subList(0, identifierIndex))
                .addAll(annotations)
                .addAll(ftn.getChildren().subList(
                    identifierIndex, ftn.getNChildren()))
                .build());
          }
        }
        return ProcessingStatus.replace(ftn);
      } else if (nt == J8NodeType.TypeName) {
        return ProcessingStatus.replace(
            factory.toTypeNameNode(fspec.rawName));
      } else {
        Preconditions.checkState(nt == J8NodeType.PackageOrTypeName);
        return ProcessingStatus.replace(
            factory.toPackageOrTypeNameNode(fspec.rawName));
      }
    }

    private void visitOnlyAnnotationsAndTypeArguments(J8BaseNode node) {
      switch (node.getNodeType()) {
        case Annotation:
        case TypeArguments:
          this.visitUnchanged(node);
          break;
        default:
          for (J8BaseNode child : node.getChildren()) {
            visitOnlyAnnotationsAndTypeArguments(child);
          }
          break;
      }
    }

    private TypeSpecification flatSpecFor(
        TypeSpecification bspec, Positioned pos,
        @Nullable BName anonymousTypeImplementationClass) {
      return flatSpecFor(
          bspec, pos, anonymousTypeImplementationClass,
          new TypeSpecification.Mapper() {
            @Override public TypeBinding map(TypeBinding b) {
              if (b.typeSpec == null) { return b; }
              @SuppressWarnings("synthetic-access")
              TypeSpecification fts = flatSpecFor(b.typeSpec, pos, null);
              if (fts.equals(b.typeSpec)) { return b; }
              return new TypeBinding(b.variance, fts);
            }
          });
    }

    private TypeSpecification flatSpecFor(
        TypeSpecification bspec, Positioned pos,
        @Nullable BName anonymousTypeImplementationClass,
        TypeSpecification.Mapper bindingMapper) {
      BName adjustedRawName = anonymousTypeImplementationClass != null
          ? anonymousTypeImplementationClass
          : BName.of(bspec.rawName);
      FlatteningType t = ps.byBumpyName.get(adjustedRawName);
      if (t == null) {
        // An external type.  Just flatten any type arguments.
        return bindingMapper.map(bspec);
      }
      ImmutableList<BName> params;
      if (anonymousTypeImplementationClass == null) {
        params = t.flatParamInfo.bumpyParametersInOrder;
      } else {
        // Go to the super-type since t uses forwarding parameters
        // inferred after t.flatParamInfo is computed.
        params = ImmutableList.of();
        Optional<TypeInfo> tiOpt = pool.r.resolve(
            anonymousTypeImplementationClass.name);
        if (tiOpt.isPresent()) {
          TypeSpecification superType = tiOpt.get()
              .bestEffortNonObjectSuperType();
          Optional<TypeInfo> stiOpt = pool.r.resolve(
              superType.rawName);
          if (stiOpt.isPresent()) {
            ImmutableList.Builder<BName> b = ImmutableList.builder();
            for (Name pn : stiOpt.get().parameters) {
              b.add(BName.of(pn));
            }
            params = b.build();
          }
        }
      }
      if (params.isEmpty()) {
        return TypeSpecification.unparameterized(t.flatName.name);
      }
      // As we walk the tree, map bumpy type parameter names to bindings.
      Map<BName, TypeBinding> bumpyParamToBindings = new LinkedHashMap<>();
      flattenParamsOnto(bspec, false, bumpyParamToBindings, pos);
      ImmutableList.Builder<TypeBinding> b = ImmutableList.builder();
      for (int i = 0, n = params.size(); i < n; ++i) {
        BName paramName = params.get(i);
        TypeBinding bb = bumpyParamToBindings.get(paramName);
        if (bb == null) {
          LogUtils.log(
              logger, Level.SEVERE, pos,
              "Missing binding for " + paramName + " in " + t.bumpyName, null);
          bb = TypeBinding.WILDCARD;
        }
        TypeBinding fb = bindingMapper.map(bb);
        b.add(fb);
      }
      return new TypeSpecification(
          new PackageSpecification(t.flatName.name.parent),
          t.flatName.name, b.build(), bspec.nDims);
    }

    private void flattenParamsOnto(
        PartialTypeSpecification bspec, boolean infer,
        Map<BName, TypeBinding> out, Positioned pos) {
      ImmutableList<TypeBinding> bindings = bspec.bindings();

      ImmutableList<Name> typeParameters = null;
      Name rawName = bspec.getRawName();
      Optional<TypeInfo> tiOpt = pool.r.resolve(
          rawName.getContainingClass());
      if (tiOpt.isPresent()) {
        TypeInfo ti = tiOpt.get();
        if (bspec instanceof TypeSpecification) {
          typeParameters = ti.parameters;
        } else if (bspec instanceof MethodTypeContainer) {
          Optional<CallableInfo> ciOpt = ti.declaredCallableNamed(rawName);
          if (ciOpt.isPresent()) {
            typeParameters = ciOpt.get().typeParameters;
          }
        }
      }
      if (typeParameters != null) {
        if (bindings.size() == typeParameters.size()) {
          for (int i = 0, n = bindings.size(); i < n; ++i) {
            out.put(BName.of(typeParameters.get(i)), bindings.get(i));
          }
        } else {
          if (!bindings.isEmpty()) {
            LogUtils.log(
                logger, Level.SEVERE, pos,
                "Type argument mismatch for " + rawName + ", got " + bindings.size()
                + ", expected " + typeParameters.size(), null);
          }
          if (!infer) {
            // Treat as raw generic type.
            out.clear();
            return;
          } else {
            @SuppressWarnings("synthetic-access")
            Map<BName, TypeBinding> fromScope = typeBindingsFromScope(ft);
            for (Name typeParameter : typeParameters) {
              TypeBinding b = fromScope.get(BName.of(typeParameter));
              if (b == null) {
                Optional<TypeInfo> tpiOpt = pool.r.resolve(typeParameter);
                TypeSpecification st = JavaLang.JAVA_LANG_OBJECT;
                if (tpiOpt.isPresent()) {
                  st = tpiOpt.get().bestEffortNonObjectSuperType();
                }
                b = new TypeBinding(Variance.EXTENDS, st);
              }
              out.put(BName.of(typeParameter), b);
            }
          }
        }
      }

      PartialTypeSpecification parent = bspec.parent();
      if (parent != null && !(parent instanceof PackageSpecification)) {
        flattenParamsOnto(parent, true, out, pos);
      }
    }

    private PartialTypeSpecification bumpySpecOf(J8BaseNode node) {
      if (node instanceof J8WholeType) {
        StaticType bt = ((J8WholeType) node).getStaticType();
        if (bt != null) { return bt.typeSpecification; }
      }
      if (node instanceof J8TypeReference) {
        TypeInfo bti = ((J8TypeReference) node).getReferencedTypeInfo();
        if (bti != null) {
          return TypeSpecification.unparameterized(bti.canonName);
        }
      }

      // Infer from AST structure.
      PartialTypeSpecification parent = null;
      TypeArgumentsNode args = null;
      String ident = null;
      Name.Type type = null;
      for (int i = 0, n = node.getNChildren(); i < n; ++i) {
        J8BaseNode child = node.getChild(i);
        if (child instanceof IdentifierNode) {
          if (ident != null) {
            LogUtils.log(
                logger, Level.SEVERE, node, "Malformed type node", null);
            return null;
          }
          IdentifierNode idn = (IdentifierNode) child;
          ident = idn.getValue();
          type = idn.getNamePartType();
        } else if (child instanceof TypeArgumentsNode) {
          args = (TypeArgumentsNode) child;
        } else if (ident == null /* LR */ && isRewritableTypeNode(child)) {
          parent = bumpySpecOf(child);
        }
      }
      if (ident == null) {
        type = null;
      }
      if (type == Name.Type.PACKAGE) {
        return new PackageSpecification(
            (parent != null ? parent.getRawName() : Name.DEFAULT_PACKAGE)
            .child(ident, type));
      }

      ImmutableList.Builder<TypeBinding> bindings = ImmutableList.builder();
      TypeArgumentListNode argList = args != null
          ? args.firstChildWithType(TypeArgumentListNode.class)
          : null;
      if (argList != null) {
        for (J8BaseNode a : argList.getChildren()) {
          if (!(a instanceof TypeArgumentNode)) {
            TypeArgumentNode an = (TypeArgumentNode) a;
            Variance v = Variance.INVARIANT;
            TypeSpecification bindingSpec = null;
            Optional<ReferenceTypeNode> rtn = an.finder(ReferenceTypeNode.class)
                .exclude(J8NodeType.ReferenceType).findOne();
            if (rtn.isPresent()) {
              StaticType st = rtn.get().getStaticType();
              if (st != null) {
                bindingSpec = st.typeSpecification;
              }
            }
            Optional<WildcardBoundsNode> wcn =
                an.finder(WildcardBoundsNode.class)
                .exclude(J8NodeType.ReferenceType).findOne();
            if (wcn.isPresent()) {
              switch (wcn.get().getVariant()) {
                case ExtendsReferenceType:
                  v = Variance.EXTENDS;
                  break;
                case SuperReferenceType:
                  v = Variance.SUPER;
                  break;
              }
            }
            bindings.add(new TypeBinding(v, bindingSpec));
          }
        }
      }

      if (type == Name.Type.METHOD) {
        // Reverse the convention from TypeNodeFactory
        int ds = Preconditions.checkNotNull(ident).lastIndexOf('$');
        int methodVariant = -1;
        String methodName = null;
        if (ds >= 0) {
          methodName = ident.substring(0, ds);
          try {
            methodVariant = Integer.parseInt(ident.substring(ds + 1));
          } catch (@SuppressWarnings("unused") NumberFormatException e) {
            // Handled below.
          }
        }
        if (methodName != null && parent instanceof TypeSpecification) {
          Name name = parent.getRawName()
            .method(methodName, methodVariant);
          return new MethodTypeContainer(
              (TypeSpecification) parent, name, bindings.build());
        }
      }
      if (type != null && type.isType) {
        return new TypeSpecification(parent, ident, type, bindings.build(), 0);
      }
      LogUtils.log(
          logger, Level.SEVERE, node,
          "Cannot determine bumpy type of " + LogUtils.serialize(node),
          null);
      return null;
    }
  }

  static boolean isRewritableTypeNode(J8BaseNode node) {
    J8NodeType nt = node.getNodeType();
    return nt == J8NodeType.ClassOrInterfaceType
        || nt == J8NodeType.TypeName
        || nt == J8NodeType.PackageOrTypeName;
  }
}
