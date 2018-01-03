package com.mikesamuel.cil.ast.passes.flatten;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.mikesamuel.cil.ast.j8.ArgumentListNode;
import com.mikesamuel.cil.ast.j8.ClassBodyDeclarationNode;
import com.mikesamuel.cil.ast.j8.ClassBodyNode;
import com.mikesamuel.cil.ast.j8.ClassTypeNode;
import com.mikesamuel.cil.ast.j8.ConstructorBodyNode;
import com.mikesamuel.cil.ast.j8.ConstructorDeclarationNode;
import com.mikesamuel.cil.ast.j8.ConstructorDeclaratorNode;
import com.mikesamuel.cil.ast.j8.ExplicitConstructorInvocationNode;
import com.mikesamuel.cil.ast.j8.ExpressionAtomNode;
import com.mikesamuel.cil.ast.j8.ExpressionNode;
import com.mikesamuel.cil.ast.j8.IdentifierNode;
import com.mikesamuel.cil.ast.j8.InterfaceTypeListNode;
import com.mikesamuel.cil.ast.j8.InterfaceTypeNode;
import com.mikesamuel.cil.ast.j8.J8BaseNode;
import com.mikesamuel.cil.ast.j8.J8FileNode;
import com.mikesamuel.cil.ast.j8.J8NodeType;
import com.mikesamuel.cil.ast.j8.J8TypeDeclaration;
import com.mikesamuel.cil.ast.j8.LocalNameNode;
import com.mikesamuel.cil.ast.j8.ModifierNode;
import com.mikesamuel.cil.ast.j8.NormalClassDeclarationNode;
import com.mikesamuel.cil.ast.j8.SimpleTypeNameNode;
import com.mikesamuel.cil.ast.j8.SuperclassNode;
import com.mikesamuel.cil.ast.j8.SuperinterfacesNode;
import com.mikesamuel.cil.ast.j8.TypeBoundNode;
import com.mikesamuel.cil.ast.j8.TypeParameterListNode;
import com.mikesamuel.cil.ast.j8.TypeParameterNode;
import com.mikesamuel.cil.ast.j8.TypeParametersNode;
import com.mikesamuel.cil.ast.j8.UnqualifiedClassInstanceCreationExpressionNode;
import com.mikesamuel.cil.ast.meta.CallableInfo;
import com.mikesamuel.cil.ast.meta.Name;
import com.mikesamuel.cil.ast.meta.StaticType.TypePool;
import com.mikesamuel.cil.ast.meta.TypeInfo;
import com.mikesamuel.cil.ast.meta.TypeSpecification;
import com.mikesamuel.cil.ast.passes.AbstractPass;
import com.mikesamuel.cil.ast.passes.TypeNodeFactory;
import com.mikesamuel.cil.ast.passes.flatten.PassState.FlatteningType;
import com.mikesamuel.cil.util.LogUtils;

final class CollectTypeDeclarationsMiniPass extends AbstractPass<PassState> {
  final TypePool pool;
  final ForwardingTypeInfoResolver r;
  private Multimap<J8FileNode, J8TypeDeclaration> decls =
      LinkedHashMultimap.create();

  protected CollectTypeDeclarationsMiniPass(
      Logger logger, TypePool pool, ForwardingTypeInfoResolver r) {
    super(logger);
    this.pool = pool;
    this.r = r;
  }

  @Override
  public PassState run(Iterable<? extends J8FileNode> fileNodes) {
    decls.clear();
    for (J8FileNode fileNode : fileNodes) {
      visit(fileNode, (J8BaseNode) fileNode);
    }
    return buildPassState();
  }

  private void visit(J8FileNode fileNode, J8BaseNode node) {
    if (node instanceof J8TypeDeclaration
        && node.getNodeType() != J8NodeType.TypeParameter) {
      J8TypeDeclaration decl = (J8TypeDeclaration) node;
      if (decl.getDeclaredTypeInfo() == null) {
        boolean needsTypeInfo = true;
        switch (decl.getNodeType()) {
          case EnumConstant:
          case UnqualifiedClassInstanceCreationExpression:
            needsTypeInfo = node.firstChildWithType(ClassBodyNode.class)
                != null;
            break;
          default:
        }
        if (needsTypeInfo) {
          LogUtils.log(
              logger, Level.SEVERE, node,
              "Missing type information for type declaration", null);
        }
      } else {
        decls.put(fileNode, decl);
      }
    }
    for (J8BaseNode child : node.getChildren()) {
      visit(fileNode, child);
    }
  }

  private PassState buildPassState() {
    Map<BName, J8TypeDeclaration> byName = new LinkedHashMap<>();
    Map<BName, J8FileNode> sources = new LinkedHashMap<>();
    for (Map.Entry<J8FileNode, J8TypeDeclaration> e : decls.entries()) {
      J8TypeDeclaration decl = e.getValue();
      BName bname = BName.of(decl.getDeclaredTypeInfo().canonName);
      J8TypeDeclaration dupe = byName.put(bname, decl);
      sources.put(bname, e.getKey());
      if (dupe != null) {
        LogUtils.log(
            logger, Level.SEVERE, dupe,
            "Declaration clobbered by one with duplicate name " + bname + " @ "
            + decl.getSourcePosition(),
            null);
      }
    }

    FlatTypes flatTypes = new FlatTypes(logger, r);
    Multimap<BName, BName> preceders = LinkedHashMultimap.create();
    for (Map.Entry<BName, J8TypeDeclaration> e : byName.entrySet()) {
      BName bname = e.getKey();
      J8TypeDeclaration decl = e.getValue();
      // We filter out any that are missing type info during visit().
      TypeInfo ti = Preconditions.checkNotNull(decl.getDeclaredTypeInfo());

      flatTypes.recordType(bname);
      for (Name typeParameter : ti.parameters) {
        flatTypes.recordType(BName.of(typeParameter));
      }

      // Declarations must follow their super types.
      for (TypeSpecification superType
           : Iterables.concat(ti.superType.asSet(), ti.interfaces)) {
        BName superBName = BName.of(superType.rawName);
        if (byName.containsKey(superBName)) {
          preceders.put(bname, superBName);
        }
      }
      // Declarations must precede their inner types.
      for (Name innerClass : ti.innerClasses) {
        BName innerBName = BName.of(innerClass);
        Preconditions.checkState(byName.containsKey(innerBName));
        preceders.put(innerBName, bname);
      }
    }
    flatTypes.disambiguate();

    ImmutableList<BName> order;
    {
      ImmutableList.Builder<BName> b = ImmutableList.builder();
      Set<BName> ordered = new LinkedHashSet<>();
      for (BName bname : byName.keySet()) {
        topoSortOnto(bname, ordered, preceders, b);
      }
      order = b.build();
      Preconditions.checkState(order.size() == byName.size());
    }

    return new PassState(
        Lists.transform(
            order,
            new Function<BName, PassState.FlatteningType>() {
              @SuppressWarnings("synthetic-access")
              @Override public FlatteningType apply(BName bname) {
                return new PassState.FlatteningType(
                    sources.get(bname),
                    deanonymize(byName.get(bname)),
                    bname,
                    flatTypes.getFlatTypeName(bname),
                    flatTypes.getFlatParamInfo(bname),
                    r
                    );
              }
            }));
  }

  private void topoSortOnto(
      BName bname, Set<BName> ordered, Multimap<BName, BName> preceders,
      ImmutableList.Builder<BName> b) {
    if (ordered.add(bname)) {
      for (BName preceder : preceders.get(bname)) {
        topoSortOnto(preceder, ordered, preceders, b);
      }
      b.add(bname);
    }
  }

  private J8TypeDeclaration deanonymize(J8TypeDeclaration root) {
    if (!(root instanceof UnqualifiedClassInstanceCreationExpressionNode)) {
      return root;
    }
    TypeNodeFactory factory = new TypeNodeFactory(logger, pool);
    UnqualifiedClassInstanceCreationExpressionNode cci =
        (UnqualifiedClassInstanceCreationExpressionNode) root;
    Name canonName = cci.getDeclaredTypeInfo().canonName;

    ArgumentListNode args = cci.firstChildWithType(ArgumentListNode.class);
    ClassBodyNode body = cci.firstChildWithType(ClassBodyNode.class);

    // Make sure that we have forwarding type parameters.
    TypeInfo ti = r.addForwardersFor(canonName).or(cci.getDeclaredTypeInfo());
    NormalClassDeclarationNode normalDecl =
        NormalClassDeclarationNode.Variant.Declaration.buildNode(
            ModifierNode.Variant.Final.buildNode(),
            SimpleTypeNameNode.Variant.Identifier.buildNode(
                IdentifierNode.Variant.Builtin.buildNode("_")));

    normalDecl.copyMetadataFrom(root);
    normalDecl.setDeclaredTypeInfo(ti);
    if (!ti.parameters.isEmpty()) {
      ImmutableList.Builder<TypeParameterNode> params =
          ImmutableList.builder();
      for (Name paramName : ti.parameters) {
        TypeParameterNode pnode = TypeParameterNode.Variant.TypeParameter
            .buildNode(
                SimpleTypeNameNode.Variant.Identifier.buildNode(
                    IdentifierNode.Variant.Builtin.buildNode(
                        paramName.identifier)));
        Optional<TypeInfo> pti = r.resolve(paramName);
        if (pti.isPresent()) {
          TypeBoundNode bound = factory.typeBoundFor(pti.get());
          if (bound != null) {
            pnode.add(bound);
          }
        }
        params.add(pnode);
      }
      normalDecl.add(TypeParametersNode.Variant.LtTypeParameterListGt
          .buildNode(
              TypeParameterListNode.Variant.TypeParameterComTypeParameter
              .buildNode(params.build())));
    }
    if (ti.superType.isPresent()) {
      normalDecl.add(SuperclassNode.Variant.ExtendsClassType.buildNode(
          ClassTypeNode.Variant.ClassOrInterfaceType.buildNode(
              factory.toClassOrInterfaceTypeNode(ti.superType.get()))));
    }
    if (!ti.interfaces.isEmpty()) {
      ImmutableList.Builder<InterfaceTypeNode> b = ImmutableList.builder();
      for (TypeSpecification iface : ti.interfaces) {
        b.add(InterfaceTypeNode.Variant.ClassOrInterfaceType.buildNode(
            factory.toClassOrInterfaceTypeNode(iface)));
      }
      normalDecl.add(SuperinterfacesNode.Variant.ImplementsInterfaceTypeList
          .buildNode(
              InterfaceTypeListNode.Variant.InterfaceTypeComInterfaceType
              .buildNode(b.build())));
    }

    // Now add a constructor that forwards arguments to the super type.
    if (args != null) {
      // If not, the implied default constructor suffices.
      // This also lets us avoid a wrinkle where the TypingPass does
      // not look for constructors on interfaces, so constructor below
      // will be null.
      CallableInfo constructor = cci.getCallableInfo();
      if (constructor != null) {
        ImmutableList.Builder<J8BaseNode> ctorChildren =
            ImmutableList.builder();
        ImmutableList.Builder<J8BaseNode> declaratorChildren =
            ImmutableList.builder();

        // Copy constructor type parameters
        if (!constructor.typeParameters.isEmpty()) {
          TypeParameterListNode paramList =
              TypeParameterListNode.Variant.TypeParameterComTypeParameter
              .buildNode();
          declaratorChildren.add(
              TypeParametersNode.Variant.LtTypeParameterListGt
              .buildNode(paramList));
          for (Name typeParamName : constructor.typeParameters) {
            TypeParameterNode paramNode = TypeParameterNode.Variant.TypeParameter
                .buildNode(SimpleTypeNameNode.Variant.Identifier.buildNode(
                    IdentifierNode.Variant.Builtin.buildNode(
                        typeParamName.identifier)));
            Optional<TypeInfo> ptiOpt = r.resolve(typeParamName);
            if (ptiOpt.isPresent()) {
              TypeBoundNode bound = factory.typeBoundFor(ptiOpt.get());
              if (bound != null) { paramNode.add(bound); }
            }
            paramList.add(paramNode);
          }
        }
        // Given it a placeholder name which will be fixed later.
        declaratorChildren.add(SimpleTypeNameNode.Variant.Identifier.buildNode(
            IdentifierNode.Variant.Builtin.buildNode("_")));
        // Add a formal parameter list.
        {
          ImmutableList<TypeSpecification> formalTypes = constructor.getFormalTypes();
          if (!formalTypes.isEmpty()) {
            List<String> formalNames = new ArrayList<>();
            for (int i = 0, n = formalTypes.size(); i < n; ++i) {
              formalNames.add("a" + i);
            }
            declaratorChildren.add(factory.toFormalParameterListNode(
                formalTypes, formalNames, constructor.isVariadic()));
          }
        }

        ctorChildren.add(
            ConstructorDeclaratorNode.Variant
            .TypeParametersSimpleTypeNameLpFormalParameterListRp
            .buildNode(declaratorChildren.build()));

        if (!constructor.getThrownTypes().isEmpty()) {
          ctorChildren.add(factory.toThrowsNode(constructor.getThrownTypes()));
        }

        ImmutableList.Builder<J8BaseNode> superCtorCall = ImmutableList.builder();
        if (!constructor.typeParameters.isEmpty()) {
          superCtorCall.add(factory.toTypeArgumentsNode(
              null,
              Lists.transform(
                  constructor.typeParameters,
                  new Function<Name, TypeSpecification.TypeBinding>() {

                    @Override
                    public TypeSpecification.TypeBinding apply(Name nm) {
                      return new TypeSpecification.TypeBinding(
                          TypeSpecification.unparameterized(nm));
                    }
                  })));
        }
        if (!constructor.getFormalTypes().isEmpty()) {
          ArgumentListNode argumentList = ArgumentListNode.Variant
              .ExpressionComExpression.buildNode();
          ImmutableList<TypeSpecification> formalTypes =
              constructor.getFormalTypes();
          for (int i = 0, n = formalTypes.size(); i < n; ++i) {
            argumentList.add(
                ExpressionNode.Variant.ConditionalExpression.buildNode(
                    ExpressionAtomNode.Variant.Local.buildNode(
                        LocalNameNode.Variant.Identifier.buildNode(
                            IdentifierNode.Variant.Builtin.buildNode(
                                "a" + i)))));
          }
          superCtorCall.add(argumentList);
        }
        ctorChildren.add(
            ConstructorBodyNode.Variant
            .LcExplicitConstructorInvocationBlockStatementsRc.buildNode(
                ExplicitConstructorInvocationNode.Variant
                .TypeArgumentsSuperLpArgumentListRpSem.buildNode(
                    superCtorCall.build())));

        ConstructorDeclarationNode ctorDecl = ConstructorDeclarationNode
            .Variant.Declaration.buildNode(ctorChildren.build());

        body.add(
            ClassBodyDeclarationNode.Variant.ConstructorDeclaration
            .buildNode(ctorDecl));
      } else {
        LogUtils.log(
            logger, Level.SEVERE, args,
            "Cannot find super constructor for anonymous class", null);
      }
    }

    normalDecl.add(body);
    return normalDecl;
  }

}
