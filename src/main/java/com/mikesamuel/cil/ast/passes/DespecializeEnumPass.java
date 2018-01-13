package com.mikesamuel.cil.ast.passes;

import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import javax.annotation.Nullable;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicates;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Table;
import com.mikesamuel.cil.ast.j8.AnnotationNode;
import com.mikesamuel.cil.ast.j8.ArgumentListNode;
import com.mikesamuel.cil.ast.j8.BlockNode;
import com.mikesamuel.cil.ast.j8.BlockStatementNode;
import com.mikesamuel.cil.ast.j8.BlockStatementsNode;
import com.mikesamuel.cil.ast.j8.CaseValueNode;
import com.mikesamuel.cil.ast.j8.ClassBodyNode;
import com.mikesamuel.cil.ast.j8.ClassMemberDeclarationNode;
import com.mikesamuel.cil.ast.j8.ClassOrInterfaceTypeToInstantiateNode;
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
import com.mikesamuel.cil.ast.j8.J8NodeVariant;
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
import com.mikesamuel.cil.ast.j8.ThrowStatementNode;
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
import com.mikesamuel.cil.ast.j8.UnqualifiedClassInstanceCreationExpressionNode;
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
import com.mikesamuel.cil.ast.mixins.TypeScope;
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
 *
 * <h3>Algorithm</h3>
 * <ol>
 *   <li>Scan entire class including super-types so that we can generate a set
 *   of identifiers.  When we auto-generate synthetic private members that do
 *   not overlap with this set.
 *   <li>For each {@code enum Base}
 *   <ol>
 *     <li>Identify all specialized constants like
 *       <code>enum Base { SPECIAL() { ... } }</code>.
 *     <li>Reserve names for members in specialized constants so that we can
 *       migrate them into the base class.
 *     <li>Recurse to children to rewrite intra-class
 *       field and method accesses inside constant classes.
 *       <ul>
 *         <li>Uses of fields and methods are rewritten based on the previously
 *           reserved names.
 *         <li>Uses of {@code this} are rewritten to point to the constant via
 *           {@code pkg.Base.SPECIAL}.
 *         <li>Uses of {@code super.method} are left as-is if the method is not
 *           in base.  Otherwise, we reserve a {@code base__method} and use
 *           that.  (See the third migrate step below).
 *       </ul>
 *     <li>Collate all methods in special constant classes and remove those
 *       class bodies.
 *     <li>Migrate overridden methods
 *     <li>Migrate all special constant's members into the base enum.
 *     <li>Migrate all methods that were overridden in the base enum by
 *       methods in constant members to {@code base__} methods.
 *     <li>Splice {@code switch(this)} into overridden methods in base to
 *       call out to specialized versions.
 *   </ol>
 *   <li>Remove all type and name metadata from the output compilation units.
 * </ol>
 */
public final class DespecializeEnumPass extends AbstractRewritingPass {
  private final MethodVariantPool variantPool;
  private final MemberInfoPool infoPool;
  private final TypeNodeFactory typeNodeFactory;

  /** */
  public DespecializeEnumPass(
      Logger logger, MemberInfoPool infoPool, MethodVariantPool variantPool) {
    super(logger);
    this.infoPool = infoPool;
    this.variantPool = variantPool;
    this.typeNodeFactory = new TypeNodeFactory(logger, infoPool.typePool);
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
  private NameAllocator nameAllocator = null;


  @Override
  protected ProcessingStatus previsit(
      J8BaseNode node, @Nullable SList<Parent> pathFromRoot) {
    if (node instanceof J8FileNode) {
      Preconditions.checkState(enumStates.isEmpty() && typesInScope.isEmpty());
      nameAllocator = NameAllocator.create(
          ImmutableList.of(node), Predicates.alwaysFalse(), infoPool.typePool.r);
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
              for (ClassMemberDeclarationNode cbd
                   : body.finder(ClassMemberDeclarationNode.class)
                         .exclude(ClassMemberDeclarationNode.class)
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

            if (!es.specialConstants.isEmpty()) {
              EnumBodyDeclarationsNode ebd = getEnumBodyDeclarationsNode(
                  (EnumDeclarationNode) node);
              if (ebd != null) {
                for (int i = 0, n = ebd.getNChildren(); i < n; ++i) {
                  ClassMemberDeclarationNode cbd =
                      (ClassMemberDeclarationNode) ebd.getChild(i);
                  Optional<MethodDeclarationNode> mdOpt =
                      asMethodDeclaration(cbd);
                  if (mdOpt.isPresent()) {
                    MethodDeclarationNode md = mdOpt.get();
                    CallableInfo ci = md.getCallableInfo();
                    if (ci != null
                        // TODO: is !abstract sufficient here or do we need
                        // !abstract || has default body.
                        && !Modifier.isAbstract(ci.modifiers)
                        && !Modifier.isStatic(ci.modifiers)) {
                      es.extractMethodBodyToBaseMethod(md, ci);
                    }
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
    switch (node.getNodeType()) {
      case Primary:
      case ExpressionAtom: {
        int nEnumStates = enumStates.size();
        if (nEnumStates == 0) { break; }
        EnumState es = enumStates.get(nEnumStates - 1);

        J8NodeVariant v = node.getVariant();
        if (v == ExpressionAtomNode.Variant.FreeField
            || v == PrimaryNode.Variant.FieldAccess) {
          FieldNameNode nameNode =
              node.firstChildWithType(FieldNameNode.class);
          if (nameNode != null) {
            Name name = nameNode.getReferencedExpressionName();
            Name unspecialized = es.specializedNameToUnspecialized.get(name);
            if (unspecialized != null) {
              IdentifierNode id = nameNode.firstChildWithType(
                  IdentifierNode.class);
              if (id == null) {
                error(
                    nameNode,
                    "Missing identifier for field access " + node);
                break;
              }
              id.setValue(unspecialized.identifier);
              if (v == PrimaryNode.Variant.FieldAccess) {
                // Turn it into a free field access so that we can
                // TODO: figure out how I was going to end this sentence.
                ExpressionAtomNode atom =
                    ExpressionAtomNode.Variant.FreeField.buildNode(nameNode);
                atom.setSourcePosition(node.getSourcePosition());
                return ProcessingStatus.replace(atom);
              }
            }
          }
        } else if (v == ExpressionAtomNode.Variant.MethodInvocation
                   || v == PrimaryNode.Variant.MethodInvocation) {
          MethodNameNode nameNode =
              node.firstChildWithType(MethodNameNode.class);
          if (nameNode != null) {
            Name name = nameNode.getReferencedExpressionName();

            IdentifierNode id = nameNode.firstChildWithType(
                IdentifierNode.class);
            if (id == null) {
              error(
                  nameNode,
                  "Missing identifier for method invocation " + node);
              break;
            }

            int targetIndex = 0;
            J8BaseNode target = null;
            if (v == PrimaryNode.Variant.MethodInvocation) {
              // Make sure that the target is the outer class.
              // This preserves any explicit type parameters.
              targetIndex = node.finder(J8BaseNode.class).indexOf();
              target = node.getChild(targetIndex);
              if (!(target.getNodeType() == J8NodeType.ExpressionAtom
                    || target.getNodeType() == J8NodeType.Primary)) {
                target = null;
                targetIndex = -1;
              }
            }
            Name newName = null;
            boolean isSuper = target != null
                && target.getVariant() == ExpressionAtomNode.Variant.Super;

            Name unspecialized = es.specializedNameToUnspecialized.get(name);
            BaseMethod baseMethod = null;
            if (unspecialized == null) {
              baseMethod = es.enumMethodNameToBase.get(name);
            }
            if (unspecialized != null || baseMethod != null) {
              if (unspecialized != null) {
                newName = unspecialized;
                // There is only one reference (per java.lang.Class instance
                // loaded from the source file being processed) that refers to
                // an instance of the specialized constant class, so we don't
                // care too much about the left hand side unless it side
                // effects.
                // TODO: care about side effects
                if (target != null) {
                  // Since it is static, rewrite `this` to the enum name.
                  ExpressionAtomNode staticReceiver = (ExpressionAtomNode)
                      // If we just replace this and super with references
                      // to the enum value, we get equivalent behavior.
                      // This solves the problem of side-effecting
                      // receiver expressions like
                      //     methodThatSideEffectAndReturnsArg(this).foo()
                      replaceBareThisAndSuper(target);

                  ((PrimaryNode) node).replace(targetIndex, staticReceiver);
                }
              } else {
                Preconditions.checkNotNull(baseMethod);
                if (target != null
                    && (target.getVariant()
                        == ExpressionAtomNode.Variant.Super)) {
                  newName = baseMethod.baseMethodName;
                  target.setVariant(ExpressionAtomNode.Variant.This);
                }
              }
              if (newName != null) {
                id.setValue(newName.identifier);
              }
            } else if (isSuper) {
              CallableInfo ci = nameNode.getCallableInfo();
              TypeInfo ti = typesInScope.get(typesInScope.size() - 1);
              SpecialConstant sc = es.specialConstantForClass(ti.canonName);
              if (sc != null && sc.ti.canonName.equals(ti.canonName)) {
                // We know that the method is not on the unspecialized enum
                // or any of the specialized instance, so the name must
                // refer to a method on a super-type: Enum or Object.
                // We need to make sure that we have a handle to the super
                // method.
                //   { ... super.toString() ... }
                // =>
                //   { ... E.C.super__toString() ... }
                //   private final String super__toString() {
                //     return super.toString();
                //   }
                // so that we can effect a super call even from a static
                // context.
                Name trampoline = getSuperTrampoline(es, ci);
                id.setValue(trampoline.identifier);
                // TODO: can we consolidate this code with that in the branch
                // above?
                ExpressionAtomNode staticReceiver = (ExpressionAtomNode)
                    replaceBareThisAndSuper(target);
                ((PrimaryNode) node).replace(targetIndex, staticReceiver);
              }
            }
          }
        } else if ((v == ExpressionAtomNode.Variant.This
                    && node.getNChildren() == 0)
                   || v == ExpressionAtomNode.Variant.Super) {
          // Unqualified `this`, or equivalent reference.
          // We don't need to worry about qualified `this` in specialized
          // enum instances since the specialized type is not mentionable.
          int nTypesInScope = typesInScope.size();
          if (nTypesInScope != 0) {
            TypeInfo type = typesInScope.get(nTypesInScope - 1);
            if (type.superType.isPresent() && type.superType.get().rawName
                .equals(es.ti.canonName)) {
              // This refers to a specialized constant.
              // TODO: this is almost certainly wrong.  Keep a map from anonymous
              // class names to special constants in EnumState.
              SpecialConstant sc = es.specialConstantForClass(type.canonName);
              ExpressionAtomNode e = (ExpressionAtomNode) node;
              e.setVariant(ExpressionAtomNode.Variant.Parenthesized);
              e.add(
                  ExpressionNode.Variant.ConditionalExpression.buildNode(
                      PrimaryNode.Variant.FieldAccess.buildNode(
                          ExpressionAtomNode.Variant.StaticMember.buildNode(
                              typeNodeFactory.toTypeNameNode(es.ti)),
                          FieldNameNode.Variant.Identifier.buildNode(
                              sc.name.deepClone()))));
            }
          }
        }
        break;
      }
      default:
        break;
    }
    return ProcessingStatus.CONTINUE;
  }

  private J8BaseNode replaceBareThisAndSuper(J8BaseNode node) {
    if (node instanceof TypeScope) {
      // Don't descend into inner classes.
      return node;
    }
    if (node.getNodeType() == J8NodeType.ExpressionAtom) {
      switch (((ExpressionAtomNode) node).getVariant()) {
        case This:
        case Super:
          if (node.getNChildren() != 0) {
            // There is no way to mention a specialized enum instance
            // via MyEnum.INSTANCE.this since the actual class is anonymous
            // so we don't do anything with qualified `this`.
            break;
          }
          EnumState enumState = enumStates.get(enumStates.size() - 1);
          TypeInfo referencedType = typesInScope.get(typesInScope.size() - 1);
          SpecialConstant constant = enumState.specialConstantForClass(
              referencedType.canonName);
          return ExpressionAtomNode.Variant.Parenthesized.buildNode(
              ExpressionNode.Variant.ConditionalExpression.buildNode(
                  PrimaryNode.Variant.FieldAccess.buildNode(
                      ExpressionAtomNode.Variant.StaticMember.buildNode(
                          typeNodeFactory.toTypeNameNode(enumState.ti)),
                      FieldNameNode.Variant.Identifier.buildNode(
                          constant.name.deepClone()))));
        default:
          break;
      }
    }
    if (node instanceof J8BaseInnerNode) {
      J8BaseInnerNode inode = (J8BaseInnerNode) node;
      for (int i = 0, n = inode.getNChildren(); i < n; ++i) {
        J8BaseNode child = inode.getChild(i);
        J8BaseNode newChild = replaceBareThisAndSuper(child);
        if (child != newChild) {
          inode.replace(i, newChild);
        }
      }
    }
    return node;
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
        if (es.specialConstants.isEmpty()) {
          Preconditions.checkState(es.enumMethodNameToBase.isEmpty());
          break;
        }
        EnumDeclarationNode decls = (EnumDeclarationNode) node;
        EnumBodyDeclarationsNode members = getEnumBodyDeclarationsNode(decls);

        for (BaseMethod baseMethod :
             Iterables.concat(
                 es.enumMethodNameToBase.values(),
                 es.outerClassTrampolines.values())) {
          baseMethod.commit(members);
        }

        // Compile a map of method declarations to descriptors.
        // We'll use this to handle overrides of specialized enums.
        CollectedOverrides overrides = new CollectedOverrides();
        Migration mig = new Migration(es);

        for (int i = 0, n = members.getNChildren(); i < n; ++i) {
          ClassMemberDeclarationNode cbd = (ClassMemberDeclarationNode)
              members.getChild(i);
          Optional<MethodDeclarationNode> mdOpt = asMethodDeclaration(cbd);
          if (mdOpt.isPresent()) {
            MethodDeclarationNode md = mdOpt.get();
            CallableInfo ci = md.getCallableInfo();
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
            ClassMemberDeclarationNode specialDeclaration =
                (ClassMemberDeclarationNode) sc.classBody.getChild(i);
            // Migrate to a private static member.
            ClassMemberDeclarationNode privateStaticCopy =
                mig.migrate(specialDeclaration.deepClone());
            members.add(privateStaticCopy);
            // Associate with any overridden method so that we can tweak
            // methods.
            Optional<MethodDeclarationNode> mdOpt = asMethodDeclaration(
                specialDeclaration);
            if (mdOpt.isPresent()) {
              MethodDeclarationNode md = mdOpt.get();
              CallableInfo ci = md.getCallableInfo();
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
                    sci.setThrownTypes(oci.getThrownTypes());
                    sci.setVariadic(oci.isVariadic());

                    MethodDeclarationNode superCallingImpl = callToSuper(
                        sci, md);
                    members.add(
                        ClassMemberDeclarationNode.Variant.MethodDeclaration
                        .buildNode(superCallingImpl));

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

        TypeNameNode outerEnumTypeName = typeNodeFactory.toTypeNameNode(es.ti);
        for (MethodNodeAndMetadata mmd : overrides.allMethods()) {
          if (mmd.specialConstantsOverriding.isEmpty()) {
            continue;
          }

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
                    VARIABLE_DECLARATOR_TO_REFERENCE));
          }
          TypeArgumentsNode typeArguments = null;
          if (!mmd.callableInfo.typeParameters.isEmpty()) {
            typeArguments = TypeArgumentsNode.Variant.LtTypeArgumentListGt
                .buildNode(
                    TypeArgumentListNode.Variant.TypeArgumentComTypeArgument
                    .buildNode(
                        Lists.transform(
                            mmd.callableInfo.typeParameters,
                            TYPE_PARAMETER_NAME_TO_TYPE_ARGUMENT_NODE)));
          }

          // Craft a switch
          ImmutableList.Builder<J8BaseNode> cases = ImmutableList.builder();
          for (ConstantAndMigratedName cmn : mmd.specialConstantsOverriding) {
            List<J8BaseNode> callAndReturnStatements;
            PrimaryNode specializedCall =
                PrimaryNode.Variant.MethodInvocation.buildNode(
                    ExpressionAtomNode.Variant.StaticMember.buildNode(
                        outerEnumTypeName));
            if (typeArguments != null) {
              specializedCall.add(typeArguments.deepClone());
            }
            specializedCall.add(
                MethodNameNode.Variant.Identifier.buildNode(
                    IdentifierNode.Variant.Builtin.buildNode(
                        cmn.migratedName.identifier)));
            if (argumentList != null) {
              specializedCall.add(argumentList.deepClone());
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
          Optional<BlockStatementsNode>
          methodBodyBlockOpt = requireMethodBodyBlock(
              decl,
              new Supplier<Iterable<BlockStatementNode>>() {

                @Override
                public Iterable<BlockStatementNode> get() {
                  @SuppressWarnings("synthetic-access")

                  UnqualifiedClassInstanceCreationExpressionNode newError =
                      UnqualifiedClassInstanceCreationExpressionNode.Variant
                      .New.buildNode(
                          ClassOrInterfaceTypeToInstantiateNode
                          .Variant.ClassOrInterfaceTypeDiamond.buildNode(
                              typeNodeFactory.toClassOrInterfaceTypeNode(
                                  JAVA_LANG_ASSERTIONERROR)),
                          ArgumentListNode.Variant.ExpressionComExpression
                          .buildNode(
                              ExpressionNode.Variant.ConditionalExpression
                              .buildNode(
                                  ExpressionAtomNode.Variant
                                  .This.buildNode())));

                  ThrowStatementNode throwStatement =
                      ThrowStatementNode.Variant.ThrowExpressionSem.buildNode(
                          ExpressionNode.Variant.ConditionalExpression
                          .buildNode(
                              ExpressionAtomNode.Variant
                              .UnqualifiedClassInstanceCreationExpression
                              .buildNode(newError)));

                  return ImmutableList.of(
                      BlockStatementNode.Variant.Statement.buildNode(
                          StatementNode.Variant.ThrowStatement.buildNode(
                              throwStatement)));
                }

              });
          if (!methodBodyBlockOpt.isPresent()) {
            continue;
          }
          BlockStatementsNode methodBodyBlock = methodBodyBlockOpt.get();
          addStatementBeforeBlock(
              methodBodyBlock,
              BlockStatementNode.Variant.Statement.buildNode(
                  StatementNode.Variant.SwitchStatement.buildNode(
                      switchStmt)));
        }
        break;
      }
      default:
        break;
    }
    return ProcessingStatus.CONTINUE;
  }

  private static void addStatementBeforeBlock(
      BlockStatementsNode methodBodyBlock,
      BlockStatementNode stmt) {
    switch (methodBodyBlock.getVariant()) {
      case BlockTypeScope:
        methodBodyBlock.setVariant(
            BlockStatementsNode.Variant
            .BlockStatementBlockStatementBlockTypeScope);
        //$FALL-THROUGH$
      case BlockStatementBlockStatementBlockTypeScope:
        methodBodyBlock.add(0, stmt);
        return;
    }
    throw new AssertionError(methodBodyBlock);
  }

  /**
   * Makes a best effort to get the method body block, converting an abstract
   * method body into a concrete one if possible.
   */
  private Optional<BlockStatementsNode> requireMethodBodyBlock(
      MethodDeclarationNode decl,
      Supplier<? extends Iterable<BlockStatementNode>>
          defaultBlockBodySupplier) {
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
      if (MethodBodyNode.Variant.Sem == methodBody.getVariant()) {
        methodBody.setVariant(MethodBodyNode.Variant.Block);
        removeModifiers(decl, Modifier.ABSTRACT);

        Iterable<BlockStatementNode> blockBody =
            defaultBlockBodySupplier.get();

        methodBodyBlockOpt = Optional.of(
            BlockStatementsNode.Variant
            .BlockStatementBlockStatementBlockTypeScope
            .buildNode(blockBody));
        methodBody.add(
            BlockNode.Variant.LcBlockStatementsRc.buildNode(
                methodBodyBlockOpt.get()));
      } else {
        error(
            methodBody, "method body has no statements");
      }
    }
    return methodBodyBlockOpt;
  }

  private static EnumBodyDeclarationsNode getEnumBodyDeclarationsNode(
      EnumDeclarationNode decls) {
    EnumBodyNode body = Preconditions.checkNotNull(
        decls.firstChildWithType(EnumBodyNode.class));
    EnumBodyDeclarationsNode members = body.firstChildWithType(
        EnumBodyDeclarationsNode.class);
    if (members == null) {
      members = EnumBodyDeclarationsNode.Variant.SemClassMemberDeclaration
          .buildNode();
      body.add(members);
    }
    return members;
  }

  private static void removeModifiers(
      MethodDeclarationNode decl, int modifiers) {
    for (int i = decl.getNChildren(); --i >= 0;) {
      J8BaseNode child = decl.getChild(i);
      if (child instanceof ModifierNode) {
        int bit = ModifierNodes.modifierBits(
            ((ModifierNode) child).getVariant());
        if ((bit & modifiers) != 0) {
          decl.remove(i);
        }
      }
    }
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
    /**
     * Maps names of methods in specialized constants to the name of the
     * method migrated from the constant class into the enum class.
     * This lets us remap intra-class method calls in the specialized
     * class body.
     */
    final Map<Name, Name> specializedNameToUnspecialized =
        Maps.newLinkedHashMap();
    /**
     * Maps names reached via super calls that are declared in a super-type
     * like java.lang.{Enum,Object} to trampoline methods that provide a
     * reliable way to reach them even if the method will have a synthetic
     * override in the base enum.
     */
    final Map<Name, BaseMethod> outerClassTrampolines =
        Maps.newLinkedHashMap();
    /**
     * Maps names of methods in the base enum class to private statics that are
     * equivalent, except which do not handle special behavior for constants
     * that override the method.
     * These base methods are used to implement super calls from a constant
     * when the enum base class defines its own version.
     */
    final Map<Name, BaseMethod> enumMethodNameToBase = Maps.newLinkedHashMap();

    EnumState(TypeInfo ti) {
      this.ti = ti;
    }

    public SpecialConstant specialConstantForClass(Name canonName) {
      for (SpecialConstant sc : specialConstants) {
        if (canonName.equals(sc.ti.canonName)) { return sc; }
      }
      throw new IllegalArgumentException(canonName.toString());
    }

    @SuppressWarnings("synthetic-access")
    private String noncollidingIdentifier(String constName, Name nm) {
      if (nm.type == Name.Type.METHOD
          && Name.isSpecialMethodIdentifier(nm.identifier)) {
        return nm.identifier;
      } else {
        return nameAllocator.allocateIdentifier(
            constName + "__" + nm.identifier);
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
            Lists.transform(
                ci.typeParameters,
                new Function<Name, Name>() {

                  @Override
                  public Name apply(Name paramOnSpecializedType) {
                    // We're translating parameters without creating types in
                    // the type info resolver.
                    // Since we blow away metadata at the end of the run, this
                    // shouldn't affect other passes.
                    return methodName.child(
                        paramOnSpecializedType.identifier,
                        paramOnSpecializedType.type);
                  }
                }),
            ci.isInitializer
            );
        staticPrivateCallable.setFormalTypes(ci.getFormalTypes());
        staticPrivateCallable.setThrownTypes(ci.getThrownTypes());
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
      Name dupe = specializedNameToUnspecialized.put(
          mi.canonName, staticPrivateMember.canonName);
      Preconditions.checkState(dupe == null);
    }

    Name reserveNameForSuperTrampoline(CallableInfo superMethod) {
      String id = noncollidingIdentifier("super", superMethod.canonName);
      return ti.canonName.method(id, 1);
    }

    @SuppressWarnings("synthetic-access")
    BaseMethod extractMethodBodyToBaseMethod(
        MethodDeclarationNode md, CallableInfo ci) {
      // Reserve a base name.
      String reservedIdentifier = noncollidingIdentifier("base", ci.canonName);
      Name baseMethodName = variantPool.allocateVariant(
          ti.canonName, reservedIdentifier);

      CallableInfo bci = new CallableInfo(
          ci.modifiers | Modifier.PRIVATE | Modifier.FINAL
          & ~(Modifier.PROTECTED | Modifier.PUBLIC),
          baseMethodName, ci.typeParameters, false);
      bci.setFormalTypes(ci.getFormalTypes());
      bci.setThrownTypes(ci.getThrownTypes());
      bci.setReturnType(ci.getReturnType());
      bci.setVariadic(ci.isVariadic());
      bci.setDescriptor(ci.getDescriptor());

      BaseMethod baseMethod = new BaseMethod(
          baseMethodName, md, bci, this);
      BaseMethod old = enumMethodNameToBase.put(
          ci.canonName, baseMethod);
      Preconditions.checkState(old == null, ci.canonName);

      return baseMethod;
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

  /**
   * Information about a base method.  A private final method that
   * encapsulates the un-overridden behavior of an instance method.
   * <p>
   * Instance methods call the base method, and super-calls to the instance
   * method from special constants that override the base method can be
   * re-routed directly to the base method.
   */
  private final class BaseMethod {
    final Name baseMethodName;
    final MethodDeclarationNode originalMethodDecl;
    final CallableInfo baseCallableInfo;
    final EnumState es;

    BaseMethod(
        Name baseMethodName,
        MethodDeclarationNode originalMethodDecl,
        CallableInfo baseCallableInfo,
        EnumState es) {
      this.baseMethodName = baseMethodName;
      this.originalMethodDecl = originalMethodDecl;
      this.baseCallableInfo = baseCallableInfo;
      this.es = es;
    }


    /**
     * Adds the base method declaration to the tree, and relinks the
     * original method declaration to delegate.
     */
    @SuppressWarnings("synthetic-access")
    void commit(EnumBodyDeclarationsNode members) {
      // Now create a method declaration.
      Migration mig = new Migration(es);
      MethodDeclarationNode baseMethodDecl = mig.renameMethod(
          originalMethodDecl.deepClone(), baseMethodName);
      int baseModifiers = mig.ensureModifiers(
          baseMethodDecl, Modifier.PRIVATE | Modifier.FINAL, 0);
      Preconditions.checkState(baseModifiers == baseCallableInfo.modifiers);
      baseMethodDecl.setMemberInfo(ImmutableList.of(baseCallableInfo));

      members.add(
          ClassMemberDeclarationNode.Variant.MethodDeclaration.buildNode(
              baseMethodDecl));

      // Now that we have a copy of the original, replace the original's
      // body with a call to the base method.
      boolean isVoid = StaticType.T_VOID.typeSpecification.equals(
          baseCallableInfo.getReturnType());
      PrimaryNode callToBase = callToPassingActualsFor(
          baseCallableInfo,
          ExpressionAtomNode.Variant.This.buildNode(), baseMethodDecl);
      StatementNode callToBaseStmt;
      if (isVoid) {
        callToBaseStmt = StatementNode.Variant.ExpressionStatement.buildNode(
            ExpressionStatementNode.Variant.StatementExpressionSem
            .buildNode(
                StatementExpressionNode.Variant.MethodInvocation
                .buildNode(
                    MethodInvocationNode.Variant.ExplicitCallee.buildNode(
                        callToBase))));
      } else {
        callToBaseStmt = StatementNode.Variant.ReturnStatement.buildNode(
            ReturnStatementNode.Variant.ReturnExpressionSem.buildNode(
                ExpressionNode.Variant.ConditionalExpression.buildNode(
                    callToBase)));
      }
      Optional<BlockStatementsNode> bodyBlockOpt = requireMethodBodyBlock(
          originalMethodDecl, Suppliers.ofInstance(ImmutableList.of()));
      if (bodyBlockOpt.isPresent()) {
        BlockStatementsNode bodyBlock = bodyBlockOpt.get();
        bodyBlock.replaceChildren(ImmutableList.of());
        addStatementBeforeBlock(
            bodyBlockOpt.get(),
            BlockStatementNode.Variant.Statement.buildNode(callToBaseStmt));
      }
    }
  }

  final class Migration {
    final EnumState enumState;

    Migration(EnumState enumState) {
      this.enumState = enumState;
    }

    ClassMemberDeclarationNode migrate(
        ClassMemberDeclarationNode toImport) {
      // Fixup declaration names so that rerunning the inference passes
      // infers the right things.

      switch (toImport.getVariant()) {
        case InstanceInitializer:
          // No name to handle, but we need to make this a static initializer.
          // Rely on the renaming of expression and statement parts within.
          toImport.setVariant(
              ClassMemberDeclarationNode.Variant.StaticInitializer);
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
        case ClassDeclaration:
        case InterfaceDeclaration: {
          Optional<J8TypeDeclaration> declOpt =
              Mixins.getInnerTypeDeclaration(toImport);
          if (declOpt.isPresent()) {
            J8TypeDeclaration decl = declOpt.get();
            ensureModifiers((J8BaseInnerNode) decl, Modifier.PRIVATE, 0);
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
          ensureModifiers(fieldDecl, Modifier.PRIVATE | Modifier.STATIC, 0);
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
          CallableInfo mi = methodDecl.getCallableInfo();
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
          renameMethod(methodDecl, migrated);
          ensureModifiers(methodDecl, Modifier.PRIVATE | Modifier.STATIC, 0);
          return toImport;
        }
        case Sem:
          return toImport;
      }
      throw new AssertionError(toImport.getVariant());
    }

    MethodDeclarationNode renameMethod(
        MethodDeclarationNode methodDecl, Name migrated) {
      MethodHeaderNode header = methodDecl.firstChildWithType(
          MethodHeaderNode.class);
      if (header == null) {
        error(methodDecl, "Missing method header");
        return methodDecl;
      }
      MethodDeclaratorNode declarator = header.firstChildWithType(
          MethodDeclaratorNode.class);
      if (declarator == null) {
        error(header, "Missing method declarator");
        return methodDecl;
      }
      MethodNameNode name = declarator.firstChildWithType(
          MethodNameNode.class);
      if (name == null) {
        error(declarator, "Missing method name");
        return methodDecl;
      }
      IdentifierNode ident = name.firstChildWithType(IdentifierNode.class);
      if (ident == null) {
        error(name, "Missing identifier");
        return methodDecl;
      }
      ident.setValue(migrated.identifier);
      return methodDecl;
    }

    int ensureModifiers(
        J8BaseInnerNode decl,
        int addIfNotPresentBits, int removeIfPresentBits) {
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
      final int accessBitMask =
          Modifier.PUBLIC | Modifier.PROTECTED | Modifier.PRIVATE;
      int accessBit = addIfNotPresentBits & accessBitMask;
      int removeIfPresentBitsFull = removeIfPresentBits
          | (accessBit != 0 ? accessBitMask & ~accessBit : 0);

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
        // public or protected or private -> private
        boolean remove = false;
        if ((modBits & accessBitMask) != 0 && accessBit != modBits
            && accessBit != 0) {
          modNode.setVariant(ModifierNodes.modifierVariant(accessBit));
          modBits = accessBit;
        } else if ((modBits & removeIfPresentBitsFull) != 0) {
          remove = true;
        }
        modifiers |= modBits;
        if (isAnnotation(JAVA_LANG_OVERRIDE, modNode)) {
          remove = true;
        }
        if (remove) {
          decl.remove(modifierEnd);
          --modifierEnd;
          --nChildren;
          continue;
        }
      }
      // Make sure static and private are present.
      int bitsNeeded = addIfNotPresentBits & ~modifiers;
      int modifierPos = modifierStart;
      for (int modBit : ModifierNodes.ALL_MODIFIER_BITS_IN_ORDER) {
        if ((bitsNeeded & modBit) != 0) {
          ModifierNode.Variant v = ModifierNodes.modifierVariant(modBit);
          if (v != null) {
            decl.add(modifierPos++, v.buildNode());
          }
          modifiers |= modBit;
          bitsNeeded &= ~modBit;
        }
      }
      Preconditions.checkState(bitsNeeded == 0);
      return modifiers;
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
  static final TypeSpecification JAVA_LANG_ASSERTIONERROR =
      TypeSpecification.unparameterized(
          JavaLang.JAVA_LANG.child(
              "AssertionError", Name.Type.CLASS));

  private static final Function<? super Name, ? extends J8BaseNode>
  TYPE_PARAMETER_NAME_TO_TYPE_ARGUMENT_NODE =
      new Function<Name, TypeArgumentNode>() {
        @Override
        public TypeArgumentNode apply(Name typeParamName) {
          return TypeArgumentNode.Variant.ReferenceType
              .buildNode(
                  ReferenceTypeNode.Variant.TypeVariable
                  .buildNode(
                      TypeVariableNode.Variant
                      .AnnotationIdentifier.buildNode(
                          TypeNodeFactory.
                          toIdentifierNode(typeParamName))));
        }
      };

  private static final Function<VariableDeclaratorIdNode, ExpressionNode>
  VARIABLE_DECLARATOR_TO_REFERENCE =
      new Function<VariableDeclaratorIdNode, ExpressionNode>() {
        @Override
        public ExpressionNode apply(VariableDeclaratorIdNode d) {
          IdentifierNode identNode = d.firstChildWithType(IdentifierNode.class);
          return ExpressionNode.Variant
              .ConditionalExpression.buildNode(
                  ExpressionAtomNode.Variant.Local.buildNode(
                      LocalNameNode.Variant.Identifier.buildNode(
                          IdentifierNode.Variant.Builtin
                              .buildNode(identNode.getValue())
                              .setNamePartType(Name.Type.LOCAL))));
        }
      };

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
      ClassMemberDeclarationNode member) {
    if (ClassMemberDeclarationNode.Variant.MethodDeclaration
        == member.getVariant()) {
      return Optional.fromNullable(
          member.firstChildWithType(MethodDeclarationNode.class));
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

    ImmutableList.Builder<ModifierNode> modifiers = ImmutableList.builder();
    modifiers.add(
        ModifierNode.Variant.Annotation.buildNode(
            AnnotationNode.Variant.MarkerAnnotation.buildNode(
                MarkerAnnotationNode.Variant.AtTypeName.buildNode(
                    typeNodeFactory.toTypeNameNode(JAVA_LANG_OVERRIDE)))));
    for (int mods = ci.modifiers, bit; mods != 0; mods &= ~bit) {
      bit = mods & -mods;
      ModifierNode.Variant v = ModifierNodes.modifierVariant(bit);
      if (v != null) {
        modifiers.add(v.buildNode());
      }
    }

    ResultNode result = typeNodeFactory.toResultNode(
        infoPool.typePool.type(ci.getReturnType(), null, null));
    boolean isVoid = StaticType.T_VOID.typeSpecification.equals(
        ci.getReturnType());

    PrimaryNode superCall = callToPassingActualsFor(
        ci,
        ExpressionAtomNode.Variant.Super.buildNode(),
        mdn);

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
            TypeNodeFactory.toIdentifierNode(ci.canonName)));
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
      // None of the overridable methods in Object or Enum have type parameters
      // so we need not handle this case.
      throw new AssertionError(ci.canonName);
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

  private static PrimaryNode callToPassingActualsFor(
      CallableInfo ci, ExpressionAtomNode thisValue,
      MethodDeclarationNode declaration) {
    MethodHeaderNode header = Preconditions.checkNotNull(
        declaration.firstChildWithType(MethodHeaderNode.class));
    MethodDeclaratorNode declarator = Preconditions.checkNotNull(
        header.firstChildWithType(MethodDeclaratorNode.class));
    FormalParameterListNode formals = declarator.firstChildWithType(
        FormalParameterListNode.class);
    ImmutableList<VariableDeclaratorIdNode> formalNames = formals != null
        ? formals.finder(VariableDeclaratorIdNode.class).find()
        : ImmutableList.of();

    ImmutableList.Builder<J8BaseNode> callChildren =
        ImmutableList.<J8BaseNode>builder()
        .add(thisValue);
    if (!ci.typeParameters.isEmpty()) {
      callChildren.add(
          TypeArgumentsNode.Variant.LtTypeArgumentListGt.buildNode(
              TypeArgumentListNode.Variant.TypeArgumentComTypeArgument
              .buildNode(
                  Lists.<Name, J8BaseNode>transform(
                      ci.typeParameters,
                      TYPE_PARAMETER_NAME_TO_TYPE_ARGUMENT_NODE))));
    }
    callChildren.add(
        MethodNameNode.Variant.Identifier.buildNode(
            TypeNodeFactory.toIdentifierNode(ci.canonName)));
    if (!formalNames.isEmpty()) {
      callChildren.add(
          ArgumentListNode.Variant.ExpressionComExpression
          .buildNode(
              Lists.<VariableDeclaratorIdNode, ExpressionNode>transform(
                  formalNames,
                  VARIABLE_DECLARATOR_TO_REFERENCE)));
    }
    return PrimaryNode.Variant.MethodInvocation.buildNode(
        callChildren.build());
  }

  private Name getSuperTrampoline(EnumState es, CallableInfo superCallee) {
    BaseMethod trampoline = es.outerClassTrampolines.get(superCallee.canonName);
    if (trampoline != null) { return trampoline.baseMethodName; }
    Name trampolineName = es.reserveNameForSuperTrampoline(superCallee);

    // First construct a call like
    // super.methodName(a0, a1, a2)
    ImmutableList.Builder<J8BaseNode> callChildren = ImmutableList.builder();
    callChildren.add(
        ExpressionAtomNode.Variant.Super.buildNode());
    if (!superCallee.typeParameters.isEmpty()) {
      callChildren.add(typeNodeFactory.toTypeArgumentsNode(
          null,
          Lists.transform(
              superCallee.typeParameters,
              new Function<Name, TypeSpecification.TypeBinding>() {

                @Override
                public TypeSpecification.TypeBinding apply(Name nm) {
                  return new TypeSpecification.TypeBinding(
                      TypeSpecification.unparameterized(nm));
                }
              })));
    }
    callChildren.add(MethodNameNode.Variant.Identifier.buildNode(
        TypeNodeFactory.toIdentifierNode(superCallee.canonName)));
    ImmutableList<TypeSpecification> formals = superCallee.getFormalTypes();
    if (!formals.isEmpty()) {
      ImmutableList.Builder<ExpressionNode> b = ImmutableList.builder();
      for (int i = 0, n = formals.size(); i < n; ++i) {
        // Same naming convention as below.
        b.add(ExpressionNode.Variant.ConditionalExpression.buildNode(
            ExpressionAtomNode.Variant.Local.buildNode(
                LocalNameNode.Variant.Identifier.buildNode(
                    IdentifierNode.Variant.Builtin.buildNode("a" + i)))));
      }
      callChildren.add(ArgumentListNode.Variant.ExpressionComExpression
          .buildNode(b.build()));
    }
    PrimaryNode callToSuper = PrimaryNode.Variant.MethodInvocation.buildNode(
        callChildren.build());

    // Next construct a method header like
    //    private final <T0, T1, T2>
    //    ReturnType super__methodName(FT0 a0, FT1 a1, FT2... a2) throws TT0
    ImmutableList.Builder<J8BaseNode> headerChildren = ImmutableList.builder();
    ImmutableList.Builder<Name> rebasedParameters = ImmutableList.builder();
    if (!superCallee.typeParameters.isEmpty()) {
      ImmutableList.Builder<J8BaseNode> typeParameters =
          ImmutableList.builder();
      for (Name typeParamName : superCallee.typeParameters) {
        Optional<TypeInfo> tiOpt = infoPool.typePool.r.resolve(typeParamName);
        if (!tiOpt.isPresent()) {
          error(null, "Missing type information for " + typeParamName);
        }
        ImmutableList.Builder<J8BaseNode> b = ImmutableList.builder();
        Name rebasedParameter = trampolineName.child(
            typeParamName.identifier, Name.Type.TYPE_PARAMETER);
        b.add(SimpleTypeNameNode.Variant.Identifier.buildNode(
            TypeNodeFactory.toIdentifierNode(rebasedParameter)));
        if (tiOpt.isPresent()) {
          TypeBoundNode boundNode = typeNodeFactory.typeBoundFor(tiOpt.get());
          if (boundNode != null) {
            b.add(boundNode);
          }
        }
        typeParameters.add(
            TypeParameterNode.Variant.TypeParameter.buildNode(b.build()));
        rebasedParameters.add(rebasedParameter);
      }
      headerChildren.add(
          TypeParametersNode.Variant.LtTypeParameterListGt.buildNode(
              TypeParameterListNode.Variant.TypeParameterComTypeParameter
              .buildNode(typeParameters.build())));
    }
    MethodDeclaratorNode methodDeclarator;
    {
      ImmutableList.Builder<J8BaseNode> b = ImmutableList.builder();
      b.add(MethodNameNode.Variant.Identifier.buildNode(
          TypeNodeFactory.toIdentifierNode(trampolineName)));
      if (!formals.isEmpty()) {
        ImmutableList.Builder<String> formalNames = ImmutableList.builder();
        for (int i = 0, n = formals.size(); i < n; ++i) {
          formalNames.add("a" + i);
        }
        b.add(typeNodeFactory.toFormalParameterListNode(
            formals, formalNames.build(), superCallee.isVariadic()));
      }
      methodDeclarator = MethodDeclaratorNode.Variant
          .MethodNameLpFormalParameterListRpDims.buildNode(b.build());
    }
    boolean isVoid = StaticType.T_VOID.typeSpecification.equals(
        superCallee.getReturnType());
    headerChildren.add(typeNodeFactory.toResultNode(
        infoPool.typePool.type(superCallee.getReturnType(), null, null)));
    headerChildren.add(methodDeclarator);
    if (!superCallee.getThrownTypes().isEmpty()) {
      headerChildren.add(
          typeNodeFactory.toThrowsNode(superCallee.getThrownTypes()));
    }
    MethodHeaderNode methodHeader = MethodHeaderNode.Variant
        .TypeParametersAnnotationResultMethodDeclaratorThrows.buildNode(
            headerChildren.build());

    // Now construct a body like either
    //   return superCall(a0, a1, a2);
    // or
    //   superCall(a0, a1, a2);
    // TODO: a lot of code could be consolidate with other BaseMethod
    // constructors.  For example, the simple switch above.
    MethodBodyNode methodBody = MethodBodyNode.Variant.Block.buildNode(
        BlockNode.Variant.LcBlockStatementsRc.buildNode(
            BlockStatementsNode.Variant
            .BlockStatementBlockStatementBlockTypeScope.buildNode(
                BlockStatementNode.Variant.Statement.buildNode(
                    isVoid
                    ? StatementNode.Variant.ExpressionStatement.buildNode(
                        ExpressionStatementNode.Variant.StatementExpressionSem
                        .buildNode(
                            StatementExpressionNode.Variant.MethodInvocation
                            .buildNode(
                                MethodInvocationNode.Variant.ExplicitCallee
                                .buildNode(callToSuper))))
                    : StatementNode.Variant.ReturnStatement.buildNode(
                        ReturnStatementNode.Variant.ReturnExpressionSem
                        .buildNode(
                            ExpressionNode.Variant.ConditionalExpression
                            .buildNode(callToSuper)))))));

    MethodDeclarationNode decl = MethodDeclarationNode.Variant.Declaration
        .buildNode(
            ModifierNode.Variant.Private.buildNode(),
            ModifierNode.Variant.Final.buildNode(),
            methodHeader,
            methodBody);

    CallableInfo trampolineInfo = new CallableInfo(
        Modifier.PRIVATE | Modifier.FINAL, trampolineName,
        rebasedParameters.build(), false);
    trampolineInfo.setDescriptor(superCallee.getDescriptor());
    trampolineInfo.setFormalTypes(superCallee.getFormalTypes());
    trampolineInfo.setSynthetic(true);
    trampolineInfo.setVariadic(superCallee.isVariadic());
    trampolineInfo.setReturnType(superCallee.getReturnType());
    trampolineInfo.setThrownTypes(superCallee.getThrownTypes());

    trampoline = new BaseMethod(trampolineName, decl, trampolineInfo, es);
    es.outerClassTrampolines.put(superCallee.canonName, trampoline);
    return trampolineName;
  }
}
