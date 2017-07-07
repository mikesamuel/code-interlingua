package com.mikesamuel.cil.ast.passes;

import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import javax.annotation.Nullable;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.collect.Table;
import com.mikesamuel.cil.ast.j8.AnnotationNode;
import com.mikesamuel.cil.ast.j8.ArgumentListNode;
import com.mikesamuel.cil.ast.j8.BlockNode;
import com.mikesamuel.cil.ast.j8.BlockStatementNode;
import com.mikesamuel.cil.ast.j8.BlockStatementsNode;
import com.mikesamuel.cil.ast.j8.CaseValueNode;
import com.mikesamuel.cil.ast.j8.ClassBodyDeclarationNode;
import com.mikesamuel.cil.ast.j8.ClassBodyNode;
import com.mikesamuel.cil.ast.j8.ClassMemberDeclarationNode;
import com.mikesamuel.cil.ast.j8.EnumBodyDeclarationsNode;
import com.mikesamuel.cil.ast.j8.EnumBodyNode;
import com.mikesamuel.cil.ast.j8.EnumConstantNameNode;
import com.mikesamuel.cil.ast.j8.EnumConstantNode;
import com.mikesamuel.cil.ast.j8.EnumDeclarationNode;
import com.mikesamuel.cil.ast.j8.ExpressionAtomNode;
import com.mikesamuel.cil.ast.j8.ExpressionNode;
import com.mikesamuel.cil.ast.j8.ExpressionStatementNode;
import com.mikesamuel.cil.ast.j8.FieldDeclarationNode;
import com.mikesamuel.cil.ast.j8.FieldNameNode;
import com.mikesamuel.cil.ast.j8.FormalParameterListNode;
import com.mikesamuel.cil.ast.j8.IdentifierNode;
import com.mikesamuel.cil.ast.j8.InstanceInitializerNode;
import com.mikesamuel.cil.ast.j8.J8BaseInnerNode;
import com.mikesamuel.cil.ast.j8.J8BaseNode;
import com.mikesamuel.cil.ast.j8.J8FileNode;
import com.mikesamuel.cil.ast.j8.J8NodeType;
import com.mikesamuel.cil.ast.j8.J8TypeDeclaration;
import com.mikesamuel.cil.ast.j8.LocalNameNode;
import com.mikesamuel.cil.ast.j8.MarkerAnnotationNode;
import com.mikesamuel.cil.ast.j8.MethodBodyNode;
import com.mikesamuel.cil.ast.j8.MethodDeclarationNode;
import com.mikesamuel.cil.ast.j8.MethodDeclaratorNode;
import com.mikesamuel.cil.ast.j8.MethodHeaderNode;
import com.mikesamuel.cil.ast.j8.MethodInvocationNode;
import com.mikesamuel.cil.ast.j8.MethodNameNode;
import com.mikesamuel.cil.ast.j8.Mixins;
import com.mikesamuel.cil.ast.j8.ModifierNode;
import com.mikesamuel.cil.ast.j8.PackageOrTypeNameNode;
import com.mikesamuel.cil.ast.j8.PrimaryNode;
import com.mikesamuel.cil.ast.j8.ReferenceTypeNode;
import com.mikesamuel.cil.ast.j8.ResultNode;
import com.mikesamuel.cil.ast.j8.ReturnStatementNode;
import com.mikesamuel.cil.ast.j8.SimpleTypeNameNode;
import com.mikesamuel.cil.ast.j8.StatementExpressionNode;
import com.mikesamuel.cil.ast.j8.StatementNode;
import com.mikesamuel.cil.ast.j8.StaticInitializerNode;
import com.mikesamuel.cil.ast.j8.SwitchBlockNode;
import com.mikesamuel.cil.ast.j8.SwitchBlockStatementGroupNode;
import com.mikesamuel.cil.ast.j8.SwitchLabelNode;
import com.mikesamuel.cil.ast.j8.SwitchLabelsNode;
import com.mikesamuel.cil.ast.j8.SwitchStatementNode;
import com.mikesamuel.cil.ast.j8.ThrowsNode;
import com.mikesamuel.cil.ast.j8.TypeArgumentListNode;
import com.mikesamuel.cil.ast.j8.TypeArgumentNode;
import com.mikesamuel.cil.ast.j8.TypeArgumentsNode;
import com.mikesamuel.cil.ast.j8.TypeBoundNode;
import com.mikesamuel.cil.ast.j8.TypeNameNode;
import com.mikesamuel.cil.ast.j8.TypeParameterListNode;
import com.mikesamuel.cil.ast.j8.TypeParameterNode;
import com.mikesamuel.cil.ast.j8.TypeParametersNode;
import com.mikesamuel.cil.ast.j8.TypeVariableNode;
import com.mikesamuel.cil.ast.j8.UnannTypeNode;
import com.mikesamuel.cil.ast.j8.VariableDeclaratorIdNode;
import com.mikesamuel.cil.ast.meta.CallableInfo;
import com.mikesamuel.cil.ast.meta.FieldInfo;
import com.mikesamuel.cil.ast.meta.JavaLang;
import com.mikesamuel.cil.ast.meta.MemberInfo;
import com.mikesamuel.cil.ast.meta.MemberInfoPool;
import com.mikesamuel.cil.ast.meta.MetadataBridge;
import com.mikesamuel.cil.ast.meta.MethodDescriptor;
import com.mikesamuel.cil.ast.meta.Name;
import com.mikesamuel.cil.ast.meta.StaticType;
import com.mikesamuel.cil.ast.meta.TypeInfo;
import com.mikesamuel.cil.ast.meta.TypeSpecification;
import com.mikesamuel.cil.ast.passes.DespecializeEnumPass.CollectedOverrides.ConstantAndMigratedName;
import com.mikesamuel.cil.ast.passes.DespecializeEnumPass.CollectedOverrides.MethodNodeAndMetadata;
import com.mikesamuel.cil.parser.SList;

/**
 * A pass that gets rid of anonymous classes created for specialized enum
 * variants.
 *
 * <h3>Prerequisites</h3>
 * <p>This pass depends on metadata attached by the typing pass.
 *
 * <h3>Postconditions</h3>
 * <p>
 * After applying this pass, there will be no enum constants with class bodies
 * which may simplify translating java {@code enum}s into similar constructs in
 * languages that do not directly support specialization.
 * <p>
 * This pass introduces new synthetic private members and removes classes from
 * compilation units without removing them from the TypeInfoResolver.
 * After running it removes all name and type related metadata, so common
 * passes should be rerun.
 * <h3>Example</h3>
 * <p>
 * Consider,
 * <pre>
 * enum E {
 *   A(1),
 *   B(3) {
 *     // Make specialized class fields into private statics on the parent enum.
 *     private int x;  // E.B.x -> E.B$x module name conflicts.
 *
 *     // Methods need to be turned into private statics and
 *     // any calls to them rewritten.
 *     // Overridden methods can be handled by inserting switches into the
 *     // method body in the containing enum.
 *     \@Override
 *     public String toString() {
 *       // References to fields of this should refer to state object
 *       // fields, with casts as appropriate.
 *       ++this.x;
 *       // Calls to super need to bubble out.
 *       return super.toString();
 *     }
 *
 *     \@Override
 *     int getNum() { return x; }
 *   },
 *   ;
 *
 *   int n;
 *
 *   E(int n) {
 *     this.n = n;
 *   }
 *
 *   // Method.
 *   int getNum() {
 *     // Since getNum() is overridden we need to insert code here.
 *     return n;
 *   }
 *
 *   \@Override
 *   public String toString() {
 *     // Since toString is overridden we need to insert code here.
 *     return name() + ":" + getX();
 *   }
 * }
 * </pre>
 */
public final class DespecializeEnumPass extends AbstractRewritingPass {
  private final MethodVariantPool variantPool;
  private final MemberInfoPool infoPool;

  protected DespecializeEnumPass(
      Logger logger, MemberInfoPool infoPool, MethodVariantPool variantPool) {
    super(logger);
    this.infoPool = infoPool;
    this.variantPool = variantPool;
  }

  @Override
  public ImmutableList<J8FileNode> run(
      Iterable<? extends J8FileNode> fileNodes) {
    ImmutableList<J8FileNode> result = super.run(fileNodes);
    for (J8FileNode file : result) {
      ((J8BaseNode) file).transformMetadata(MetadataBridge.Bridges.ZERO, true);
    }
    return result;
  }

  /**
   * We keep a stack of information we've collected about the enum that we're
   * processing.
   */
  private List<EnumState> enumStates = Lists.newArrayList();
  private List<TypeInfo> typesInScope = Lists.newArrayList();

  /**
   * We keep a stack with an element for each type declaration's scope so that
   * we can mangle names without introducing namespace conflicts.
   */
  private Set<String> nameExclusions = Sets.newHashSet();


  @Override
  protected ProcessingStatus previsit(
      J8BaseNode node, @Nullable SList<Parent> pathFromRoot) {
    if (node instanceof J8FileNode) {
      Preconditions.checkState(enumStates.isEmpty() && typesInScope.isEmpty());
      nameExclusions.clear();

      excludeNamesThatMayBeInScope(node);
    }
    if (node instanceof J8TypeDeclaration) {
      J8TypeDeclaration decl = (J8TypeDeclaration) node;
      TypeInfo ti = decl.getDeclaredTypeInfo();
      if (ti != null) {
        typesInScope.add(ti);
        switch (node.getNodeType()) {
          case EnumDeclaration: {
            EnumState es = new EnumState(ti);
            enumStates.add(es);
            for (EnumConstantNode ecn
                 : node.finder(EnumConstantNode.class)
                       .exclude(EnumConstantNode.class)
                       .find()) {
              ClassBodyNode body = ecn.firstChildWithType(ClassBodyNode.class);
              if (body == null) { continue; }  // Not specialized.
              TypeInfo eti = ecn.getDeclaredTypeInfo();
              if (eti == null) {
                error(ecn, "Missing type info");
                continue;
              }
              EnumConstantNameNode name = Preconditions.checkNotNull(
                  ecn.firstChildWithType(EnumConstantNameNode.class));
              IdentifierNode nameId = name.finder(IdentifierNode.class).findOne()
                  .get();
              es.specialConstants.add(new SpecialConstant(eti, nameId, body));

              String constNameIdent = nameId.getValue();

              // Reserve names for members we need to move into the outer
              // enun class.
              for (MemberInfo mi : eti.getDeclaredMembers()) {
                // TODO: does this reserve a name for any constructor that we
                // end up not using?
                es.reserveNameFor(constNameIdent, mi);
              }
              // Look for named inner class declarations.
              for (ClassBodyDeclarationNode cbd
                   : body.finder(ClassBodyDeclarationNode.class)
                         .exclude(ClassBodyDeclarationNode.class)
                         .find()) {
                Optional<J8TypeDeclaration> innerType =
                    Mixins.getInnerTypeDeclaration(cbd);
                if (innerType.isPresent()) {
                  TypeInfo iti = innerType.get().getDeclaredTypeInfo();
                  if (iti != null) {
                    es.reserveNameFor(constNameIdent, iti);
                  }
                }
              }
            }
            break;
          }
          default:
            break;
        }
      }
    }
    return ProcessingStatus.CONTINUE;
  }

  private ProcessingStatus maybeRewrite(J8BaseNode node) {
    switch (node.getNodeType()) {
      case EnumConstant: {
        int bodyIndex = node.finder(ClassBodyNode.class).indexOf();
        if (bodyIndex >= 0) {
          EnumConstantNode decl = (EnumConstantNode) node;
          decl.remove(bodyIndex);
        }
        break;
      }
      case EnumDeclaration: {
        // Take member
        // in the containing
        EnumState es = enumStates.get(enumStates.size() - 1);
        if (es.specialConstants.isEmpty()) { break; }
        EnumDeclarationNode decls = (EnumDeclarationNode) node;
        EnumBodyNode body = Preconditions.checkNotNull(
            decls.firstChildWithType(EnumBodyNode.class));
        EnumBodyDeclarationsNode members = body.firstChildWithType(
            EnumBodyDeclarationsNode.class);
        if (members == null) {
          members = EnumBodyDeclarationsNode.Variant.SemClassBodyDeclaration
              .buildNode();
          body.add(members);
        }

        // Compile a map of method declarations to descriptors.
        // We'll use this to handle overrides of specialized enums.
        CollectedOverrides overrides = new CollectedOverrides();
        Migration mig = new Migration(es);

        for (int i = 0, n = members.getNChildren(); i < n; ++i) {
          ClassBodyDeclarationNode cbd = (ClassBodyDeclarationNode)
              members.getChild(i);
          Optional<MethodDeclarationNode> mdOpt = asMethodDeclaration(cbd);
          if (mdOpt.isPresent()) {
            MethodDeclarationNode md = mdOpt.get();
            CallableInfo ci = (CallableInfo) md.getMemberInfo();
            if (ci != null) {
              MethodDescriptor desc = ci.getDescriptor();
              if (desc != null) {
                CollectedOverrides.MethodNodeAndMetadata methodAndMetadata =
                    overrides.get(ci);
                methodAndMetadata.declNode = md;
              }
            }
          }
        }

        for (SpecialConstant sc : es.specialConstants) {
          for (int i = 0, n = sc.classBody.getNChildren(); i < n; ++i) {
            ClassBodyDeclarationNode specialDeclaration =
                (ClassBodyDeclarationNode) sc.classBody.getChild(i);
            // Migrate to a private static member.
            ClassBodyDeclarationNode privateStaticCopy =
                mig.migrate(specialDeclaration.deepClone());
            members.add(privateStaticCopy);
            // Associate with any overridden method so that we can tweak
            // methods.
            Optional<MethodDeclarationNode> mdOpt = asMethodDeclaration(
                specialDeclaration);
            if (mdOpt.isPresent()) {
              MethodDeclarationNode md = mdOpt.get();
              CallableInfo ci = (CallableInfo) md.getMemberInfo();
              if (ci != null) {
                ImmutableSet<Name> overridden = infoPool.overriddenBy(ci);
                if (!overridden.isEmpty()) {
                  // Find the override in the containing enum, creating one
                  // if necessary.
                  // We don't need to specialize any created methods since none
                  // of the overridable methods in Object or Enum are
                  // parameterized.
                  Map<Name, Name> containingClassToMethod = Maps.newHashMap();
                  for (Name m : overridden) {
                    // TODO: Do we risk collisions between specialized methods
                    // and bridge methods.
                    containingClassToMethod.put(m.getContainingClass(), m);
                  }
                  Name overrideName = null;  // The method overridden by sc
                  for (Name className
                       : new Name[] {
                           es.ti.canonName,
                           JavaLang.JAVA_LANG_ENUM.rawName,
                           JavaLang.JAVA_LANG_OBJECT.rawName }) {
                    overrideName = containingClassToMethod.get(className);
                    if (overrideName != null) { break; }
                  }
                  Preconditions.checkNotNull(overrideName, ci.canonName);
                  // We need to remember to insert a switch(this) at the
                  // start of the body of the overridden method with a
                  // case for sc.
                  MethodNodeAndMetadata mmd;
                  Optional<CallableInfo> ociOpt =
                      infoPool.typePool.r.resolveCallable(overrideName);
                  if (!ociOpt.isPresent()) {
                    error(
                        md,
                        "Missing method info for overridden method "
                        + overrideName);
                    continue;
                  }
                  CallableInfo oci = ociOpt.get();
                  if (overrideName.parent.equals(es.ti.canonName)) {
                    mmd = overrides.get(oci);
                  } else {
                    // The method is not overridden in the enum class, so
                    // we need to add an override so we have a place to put
                    // the switch.
                    // Create an override that just calls super.

                    // We can't just clone md since it may specialize the
                    // return type or widen access modifiers.
                    Name localOverrideName = variantPool.allocateVariant(
                        es.ti.canonName, oci.canonName.identifier);

                    CallableInfo sci = new CallableInfo(
                        oci.modifiers,
                        localOverrideName,
                        Lists.transform(
                            oci.typeParameters,
                            new Function<Name, Name>() {
                              @Override
                              public Name apply(Name tp) {
                                return localOverrideName.child(
                                    tp.identifier, tp.type);
                              }
                            }),
                        false);
                    sci.setReturnType(oci.getReturnType());
                    sci.setFormalTypes(oci.getFormalTypes());
                    sci.setVariadic(oci.isVariadic());

                    MethodDeclarationNode superCallingImpl = callToSuper(
                        sci, md);
                    members.add(
                        ClassBodyDeclarationNode.Variant.ClassMemberDeclaration
                        .buildNode(
                            ClassMemberDeclarationNode.Variant.MethodDeclaration
                            .buildNode(superCallingImpl)));
                    es.ti.addSyntheticMember(sci);

                    mmd = overrides.get(oci);
                    mmd.declNode = superCallingImpl;
                  }

                  mmd.specialConstantsOverriding.add(
                      new CollectedOverrides.ConstantAndMigratedName(
                          sc,
                          es.specializedNameToUnspecialized.get(
                              ci.canonName)));
                }
              }
            }
          }
        }
        for (MethodNodeAndMetadata mmd : overrides.allMethods()) {
          MethodDeclarationNode decl = mmd.declNode;
          Optional<FormalParameterListNode> formalsOpt =
              decl.finder(FormalParameterListNode.class)
              .exclude(J8NodeType.MethodBody)
              .findOne();

          boolean isVoid = StaticType.T_VOID.typeSpecification.equals(
              mmd.callableInfo.getReturnType());

          ArgumentListNode argumentList = null;
          if (formalsOpt.isPresent()) {
            ImmutableList<VariableDeclaratorIdNode> formalNames = formalsOpt
                .get().finder(VariableDeclaratorIdNode.class).find();

            argumentList = ArgumentListNode.Variant.ExpressionComExpression
                .buildNode(Lists.transform(
                    formalNames,
                    new Function<VariableDeclaratorIdNode, ExpressionNode>() {

                      @Override
                      public ExpressionNode apply(
                          VariableDeclaratorIdNode formal) {
                        return ExpressionNode.Variant
                            .ConditionalExpression.buildNode(
                                ExpressionAtomNode.Variant.Local.buildNode(
                                    LocalNameNode.Variant.Identifier.buildNode(
                                        toIdentifierNode(
                                            formal.getDeclaredExpressionName())
                                        )));
                      }
                    }));
          }

          // Craft a switch
          ImmutableList.Builder<J8BaseNode> cases = ImmutableList.builder();
          for (ConstantAndMigratedName cmn : mmd.specialConstantsOverriding) {
            List<J8BaseNode> callAndReturnStatements;
            ExpressionAtomNode specializedCall =
                ExpressionAtomNode.Variant.MethodInvocation
                .buildNode(
                    MethodNameNode.Variant.Identifier.buildNode(
                        IdentifierNode.Variant.Builtin.buildNode(
                            cmn.migratedName.identifier)));
            if (argumentList != null) {
              specializedCall.add(
                  ArgumentListNode.Variant.ExpressionComExpression
                  .buildNode(argumentList.deepClone()));
            }

            if (isVoid) {
              callAndReturnStatements = ImmutableList.of(
                  BlockStatementNode.Variant.Statement.buildNode(
                      StatementNode.Variant.ExpressionStatement.buildNode(
                          ExpressionStatementNode.Variant.StatementExpressionSem
                          .buildNode(
                              StatementExpressionNode.Variant.MethodInvocation
                              .buildNode(
                                  MethodInvocationNode.Variant.ImplicitCallee
                                  .buildNode(specializedCall))))),
                  BlockStatementNode.Variant.Statement.buildNode(
                      StatementNode.Variant.ReturnStatement.buildNode())
                  );
            } else {
              callAndReturnStatements = ImmutableList.of(
                  BlockStatementNode.Variant.Statement.buildNode(
                      StatementNode.Variant.ReturnStatement.buildNode(
                          ReturnStatementNode.Variant.ReturnExpressionSem
                          .buildNode(
                              ExpressionNode.Variant.ConditionalExpression
                              .buildNode(specializedCall)))));
            }
            cases.add(
                SwitchBlockStatementGroupNode.Variant
                .SwitchLabelsBlockStatements
                .buildNode(
                    SwitchLabelsNode.Variant.SwitchLabelSwitchLabel.buildNode(
                        SwitchLabelNode.Variant.CaseCaseValueCln.buildNode(
                            CaseValueNode.Variant.EnumConstantNameExpCln
                            .buildNode(
                                EnumConstantNameNode.Variant
                                .FieldName.buildNode(
                                    FieldNameNode.Variant.Identifier.buildNode(
                                        IdentifierNode.Variant
                                        .Builtin.buildNode(
                                            cmn.sc.name.getValue())))))),
                    BlockStatementsNode.Variant
                    .BlockStatementBlockStatementBlockTypeScope.buildNode(
                        callAndReturnStatements)));
          }
          SwitchStatementNode switchStmt = SwitchStatementNode.Variant
              .SwitchLpExpressionRpSwitchBlock.buildNode(
                  ExpressionNode.Variant.ConditionalExpression.buildNode(
                      ExpressionAtomNode.Variant.This.buildNode()),
                  SwitchBlockNode.Variant.LcSwitchBlockStatementGroupRc
                  .buildNode(cases.build()));

          // Insert the switch
          Optional<MethodBodyNode> methodBodyOpt =
              decl.finder(MethodBodyNode.class)
              .exclude(J8NodeType.MethodBody)
              .findOne();
          Preconditions.checkState(methodBodyOpt.isPresent());
          MethodBodyNode methodBody = methodBodyOpt.get();
          Optional<BlockStatementsNode> methodBodyBlockOpt = methodBody
              .finder(BlockStatementsNode.class)
              .exclude(BlockStatementsNode.class)
              .findOne();
          if (!methodBodyBlockOpt.isPresent()) {
            // TODO: handle case where method is abstract in enum and overridden
            // in every constant.
            continue;
          }
          BlockStatementsNode methodBodyBlock = methodBodyBlockOpt.get();
          switch (methodBodyBlock.getVariant()) {
            case BlockTypeScope:
              methodBodyBlock.setVariant(
                  BlockStatementsNode.Variant
                  .BlockStatementBlockStatementBlockTypeScope);
              //$FALL-THROUGH$
            case BlockStatementBlockStatementBlockTypeScope:
              methodBodyBlock.add(
                  0,
                  BlockStatementNode.Variant.Statement.buildNode(
                      StatementNode.Variant.SwitchStatement.buildNode(
                          switchStmt)));
              break;
          }
        }

        break;
      }
      // TODO: rewrite free fields and this.field uses
      // TODO: rewrite method uses.  private methods become statics.
      // TODO: rewrite super calls.
      default:
        break;
    }
    return ProcessingStatus.CONTINUE;
  }

  @Override
  protected ProcessingStatus postvisit(
      J8BaseNode node, @Nullable SList<Parent> pathFromRoot) {

    ProcessingStatus ps = maybeRewrite(node);
    if (!ProcessingStatus.CONTINUE.equals(ps)) {
      return ps;
    }

    if (node instanceof J8TypeDeclaration) {
      J8TypeDeclaration decl = (J8TypeDeclaration) node;
      TypeInfo ti = decl.getDeclaredTypeInfo();
      if (ti != null) {
        typesInScope.remove(typesInScope.size() - 1);
        if (node.getNodeType() == J8NodeType.EnumDeclaration) {
          enumStates.remove(enumStates.size() - 1);
        }
      }
    }
    return ProcessingStatus.CONTINUE;
  }

  /**
   * State collected about an enum during processing.
   */
  private final class EnumState {
    final TypeInfo ti;
    final List<SpecialConstant> specialConstants = Lists.newArrayList();
    final Map<Name, Name> specializedNameToUnspecialized = Maps.newHashMap();

    EnumState(TypeInfo ti) {
      this.ti = ti;
    }

    @SuppressWarnings("synthetic-access")
    private String noncollidingIdentifier(String constName, Name nm) {
      String reservedIdentifier;
      if (nm.type == Name.Type.METHOD
          && Name.isSpecialMethodIdentifier(nm.identifier)) {
        return nm.identifier;
      } else {
        String baseIdentifier = constName
            + "__" + nm.identifier;
        for (int counter = -1;; ++counter) {
          reservedIdentifier = counter < 0
              ? baseIdentifier
              : baseIdentifier + "__" + counter;
          if (nameExclusions.add(reservedIdentifier)) {
            return reservedIdentifier;
          }
          Preconditions.checkState(counter != Integer.MAX_VALUE);
        }
      }
    }

    void reserveNameFor(String constName, TypeInfo innerTypeInfo) {
      String reservedIdentifier = noncollidingIdentifier(
          constName, innerTypeInfo.canonName);
      Name unspecializedInnerTypeName = ti.canonName.child(
          reservedIdentifier, innerTypeInfo.canonName.type);
      this.specializedNameToUnspecialized.put(
          innerTypeInfo.canonName, unspecializedInnerTypeName);
    }

    void reserveNameFor(String constName, MemberInfo mi) {
      Preconditions.checkArgument(!Modifier.isAbstract(mi.modifiers));
      String reservedIdentifier = noncollidingIdentifier(
          constName, mi.canonName);
      MemberInfo staticPrivateMember;
      int staticPrivateModifiers =
          (mi.modifiers & ~(Modifier.PROTECTED | Modifier.PUBLIC))
          | Modifier.STATIC | Modifier.PRIVATE;
      if (mi instanceof CallableInfo) {
        CallableInfo ci = (CallableInfo) mi;
        @SuppressWarnings("synthetic-access")
        Name methodName = variantPool.allocateVariant(
            ti.canonName, reservedIdentifier);
        CallableInfo staticPrivateCallable = new CallableInfo(
            staticPrivateModifiers,
            methodName,
            // HACK: We're translating parameters without creating type entries.
            // TODO: document the passes we need to rerun after this one
            // because of this dodginess.
            Lists.transform(
                ci.typeParameters,
                new Function<Name, Name>() {

                  @Override
                  public Name apply(Name paramOnSpecializedType) {
                    return methodName.child(
                        paramOnSpecializedType.identifier,
                        paramOnSpecializedType.type);
                  }
                }),
            ci.isInitializer
            );
        // TODO: If we're just going to blow away name and type metadata,
        // can we skip this?
        staticPrivateCallable.setVariadic(ci.isVariadic());
        staticPrivateCallable.setSynthetic(ci.isSynthetic());
        staticPrivateCallable.setIsBridge(ci.isBridge());
        // We shouldn't need to worry about the specialized type showing
        // up in the return type or parameter types, since that type is
        // unmentionable.
        staticPrivateCallable.setFormalTypes(ci.getFormalTypes());
        staticPrivateCallable.setDescriptor(ci.getDescriptor());
        staticPrivateCallable.setReturnType(ci.getReturnType());

        staticPrivateMember = staticPrivateCallable;
      } else {
        assert mi instanceof FieldInfo;
        FieldInfo fi = (FieldInfo) mi;
        FieldInfo staticPrivateField = new FieldInfo(
            staticPrivateModifiers,
            ti.canonName.child(reservedIdentifier, Name.Type.FIELD)
            );
        staticPrivateField.setValueType(fi.getValueType());
        staticPrivateMember = staticPrivateField;
      }
      ti.addSyntheticMember(staticPrivateMember);
      Name dupe = specializedNameToUnspecialized.put(
          mi.canonName, staticPrivateMember.canonName);
      Preconditions.checkState(dupe == null);
    }
  }

  /**
   * Information about a specialized constant.
   * A specialized constant is a EnumConstant declaration with a class body.
   */
  private static final class SpecialConstant {
    final TypeInfo ti;
    final IdentifierNode name;
    final ClassBodyNode classBody;

    SpecialConstant(TypeInfo ti, IdentifierNode name, ClassBodyNode classBody) {
      this.ti = ti;
      this.name = name;
      this.classBody = classBody;
    }
  }

  private void excludeNamesThatMayBeInScope(J8BaseNode node) {
    if (node instanceof J8TypeDeclaration) {
      TypeInfo ti = ((J8TypeDeclaration) node).getDeclaredTypeInfo();

      // Exclude inherited members.
      for (MemberInfo mi : ti.transitiveMembers(infoPool.typePool.r)) {
        nameExclusions.add(mi.canonName.identifier);
      }
    } else if (node instanceof IdentifierNode) {
      // Covers local variables.
      nameExclusions.add(node.getValue());
    }
  }


  final class Migration {
    final EnumState enumState;

    Migration(EnumState enumState) {
      this.enumState = enumState;
    }
    // TODO: use a bridge to clear out type and name metadata.

    ClassBodyDeclarationNode migrate(
        ClassBodyDeclarationNode toImport) {

      switch (toImport.getVariant()) {
        case ClassMemberDeclaration:
          migrate(
              toImport.firstChildWithType(ClassMemberDeclarationNode.class));
          return toImport;
        case InstanceInitializer:
          // No name to handle, but we need to make this a static initializer.
          // Rely on the renaming of expression and statement parts within.
          toImport.setVariant(
              ClassBodyDeclarationNode.Variant.StaticInitializer);
          int initIndex = toImport
              .finder(InstanceInitializerNode.class)
              .indexOf();
          if (initIndex >= 0) {
            InstanceInitializerNode init =
                (InstanceInitializerNode) toImport.getChild(initIndex);
            toImport.replace(
                initIndex,
                StaticInitializerNode.Variant.StaticBlock.buildNode(
                    init.getChildren()));
          }
          return toImport;
        case StaticInitializer:
        case ConstructorDeclaration:
          // Should not appear in inner classes.
          break;
      }
      throw new AssertionError(toImport.getVariant());
    }

    ClassMemberDeclarationNode migrate(
        ClassMemberDeclarationNode toImport) {
      // Fixup declaration names so that rerunning the inference passes
      // infers the right things.
      switch (toImport.getVariant()) {
        case ClassDeclaration:
        case InterfaceDeclaration: {
          Optional<J8TypeDeclaration> declOpt =
              Mixins.getInnerTypeDeclaration(toImport);
          if (declOpt.isPresent()) {
            J8TypeDeclaration decl = declOpt.get();
            ensurePrivateStatic((J8BaseInnerNode) decl);
            TypeInfo ti = decl.getDeclaredTypeInfo();
            if (ti != null) {
              Name migratedName = enumState.specializedNameToUnspecialized.get(
                  ti.canonName);
              if (migratedName == null) {
                error(
                    decl, "Missing target name for migrated class: "
                    + ti.canonName);
                return toImport;
              }
              Optional<SimpleTypeNameNode> nameNodeOpt =
                  Mixins.getDeclaredTypeName(decl);
              if (nameNodeOpt.isPresent()) {
                SimpleTypeNameNode nameNode = nameNodeOpt.get();
                IdentifierNode ident =
                    nameNode.firstChildWithType(IdentifierNode.class);
                if (ident != null) {
                  ident.setValue(migratedName.identifier);
                  return toImport;
                }
              }
              error(decl, "Failed to fixup name");
            }
          } else {
            error(toImport, "Missing type declaration");
          }
          return toImport;
        }
        case FieldDeclaration: {
          FieldDeclarationNode fieldDecl = toImport.firstChildWithType(
              FieldDeclarationNode.class);
          if (fieldDecl == null) {
            error(toImport, "Missing field declaration");
            return toImport;
          }
          ensurePrivateStatic(fieldDecl);
          for (VariableDeclaratorIdNode decl
              : fieldDecl.finder(VariableDeclaratorIdNode.class)
              .exclude(J8NodeType.VariableInitializer)
              .find()) {
            Name declared = decl.getDeclaredExpressionName();
            if (declared == null) {
              error(decl, "Missing name meta-info");
              continue;
            }
            Name migrated = enumState.specializedNameToUnspecialized.get(
                declared);
            if (migrated == null) {
              error(
                  decl, "Never reserved a name for migrated field " + declared);
              continue;
            }
            IdentifierNode ident = decl.firstChildWithType(
                IdentifierNode.class);
            if (ident == null) {
              error(decl, "Missing identifier for field " + declared);
              continue;
            }
            ident.setValue(migrated.identifier);
          }
          return toImport;
        }
        case MethodDeclaration: {
          MethodDeclarationNode methodDecl = toImport.firstChildWithType(
              MethodDeclarationNode.class);
          if (methodDecl == null) {
            error(toImport, "Missing method declaration");
            return toImport;
          }
          MemberInfo mi = methodDecl.getMemberInfo();
          if (mi == null) {
            error(methodDecl, "Missing member info for method declaration");
            return toImport;
          }
          Name migrated = enumState.specializedNameToUnspecialized.get(
              mi.canonName);
          if (migrated == null) {
            error(methodDecl, "Never reserved name for migrated method");
            return toImport;
          }
          MethodHeaderNode header = methodDecl.firstChildWithType(
              MethodHeaderNode.class);
          if (header == null) {
            error(methodDecl, "Missing method header");
            return toImport;
          }
          MethodDeclaratorNode declarator = header.firstChildWithType(
              MethodDeclaratorNode.class);
          if (declarator == null) {
            error(header, "Missing method declarator");
            return toImport;
          }
          MethodNameNode name = declarator.firstChildWithType(
              MethodNameNode.class);
          if (name == null) {
            error(declarator, "Missing method name");
            return toImport;
          }
          IdentifierNode ident = name.firstChildWithType(IdentifierNode.class);
          if (ident == null) {
            error(name, "Missing identifier");
            return toImport;
          }
          ensurePrivateStatic(methodDecl);
          ident.setValue(migrated.identifier);
          return toImport;
        }
        case Sem:
          return toImport;
        default:
          break;
      }
      throw new AssertionError(toImport.getVariant());
    }

    private void ensurePrivateStatic(J8BaseInnerNode decl) {
      // The only thing that can precede modifiers are doc comments.
      int nChildren = decl.getNChildren();
      int modifierStart = 0;
      while (modifierStart < nChildren) {
        if (decl.getChild(modifierStart).getNodeType()
            == J8NodeType.JavaDocComment) {
          ++modifierStart;
        } else {
          break;
        }
      }
      // Scan the modifiers.
      int modifiers = 0;
      for (int modifierEnd = modifierStart;
           modifierEnd < nChildren; ++modifierEnd) {
        J8BaseNode node = decl.getChild(modifierEnd);
        if (node.getNodeType() != J8NodeType.Modifier) {
          break;
        }
        ModifierNode modNode = (ModifierNode) node;
        int modBits = ModifierNodes.modifierBits(modNode.getVariant());
        // public -> private, ditto for protected
        if ((modBits & (Modifier.PUBLIC | Modifier.PROTECTED)) != 0) {
          modNode.setVariant(ModifierNode.Variant.Private);
          modBits = Modifier.PRIVATE;
        }
        modifiers |= modBits;
        if (isAnnotation(JAVA_LANG_OVERRIDE, modNode)) {
          decl.remove(modifierEnd);
          --modifierEnd;
          --nChildren;
          continue;
        }
      }
      // Make sure static and private are present.
      if ((modifiers & Modifier.STATIC) == 0) {
        decl.add(modifierStart, ModifierNode.Variant.Static.buildNode());
      }
      if ((modifiers & Modifier.PRIVATE) == 0) {
        decl.add(modifierStart, ModifierNode.Variant.Private.buildNode());
      }
    }
  }

  static boolean isAnnotation(Name name, ModifierNode mod) {
    if (mod.getVariant() == ModifierNode.Variant.Annotation) {
      Optional<TypeNameNode> annotationNameNodeOpt = mod
          .finder(TypeNameNode.class)
          .exclude(J8NodeType.ElementValue, J8NodeType.ElementValuePairList)
          .findOne();
      if (annotationNameNodeOpt.isPresent()) {
        TypeNameNode annotationNameNode = annotationNameNodeOpt.get();
        TypeInfo ati = annotationNameNode.getReferencedTypeInfo();
        return ati != null && ati.canonName.equals(name);
      }
    }
    return false;
  }

  static final Name JAVA_LANG_OVERRIDE = JavaLang.JAVA_LANG.child(
      "Override", Name.Type.CLASS);

  static final class CollectedOverrides {

    private final Table<String, MethodDescriptor, MethodNodeAndMetadata> methods
        = HashBasedTable.create();

    static final class MethodNodeAndMetadata {
      final CallableInfo callableInfo;
      MethodDeclarationNode declNode;
      /** Constants that override decleNode and the overriding declaration. */
      final List<ConstantAndMigratedName> specialConstantsOverriding
          = Lists.newArrayList();

      MethodNodeAndMetadata(CallableInfo callableInfo) {
        this.callableInfo = callableInfo;
      }
    }

    static final class ConstantAndMigratedName {
      final SpecialConstant sc;
      final Name migratedName;

      ConstantAndMigratedName(SpecialConstant sc, Name migratedName) {
        this.sc = sc;
        this.migratedName = migratedName;
      }

      @Override
      public String toString() {
        return "(" + sc.ti.canonName + " / " + migratedName + ")";
      }
    }

    MethodNodeAndMetadata get(CallableInfo ci) {
      MethodDescriptor desc = ci.getDescriptor();
      MethodNodeAndMetadata methodAndMetadata = methods.get(
          ci.canonName.identifier, desc);
      if (methodAndMetadata == null) {
        methodAndMetadata = new MethodNodeAndMetadata(ci);
        methods.put(ci.canonName.identifier, desc, methodAndMetadata);
      }
      return methodAndMetadata;
    }

    Iterable<MethodNodeAndMetadata> allMethods() {
      return methods.values();
    }
  }

  private static Optional<MethodDeclarationNode> asMethodDeclaration(
      ClassBodyDeclarationNode cbd) {
    if (cbd.getVariant()
        == ClassBodyDeclarationNode.Variant.ClassMemberDeclaration) {
      ClassMemberDeclarationNode member = cbd.firstChildWithType(
          ClassMemberDeclarationNode.class);
      if (member != null
          && (ClassMemberDeclarationNode.Variant.MethodDeclaration
              == member.getVariant())) {
        return Optional.fromNullable(
            member.firstChildWithType(MethodDeclarationNode.class));
      }
    }
    return Optional.absent();
  }

  /**
   * @param mdn An example of an override of the given method.
   */
  private MethodDeclarationNode callToSuper(
      CallableInfo ci, MethodDeclarationNode mdn) {
    MethodHeaderNode exHeader = Preconditions.checkNotNull(
        mdn.firstChildWithType(MethodHeaderNode.class));
    MethodDeclaratorNode exDeclarator = Preconditions.checkNotNull(
        exHeader.firstChildWithType(MethodDeclaratorNode.class));
    FormalParameterListNode exFormals = exDeclarator.firstChildWithType(
        FormalParameterListNode.class);
    ThrowsNode exThrowsClause = exHeader.firstChildWithType(ThrowsNode.class);
    ImmutableList<VariableDeclaratorIdNode> exFormalNames = exFormals != null
        ? exFormals.finder(VariableDeclaratorIdNode.class).find()
        : ImmutableList.of();

    TypeNodeFactory typeNodeFactory = new TypeNodeFactory(
        logger, infoPool.typePool);

    ImmutableList.Builder<ModifierNode> modifiers = ImmutableList.builder();
    modifiers.add(
        ModifierNode.Variant.Annotation.buildNode(
            AnnotationNode.Variant.MarkerAnnotation.buildNode(
                MarkerAnnotationNode.Variant.AtTypeName.buildNode(
                    toTypeNameNode(JAVA_LANG_OVERRIDE)))));
    for (int mods = ci.modifiers, bit; mods != 0; mods &= ~bit) {
      bit = mods & -mods;
      ModifierNode.Variant v = ModifierNodes.modifierVariant(bit);
      if (v != null) {
        modifiers.add(v.buildNode());
      }
    }

    ResultNode result;
    boolean isVoid;
    {
      TypeSpecification rt = ci.getReturnType();
      isVoid = StaticType.T_VOID.typeSpecification.equals(rt);
      if (isVoid) {
        result = ResultNode.Variant.Void.buildNode();
      } else {
        StaticType retType = infoPool.typePool.type(rt, null, logger);
        result = ResultNode.Variant.UnannType.buildNode(
            UnannTypeNode.Variant.NotAtType.buildNode(
                typeNodeFactory.toTypeNode(retType)));
      }
    }

    ImmutableList.Builder<J8BaseNode> callChildren =
        ImmutableList.<J8BaseNode>builder()
        .add(ExpressionAtomNode.Variant.Super.buildNode());
    if (!ci.typeParameters.isEmpty()) {
      callChildren.add(
          TypeArgumentsNode.Variant.LtTypeArgumentListGt.buildNode(
              TypeArgumentListNode.Variant.TypeArgumentComTypeArgument
              .buildNode(
                  Lists.<Name, J8BaseNode>transform(
                      ci.typeParameters,
                      new Function<Name, TypeArgumentNode>() {
                        @Override
                        public TypeArgumentNode apply(Name typeParamName) {
                          return TypeArgumentNode.Variant.ReferenceType
                              .buildNode(
                                  ReferenceTypeNode.Variant.TypeVariable
                                  .buildNode(
                                      TypeVariableNode.Variant
                                      .AnnotationIdentifier.buildNode(
                                          toIdentifierNode(typeParamName))));
                        }
                      }))));
    }
    callChildren.add(
        MethodNameNode.Variant.Identifier.buildNode(
            toIdentifierNode(ci.canonName)));
    if (!exFormalNames.isEmpty()) {
      callChildren.add(
          ArgumentListNode.Variant.ExpressionComExpression
          .buildNode(
              Lists.<VariableDeclaratorIdNode, ExpressionNode>transform(
                  exFormalNames,
                  new Function<VariableDeclaratorIdNode, ExpressionNode>() {
                    @Override
                    public ExpressionNode apply(VariableDeclaratorIdNode d) {
                      IdentifierNode id = d.firstChildWithType(
                          IdentifierNode.class);
                      return ExpressionNode.Variant.ConditionalExpression
                          .buildNode(
                              ExpressionAtomNode.Variant.Local.buildNode(
                                  LocalNameNode.Variant.Identifier.buildNode(
                                      IdentifierNode.Variant.Builtin.buildNode(
                                          id.getValue())
                                      .setNamePartType(Name.Type.LOCAL))
                                  ));
                    }
                  })));
    }
    PrimaryNode superCall = PrimaryNode.Variant.MethodInvocation.buildNode(
        callChildren.build());

    StatementNode superCallStatement;
    if (isVoid) {
      superCallStatement = StatementNode.Variant.ExpressionStatement
          .buildNode(
              ExpressionStatementNode.Variant.StatementExpressionSem
              .buildNode(
                  StatementExpressionNode.Variant.MethodInvocation
                  .buildNode(
                      MethodInvocationNode.Variant.ExplicitCallee.buildNode(
                          superCall))));
    } else {
      superCallStatement = StatementNode.Variant.ReturnStatement
          .buildNode(
              ReturnStatementNode.Variant.ReturnExpressionSem
              .buildNode(
                  ExpressionNode.Variant.ConditionalExpression
                  .buildNode(superCall)));
    }

    ImmutableList.Builder<J8BaseNode> methodDeclaratorParts =
        ImmutableList.builder();
    methodDeclaratorParts.add(
        MethodNameNode.Variant.Identifier.buildNode(
            toIdentifierNode(ci.canonName)));
    if (exFormals != null) {
      // TODO: make sure we use long names so this isn't dependent on
      // imports.
      // Similarly for throws clause.
      methodDeclaratorParts.add(exFormals.deepClone());
    }
    MethodDeclaratorNode declarator = MethodDeclaratorNode.Variant
        .MethodNameLpFormalParameterListRpDims
        .buildNode(methodDeclaratorParts.build());

    ImmutableList.Builder<J8BaseNode> headerParts = ImmutableList.builder();
    if (!ci.typeParameters.isEmpty()) {
      headerParts.add(
          TypeParametersNode.Variant.LtTypeParameterListGt
          .buildNode(
              TypeParameterListNode.Variant.TypeParameterComTypeParameter
              .buildNode(
                  Lists.<Name, TypeParameterNode>transform(
                      ci.typeParameters,
                      new Function<Name, TypeParameterNode>() {
                        @Override
                        public TypeParameterNode apply(Name paramName) {
                          // TODO: capture type bound info in CallableInfo
                          TypeBoundNode typeBound = null;  // TODO
                          if (true) { throw new AssertionError("TODO"); }
                          return TypeParameterNode.Variant.TypeParameter
                              .buildNode(
                                  SimpleTypeNameNode.Variant.Identifier
                                  .buildNode(toIdentifierNode(paramName)),
                                  typeBound);
                        }
                      }))));
    }
    headerParts.add(result)
        .add(declarator);
    if (exThrowsClause != null) {
      // TODO: capture throws info in CallableInfo
      ThrowsNode throwsClause = exThrowsClause.deepClone();
      headerParts.add(throwsClause);
    }
    MethodHeaderNode header = MethodHeaderNode.Variant
        .TypeParametersAnnotationResultMethodDeclaratorThrows
        .buildNode(headerParts.build());

    return MethodDeclarationNode.Variant.Declaration
        .buildNode(
            ImmutableList.<J8BaseNode>builder()
            .addAll(modifiers.build())
            .add(header)
            .add(
                MethodBodyNode.Variant.Block.buildNode(
                    BlockNode.Variant.LcBlockStatementsRc.buildNode(
                        BlockStatementsNode.Variant.BlockStatementBlockStatementBlockTypeScope
                        .buildNode(
                            BlockStatementNode.Variant.Statement.buildNode(
                                superCallStatement
                                )
                            ))))
            .build());
  }

  private static TypeNameNode toTypeNameNode(Name typeName) {
    Preconditions.checkArgument(typeName.type == Name.Type.CLASS);
    TypeNameNode result;
    if (typeName.parent == null
        || Name.DEFAULT_PACKAGE.equals(typeName.parent)) {
      result = TypeNameNode.Variant.Identifier.buildNode(
          toIdentifierNode(typeName));
    } else {
      result = TypeNameNode.Variant.PackageOrTypeNameDotIdentifier
          .buildNode(
              toPackageOrTypeNameNode(typeName.parent),
              toIdentifierNode(typeName));
    }
    return result;
  }

  private static PackageOrTypeNameNode toPackageOrTypeNameNode(
      Name typeOrPackageName) {
    PackageOrTypeNameNode result;
    if (typeOrPackageName.parent == null
        || Name.DEFAULT_PACKAGE.equals(typeOrPackageName.parent)) {
      result = PackageOrTypeNameNode.Variant.Identifier.buildNode(
          toIdentifierNode(typeOrPackageName));
    } else {
      result = PackageOrTypeNameNode.Variant.PackageOrTypeNameDotIdentifier
          .buildNode(
              toPackageOrTypeNameNode(typeOrPackageName.parent),
              toIdentifierNode(typeOrPackageName));
    }
    return result;
  }

  static IdentifierNode toIdentifierNode(Name nm) {
    IdentifierNode node = IdentifierNode.Variant.Builtin
        .buildNode(nm.identifier);
    node.setNamePartType(nm.type);
    return node;
  }
}
