package com.mikesamuel.cil.ast.passes.flatten;

import java.lang.reflect.Modifier;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Nullable;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.mikesamuel.cil.ast.j8.ArgumentListNode;
import com.mikesamuel.cil.ast.j8.ExpressionAtomNode;
import com.mikesamuel.cil.ast.j8.ExpressionNode;
import com.mikesamuel.cil.ast.j8.FieldNameNode;
import com.mikesamuel.cil.ast.j8.IdentifierNode;
import com.mikesamuel.cil.ast.j8.J8BaseNode;
import com.mikesamuel.cil.ast.j8.J8ExpressionNameReference;
import com.mikesamuel.cil.ast.j8.J8NodeType;
import com.mikesamuel.cil.ast.j8.LocalNameNode;
import com.mikesamuel.cil.ast.j8.MethodNameNode;
import com.mikesamuel.cil.ast.j8.PrimaryNode;
import com.mikesamuel.cil.ast.j8.TypeNameNode;
import com.mikesamuel.cil.ast.meta.CallableInfo;
import com.mikesamuel.cil.ast.meta.FieldInfo;
import com.mikesamuel.cil.ast.meta.Name;
import com.mikesamuel.cil.ast.meta.StaticType;
import com.mikesamuel.cil.ast.meta.StaticType.TypePool;
import com.mikesamuel.cil.ast.meta.TypeInfo;
import com.mikesamuel.cil.ast.passes.TypeNodeFactory;
import com.mikesamuel.cil.ast.passes.flatten.PassState.FlatteningType;
import com.mikesamuel.cil.parser.SList;
import com.mikesamuel.cil.util.LogUtils;

final class RewriteUsesOfClosedOverStateMiniPass {
  final Logger logger;
  final TypePool pool;
  final TypeNodeFactory factory;

  RewriteUsesOfClosedOverStateMiniPass(Logger logger, TypePool pool) {
    this.logger = logger;
    this.pool = pool;
    this.factory = new TypeNodeFactory(logger, pool);
    factory.allowMethodContainers();
  }

  void run(PassState ps) {
    for (PassState.FlatteningType ft : ps.inProcessOrder) {
      new Rewriter(ps, ft).visit();
    }
  }

  final class Rewriter extends SingleTypeRewriter {

    Rewriter(PassState ps, PassState.FlatteningType ft) {
      super(RewriteUsesOfClosedOverStateMiniPass.this.logger, ps, ft);
    }

    @Override
    protected ProcessingStatus previsit(
        J8BaseNode node, @Nullable SList<Parent> pathFromRoot) {
      J8NodeType nt = node.getNodeType();
      if (nt == J8NodeType.Primary) {
        return handle((PrimaryNode) node);
      }
      if (nt == J8NodeType.ExpressionAtom) {
        return handle((ExpressionAtomNode) node);
      }
      if (node instanceof J8ExpressionNameReference) {
        J8ExpressionNameReference ref = (J8ExpressionNameReference) node;
        Name nm = ref.getReferencedExpressionName();
        if (nm != null) {
          switch (nm.type) {
            case AMBIGUOUS:
            case CLASS:
            case PACKAGE:
            case TYPE_PARAMETER:
              break;
            case FIELD:
            case LOCAL:
            case METHOD:
              BName cl = BName.of(nm.getContainingClass());
              if (!cl.equals(ft.bumpyName)
                  && ps.byBumpyName.containsKey(cl)) {
                LogUtils.log(
                    logger, Level.SEVERE, node,
                    "Could not rewrite external reference to "
                        + nm + " from " + ft.bumpyName, null);
              }
              break;
          }
        }
      }
      return ProcessingStatus.CONTINUE;
    }

    private ProcessingStatus handle(ExpressionAtomNode node) {
      switch (node.getVariant()) {
        case This: {
          TypeNameNode tn = node.firstChildWithType(TypeNameNode.class);
          if (tn == null) { break; }  // Same-class reference.
          TypeInfo rti = tn.getReferencedTypeInfo();
          if (rti != null) {
            FieldInfo thisField = thisFieldFor(rti.canonName);
            if (thisField != null) {
              return ProcessingStatus.replace(
                  thisFieldAsAtom(thisField.canonName, node));
            }
          }
          break;
        }
        case FreeField: {
          FieldNameNode fnn = node.firstChildWithType(FieldNameNode.class);
          if (fnn == null) { break; }
          Name fn = fnn.getReferencedExpressionName();
          if (fn == null) { break; }
          Name cn = fn.getContainingClass();
          FieldInfo referencedField;
          {
            Optional<TypeInfo> ti = pool.r.resolve(cn);
            referencedField = null;
            if (ti.isPresent()) {
              referencedField = ti.get().declaredFieldNamed(fn).orNull();
            }
            if (referencedField == null) {
              break;
            }
          }
          ExpressionAtomNode explicitRef = null;
          if (Modifier.isStatic(referencedField.modifiers)) {
            explicitRef = ExpressionAtomNode.Variant.StaticMember.buildNode(
                factory.toTypeNameNode(cn));
          } else {
            FieldInfo fi = thisFieldFor(cn);
            if (fi != null) {
              explicitRef = thisFieldAsAtom(fi.canonName, node);
            }
          }
          if (explicitRef != null) {
            fnn.setReferencedExpressionName(null);
            ExpressionAtomNode fieldRef =
                ExpressionAtomNode.Variant.Parenthesized.buildNode(
                    ExpressionNode.Variant.ConditionalExpression.buildNode(
                        PrimaryNode.Variant.FieldAccess.buildNode(
                            explicitRef,
                            fnn)));
            fieldRef.setSourcePosition(node.getSourcePosition());
            return ProcessingStatus.replace(fieldRef);
          }
          break;
        }
        case Local: {
          int lnni = node.finder(LocalNameNode.class).indexOf();
          if (lnni < 0) { break; }
          LocalNameNode lnn = (LocalNameNode) node.getChild(lnni);
          if (lnn == null) { break; }
          IdentifierNode in = lnn.firstChildWithType(IdentifierNode.class);
          Name ln = lnn.getReferencedExpressionName();
          if (in == null || ln == null) { break; }
          StaticType t = node.getStaticType();
          if (t == null) { break; }
          PassState.ClosedOver co = new PassState.ClosedOverLocal(
              t.typeSpecification, BName.of(ln));
          FieldInfo fi = ft.closedOverMembers.get(co);
          if (fi == null) { break; }
          node.setVariant(ExpressionAtomNode.Variant.FreeField);
          FieldNameNode repl = FieldNameNode.Variant.Identifier.buildNode(in);
          repl.setSourcePosition(lnn.getSourcePosition());
          in.setNamePartType(null);
          in.setValue(fi.canonName.identifier);
          node.replace(lnni, repl);
          break;
        }
        case MethodInvocation: {
          MethodNameNode mnn = node.firstChildWithType(MethodNameNode.class);
          if (mnn == null) { break; }
          Name mn = mnn.getReferencedExpressionName();
          if (mn == null) { break; }
          Name cn = mn.getContainingClass();
          CallableInfo referencedMethod;
          {
            Optional<TypeInfo> ti = pool.r.resolve(cn);
            referencedMethod = null;
            if (ti.isPresent()) {
              referencedMethod = ti.get().declaredCallableNamed(mn).orNull();
            }
            if (referencedMethod == null) {
              break;
            }
          }
          ExpressionAtomNode explicitRef = null;
          if (Modifier.isStatic(referencedMethod.modifiers)) {
            explicitRef = ExpressionAtomNode.Variant.StaticMember.buildNode(
                factory.toTypeNameNode(cn));
          } else {
            FieldInfo fi = thisFieldFor(cn);
            if (fi != null) {
              explicitRef = thisFieldAsAtom(fi.canonName, node);
            }
          }
          if (explicitRef != null) {
            mnn.setReferencedExpressionName(null);
            PrimaryNode invoc = PrimaryNode.Variant.MethodInvocation.buildNode(
                explicitRef,
                mnn);
            ArgumentListNode args = node.firstChildWithType(
                ArgumentListNode.class);
            if (args != null) {
              invoc.add(args);
            }
            ExpressionAtomNode repl =
                ExpressionAtomNode.Variant.Parenthesized.buildNode(
                    ExpressionNode.Variant.ConditionalExpression.buildNode(
                        invoc));
            repl.setSourcePosition(node.getSourcePosition());
            return ProcessingStatus.replace(repl);
          }
          break;
        }
        case ArrayConstructorReference:
        case ArrayCreationExpression:
        case ClassLiteral:
        case ConstructorReference:
        case Literal:
        case Parenthesized:
        case QuotedName:
        case StaticMember:
        case StaticReference:
        case Super:
        case UnqualifiedClassInstanceCreationExpression:
          break;
      }
      return ProcessingStatus.CONTINUE;
    }

    private FieldInfo thisFieldFor(Name canonName) {
      Preconditions.checkArgument(
          canonName.type == Name.Type.CLASS);
      FlatteningType referent = ps.byBumpyName.get(
          BName.of(canonName));
      return referent != null
          ? ft.closedOverMembers.get(
              new PassState.ClosedOverThisValue(referent))
          : null;
    }

    private ExpressionAtomNode thisFieldAsAtom(
        Name fn, J8BaseNode node) {
      Preconditions.checkArgument(fn.type == Name.Type.FIELD);
      ExpressionAtomNode repl =
          ExpressionAtomNode.Variant.Parenthesized.buildNode(
              ExpressionNode.Variant.ConditionalExpression.buildNode(
                  PrimaryNode.Variant.FieldAccess.buildNode(
                      ExpressionAtomNode.Variant.This.buildNode(),
                      FieldNameNode.Variant.Identifier.buildNode(
                          IdentifierNode.Variant.Builtin.buildNode(
                              fn.identifier)))));
      repl.setSourcePosition(node.getSourcePosition());
      return repl;
    }

    private ProcessingStatus handle(PrimaryNode node) {
      switch (node.getVariant()) {
        case Ambiguous:
        case ArrayAccess:
        case ExpressionAtom:
        case InnerClassCreation:
          break;
        case FieldAccess:
        case MethodInvocation: {
          J8ExpressionNameReference nameNode =
              node.firstChildWithType(FieldNameNode.class);
          if (nameNode == null) {
            nameNode = node.firstChildWithType(MethodNameNode.class);
          }
          ExpressionAtomNode o = node.firstChildWithType(
              ExpressionAtomNode.class);
          // If we rewrote the left, clear the field name.
          if (o != null && nameNode != null
              && (
                  o.getVariant() == ExpressionAtomNode.Variant.StaticMember
                  || (
                      o.getVariant() == ExpressionAtomNode.Variant.This
                      && o.getNChildren() == 0))) {
            nameNode.setReferencedExpressionName(null);
          }
          break;
        }
        case MethodReference:
          break;
      }
      return ProcessingStatus.CONTINUE;
    }
  }

  // There are several patterns we need to find.
  // * FieldNames that reference cross-class
  // * LocalNames that references cross-class
  // * MethodNames that reference cross-class
  // * Cross-class references to this
  // We try to handle this by exhaustively looking for
  // * Bare field references
  // * Bare method references
  // * Qualified field references where the referent
  //   is not the current class.
  // * Qualified method calls where the referent
  //   is not the current class.
  // * Qualified used of this where the type name
  //   is not the current class.
  // We then error on any {Field,Local,Method}Names
  // that refer cross-class that we have not seen.


}
