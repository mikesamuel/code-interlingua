package com.mikesamuel.cil.ast.passes.synth;

import java.lang.reflect.Modifier;
import java.util.EnumSet;

import javax.annotation.Nullable;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.mikesamuel.cil.ast.j8.AssignmentNode;
import com.mikesamuel.cil.ast.j8.AssignmentOperatorNode;
import com.mikesamuel.cil.ast.j8.ClassOrInterfaceTypeNode;
import com.mikesamuel.cil.ast.j8.ClassOrInterfaceTypeToInstantiateNode;
import com.mikesamuel.cil.ast.j8.ExpressionAtomNode;
import com.mikesamuel.cil.ast.j8.J8BaseInnerNode;
import com.mikesamuel.cil.ast.j8.J8BaseNode;
import com.mikesamuel.cil.ast.j8.J8ExpressionNameReference;
import com.mikesamuel.cil.ast.j8.J8FileNode;
import com.mikesamuel.cil.ast.j8.J8NodeType;
import com.mikesamuel.cil.ast.j8.J8TypeDeclaration;
import com.mikesamuel.cil.ast.j8.MethodNameNode;
import com.mikesamuel.cil.ast.j8.PrimaryNode;
import com.mikesamuel.cil.ast.j8.UnqualifiedClassInstanceCreationExpressionNode;
import com.mikesamuel.cil.ast.meta.CallableInfo;
import com.mikesamuel.cil.ast.meta.MemberInfo;
import com.mikesamuel.cil.ast.meta.MethodDescriptor;
import com.mikesamuel.cil.ast.meta.Name;
import com.mikesamuel.cil.ast.meta.TypeInfo;
import com.mikesamuel.cil.ast.passes.AbstractRewritingPass.Parent;
import com.mikesamuel.cil.parser.SList;

abstract class PerUseAbstractMiniPass {
  final Common c;

  PerUseAbstractMiniPass(Common c) {
    this.c = c;
  }

  void run(ImmutableList<J8FileNode> files) {
    for (J8FileNode f : files) {
      J8BaseNode root = (J8BaseNode) f;
      findUses(root, null, null);
    }
  }

  final void processUse(
      UserDefinedType udt,
      J8BaseNode node, @Nullable SList<Parent> pathFromRoot,
      Name nm, UseType t) {
    processUse(udt, node, pathFromRoot, nm, EnumSet.of(t));
  }

  abstract void processUse(
      UserDefinedType udt,
      J8BaseNode node, @Nullable SList<Parent> pathFromRoot,
      Name nm, EnumSet<UseType> types);

  private boolean isCrossClassPrivateAccess(
      UserDefinedType udt, Name name, boolean evenIfSameClass) {
    Name className = name.getContainingClass();
    if (udt != null && (evenIfSameClass || !udt.ti.canonName.equals(className))
        && udt.topLevelTypeContaining.equals(
            Common.topLevelTypeOf(className))) {
      Optional<TypeInfo> declaringTypeOpt = c.typePool.r.resolve(className);
      if (declaringTypeOpt.isPresent()) {
        Optional<MemberInfo> miOpt = declaringTypeOpt.get()
            .declaredMemberNamed(name);
        if (miOpt.isPresent()) {
          return Modifier.isPrivate(miOpt.get().modifiers);
        }
      }
    }
    return false;
  }

  private void findUses(
      J8BaseNode node, SList<Parent> pathFromRoot, UserDefinedType udt) {
    UserDefinedType cudt = udt;
    if (node instanceof J8TypeDeclaration) {
      J8TypeDeclaration d = (J8TypeDeclaration) node;
      TypeInfo ti = d.getDeclaredTypeInfo();
      if (ti != null && ti.canonName.type == Name.Type.CLASS) {
        cudt = c.byName.get(ti.canonName);
        if (cudt == null) {
          cudt = new UserDefinedType(d);
          c.byName.put(ti.canonName, cudt);
        }
      }
    }
    // Look for private cross-class accesses
    if (node instanceof J8ExpressionNameReference) {
      J8ExpressionNameReference ref = (J8ExpressionNameReference) node;
      Name name = ref.getReferencedExpressionName();
      if (name != null
          && (name.type == Name.Type.METHOD
              || name.type == Name.Type.FIELD)
          && isCrossClassPrivateAccess(udt, name, false)) {
        if (name.type == Name.Type.METHOD) {
          processUse(
              cudt, node, pathFromRoot, name, UseType.INVOKE_OF_PRIVATE);
        } else {
          SList<Parent> p = pathFromRoot;
          SList<Parent> gp = p != null ? p.prev : null;
          EnumSet<UseType> types = EnumSet.of(UseType.READ_OF_PRIVATE);
          if (gp != null
              && gp.x.parent.getNodeType() == J8NodeType.LeftHandSide) {
            types.add(UseType.WRITE_OF_PRIVATE);
            SList<Parent> ggp = gp.prev;
            if (ggp != null && ggp.x.parent instanceof AssignmentNode) {
              // If it's a combo assignment, we still need to read the value
              // first.
              // If its a pre-increment or post-increment, the test above will
              // fail.
              AssignmentOperatorNode op = ggp.x.parent.firstChildWithType(
                  AssignmentOperatorNode.class);
              if (op != null
                  && op.getVariant() == AssignmentOperatorNode.Variant.Eq) {
                types.remove(UseType.READ_OF_PRIVATE);
              }
            }
          }
          processUse(cudt, node, pathFromRoot, name, types);
        }
      }
    } else if (node instanceof
               UnqualifiedClassInstanceCreationExpressionNode) {
      UnqualifiedClassInstanceCreationExpressionNode e =
          (UnqualifiedClassInstanceCreationExpressionNode) node;
      // Even if this declares an anonymous type, that anonymous type
      // will still need to access its super constructor,
      boolean declaresAnonymousClass = e.getDeclaredTypeInfo() != null;
      ClassOrInterfaceTypeToInstantiateNode ttiNode = e.firstChildWithType(
          ClassOrInterfaceTypeToInstantiateNode.class);
      ClassOrInterfaceTypeNode tNode = ttiNode != null
          ? ttiNode.firstChildWithType(ClassOrInterfaceTypeNode.class)
          : null;
      TypeInfo ti = tNode != null ? tNode.getReferencedTypeInfo() : null;
      MethodDescriptor md = e.getMethodDescriptor();
      if (ti != null && md != null) {
        for (MemberInfo mi : ti.getDeclaredMembers()) {
          if (mi instanceof CallableInfo
              && Name.CTOR_INSTANCE_INITIALIZER_SPECIAL_NAME.equals(
                  mi.canonName.identifier)
              && !((CallableInfo) mi).isInitializer
              && md.equals(((CallableInfo) mi).getDescriptor())) {
            // We have uniquely identified the constructor.
            Name name = mi.canonName;
            if (isCrossClassPrivateAccess(
                    udt, name, declaresAnonymousClass)) {
              processUse(
                  cudt, node, pathFromRoot, name, UseType.INVOKE_OF_PRIVATE);
            }
            break;
          }
        }
      }
    }

    if (node.getVariant() == PrimaryNode.Variant.MethodInvocation) {
      ExpressionAtomNode receiver = node.firstChildWithType(
          ExpressionAtomNode.class);
      if (receiver != null
          && receiver.getVariant() == ExpressionAtomNode.Variant.Super) {
        MethodNameNode methodNameNode = node.firstChildWithType(
            MethodNameNode.class);
        if (methodNameNode != null) {
          Name name = methodNameNode.getReferencedExpressionName();
          processUse(cudt, node, pathFromRoot, name, UseType.SUPER_INVOKE);
        }
      }
    }

    if (node instanceof J8BaseInnerNode) {
      J8BaseInnerNode inode = (J8BaseInnerNode) node;
      for (int i = 0, n = inode.getNChildren(); i < n; ++i) {
        J8BaseNode child = inode.getChild(i);
        findUses(
            child,
            SList.<Parent>append(pathFromRoot, new Parent(i, inode)),
            cudt);
      }
    }
  }
}
