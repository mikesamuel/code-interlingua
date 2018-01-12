package com.mikesamuel.cil.ast.passes.flatten;

import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.mikesamuel.cil.ast.j8.BlockStatementsNode;
import com.mikesamuel.cil.ast.j8.BlockTypeScopeNode;
import com.mikesamuel.cil.ast.j8.ClassBodyNode;
import com.mikesamuel.cil.ast.j8.ClassDeclarationNode;
import com.mikesamuel.cil.ast.j8.IdentifierNode;
import com.mikesamuel.cil.ast.j8.InterfaceDeclarationNode;
import com.mikesamuel.cil.ast.j8.J8BaseInnerNode;
import com.mikesamuel.cil.ast.j8.J8BaseNode;
import com.mikesamuel.cil.ast.j8.J8FileNode;
import com.mikesamuel.cil.ast.j8.J8NodeType;
import com.mikesamuel.cil.ast.j8.J8NodeVariant;
import com.mikesamuel.cil.ast.j8.J8TypeDeclaration;
import com.mikesamuel.cil.ast.j8.SimpleTypeNameNode;
import com.mikesamuel.cil.ast.j8.TypeDeclarationNode;
import com.mikesamuel.cil.ast.meta.TypeInfo;
import com.mikesamuel.cil.ast.meta.TypeInfoResolver;
import com.mikesamuel.cil.ast.meta.Name;
import com.mikesamuel.cil.ast.passes.flatten.PassState.FlatteningType;
import com.mikesamuel.cil.util.LogUtils;

final class FlattenTypeHierarchyMiniPass {

  final Logger logger;
  final TypeInfoResolver r;

  FlattenTypeHierarchyMiniPass(Logger logger, TypeInfoResolver r) {
    this.logger = logger;
    this.r = r;
  }

  final ImmutableList<J8FileNode> run(PassState ps) {
    ImmutableList.Builder<J8FileNode> flatFileNodes = ImmutableList.builder();
    for (PassState.FlatteningType ft : ps.inProcessOrder) {
      flatFileNodes.add(ownFileNode(ft));
    }
    for (PassState.FlatteningType ft : ps.inProcessOrder) {
      unlinkNestedTypeDeclarations((J8BaseNode) ft.root);
    }
    for (PassState.FlatteningType ft : ps.inProcessOrder) {
      rewriteSimpleTypeNames(ft);
    }
    return flatFileNodes.build();
  }

  private J8FileNode ownFileNode(FlatteningType ft) {
    return (J8FileNode) cloneSkeletonAndInsertInPlace(
        (J8BaseNode) ft.declarationSite, ft);
  }

  private J8BaseNode cloneSkeletonAndInsertInPlace(
      J8BaseNode node, FlatteningType ft) {
    boolean clone = false;
    if (node.getNodeType().isNonStandard()) {
      clone = true;
    } else {
      switch (node.getNodeType()) {
        case PackageDeclaration:
        case ImportDeclaration:
          return node.deepClone();
        case CompilationUnit:
        case TypeDeclaration:
        case ClassDeclaration:
        case InterfaceDeclaration:
          clone = true;
          break;
        default:
          if (node instanceof J8TypeDeclaration) {
            TypeInfo ti = ((J8TypeDeclaration) node).getDeclaredTypeInfo();
            if (ti != null && isRootOf(ti.canonName, ft.typeInfo.canonName)) {
              return (J8BaseNode) ft.root;
            }
          }
          break;
      }
    }
    if (!clone) { return null; }
    J8BaseNode newNode = node.shallowClone();
    ImmutableList.Builder<J8BaseNode> newChildren = ImmutableList.builder();
    for (J8BaseNode child : node.getChildren()) {
      J8BaseNode childClone = cloneSkeletonAndInsertInPlace(child, ft);
      if (childClone != null) {
        newChildren.add(childClone);
      }
    }
    if (newNode instanceof J8BaseInnerNode) {
      J8BaseInnerNode newINode = (J8BaseInnerNode) newNode;
      newINode.replaceChildren(newChildren.build());
    } else {
      Preconditions.checkState(newChildren.build().isEmpty());
    }
    if (newNode.getNChildren() == 0) {
      switch (newNode.getNodeType()) {
        case TypeDeclaration:
        case ClassDeclaration:
        case InterfaceDeclaration:
          return null;
        default:
          break;
      }
    }

    J8NodeVariant adjustedVariant = null;
    if (newNode.getNodeType() == J8NodeType.TypeDeclaration) {
      switch (ft.root.getNodeType()) {
        case EnumDeclaration:
        case NormalClassDeclaration:
          adjustedVariant = TypeDeclarationNode.Variant.ClassDeclaration;
          break;
        case NormalInterfaceDeclaration:
        case AnnotationTypeDeclaration:
          adjustedVariant = TypeDeclarationNode.Variant.InterfaceDeclaration;
          break;
        default:
          throw new AssertionError(ft.root.getNodeType());
      }
    } else if (newNode.getNodeType() == J8NodeType.ClassDeclaration
               || newNode.getNodeType() == J8NodeType.InterfaceDeclaration) {
      switch (ft.root.getNodeType()) {
        case EnumDeclaration:
          adjustedVariant = ClassDeclarationNode.Variant.EnumDeclaration;
          break;
        case NormalClassDeclaration:
          adjustedVariant =
              ClassDeclarationNode.Variant.NormalClassDeclaration;
          break;
        case AnnotationTypeDeclaration:
          adjustedVariant =
             InterfaceDeclarationNode.Variant.NormalInterfaceDeclaration;
          break;
        case NormalInterfaceDeclaration:
          adjustedVariant =
             InterfaceDeclarationNode.Variant.NormalInterfaceDeclaration;
          break;
        default:
      }
    }
    if (adjustedVariant != null && newNode.getVariant() != adjustedVariant) {
      if (adjustedVariant.getNodeType() != newNode.getNodeType()) {
        J8BaseNode adjustedNewNode = adjustedVariant.buildNode(
            newNode.getChildren());
        adjustedNewNode.copyMetadataFrom(newNode);
        newNode = adjustedNewNode;
      } else {
        newNode.setVariant(adjustedVariant);
      }
    }
    return newNode;
  }

  private static boolean isRootOf(Name root, Name desc) {
    for (Name nm = desc; nm != null; nm = nm.parent) {
     if (root.equals(nm)) {
       return true;
     }
     if (nm.parent == null || desc.parent.type == Name.Type.PACKAGE) {
       return false;
     }
    }
    return false;
  }

  /** @return true iff the node should be removed from its parent. */
  private boolean unlinkNestedTypeDeclarations(J8BaseNode node) {
    J8NodeType nt = node.getNodeType();
    if (nt == J8NodeType.ClassDeclaration
        || nt == J8NodeType.InterfaceDeclaration) {
      return true;
    }
    if (!(node instanceof J8BaseInnerNode)) { return false; }
    J8BaseInnerNode inode = (J8BaseInnerNode) node;
    boolean removed = false;
    for (int i = inode.getNChildren(); --i >= 0;) {
      if (unlinkNestedTypeDeclarations(inode.getChild(i))) {
        inode.remove(i);
        removed = true;
      }
    }
    // Unlink just the class body from any anonymous class creation expressions.
    // This won't affect the type node in which nt is declared.
    if (nt == J8NodeType.UnqualifiedClassInstanceCreationExpression) {
      int bodyIndex = inode.finder(ClassBodyNode.class).indexOf();
      if (bodyIndex >= 0) {
        inode.remove(bodyIndex);
        removed = true;
      }
    }
    if (nt == J8NodeType.BlockStatements) {
      if (inode.getVariant() == BlockStatementsNode.Variant.BlockTypeScope) {
        inode.setVariant(
            BlockStatementsNode.Variant
            .BlockStatementBlockStatementBlockTypeScope);
      }
      int nChildren = inode.getNChildren();
      // We might have some BlockTypeScopes that only contain BlockStatements
      // now that block-scoped ClassDeclarations have been removed.
      // Remove the BlockTypeScopes and flatten BlockStatements.
      flattenBlockStatements(inode);
      if (nChildren != inode.getNChildren()) {
        removed = true;
      }
    }
    if (removed && inode.getNChildren() == 0) {
      switch (nt) {
        case TypeDeclaration:
        case AnnotationTypeMemberDeclaration:
        case ClassMemberDeclaration:
        case InterfaceMemberDeclaration:
        case BlockTypeScope:
        case BlockStatements:
          return true;
        default:
      }
    }
    return false;
  }

  private static void flattenBlockStatements(J8BaseInnerNode inode) {
    ImmutableList.Builder<J8BaseNode> newChildren = ImmutableList.builder();
    for (J8BaseNode c : inode.getChildren()) {
      if (c instanceof BlockTypeScopeNode) {
        for (J8BaseNode gc : c.getChildren()) {
          if (gc instanceof BlockStatementsNode) {
            newChildren.addAll(gc.getChildren());
          } else {
            newChildren.add(gc);
          }
        }
      } else {
        newChildren.add(c);
      }
    }
    inode.replaceChildren(newChildren.build());
  }

  private void rewriteSimpleTypeNames(FlatteningType ft) {
    J8BaseNode base = (J8BaseNode) ft.root;
    String oldTypeName = ft.bumpyName.name.identifier;
    String newTypeName = ft.flatName.name.identifier;
    if (!oldTypeName.equals(newTypeName)) {
      for (SimpleTypeNameNode nameNode : base.finder(SimpleTypeNameNode.class)
          .exclude(J8NodeType.TypeParameter).find()) {
        IdentifierNode idNode =
            nameNode.firstChildWithType(IdentifierNode.class);
        if (idNode != null) {
          if (!oldTypeName.equals(idNode.getValue())
              // Placeholder for anonymous classes that
              // were lifted to normal class declarations.
              && !"_".equals(idNode.getValue())) {
            LogUtils.log(
                logger, Level.SEVERE, nameNode,
                "Mismatched type name: " + idNode.getValue(), null);
          } else {
            idNode.setValue(newTypeName);
          }
        }
      }
    }
  }
}
