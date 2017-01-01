package com.mikesamuel.cil.ast.passes;

import java.util.logging.Logger;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.mikesamuel.cil.ast.AdditiveExpressionNode;
import com.mikesamuel.cil.ast.AndExpressionNode;
import com.mikesamuel.cil.ast.ArrayCreationExpressionNode;
import com.mikesamuel.cil.ast.AssignmentNode;
import com.mikesamuel.cil.ast.BaseNode;
import com.mikesamuel.cil.ast.CastExpressionNode;
import com.mikesamuel.cil.ast.ClassInstanceCreationExpressionNode;
import com.mikesamuel.cil.ast.ClassLiteralNode;
import com.mikesamuel.cil.ast.CompilationUnitNode;
import com.mikesamuel.cil.ast.ConditionalAndExpressionNode;
import com.mikesamuel.cil.ast.ConditionalExpressionNode;
import com.mikesamuel.cil.ast.ConditionalOrExpressionNode;
import com.mikesamuel.cil.ast.EqualityExpressionNode;
import com.mikesamuel.cil.ast.ExclusiveOrExpressionNode;
import com.mikesamuel.cil.ast.ExpressionAtomNode;
import com.mikesamuel.cil.ast.InclusiveOrExpressionNode;
import com.mikesamuel.cil.ast.MethodInvocationNode;
import com.mikesamuel.cil.ast.MultiplicativeExpressionNode;
import com.mikesamuel.cil.ast.NodeType;
import com.mikesamuel.cil.ast.NodeTypeTables;
import com.mikesamuel.cil.ast.PostExpressionNode;
import com.mikesamuel.cil.ast.PreExpressionNode;
import com.mikesamuel.cil.ast.PrimaryNode;
import com.mikesamuel.cil.ast.RelationalExpressionNode;
import com.mikesamuel.cil.ast.ShiftExpressionNode;
import com.mikesamuel.cil.ast.ShiftOperatorNode;
import com.mikesamuel.cil.ast.UnaryExpressionNode;
import com.mikesamuel.cil.ast.meta.ExpressionNameResolver;
import com.mikesamuel.cil.ast.meta.Name;
import com.mikesamuel.cil.ast.meta.ExpressionNameResolver.DeclarationPositionMarker;
import com.mikesamuel.cil.ast.meta.StaticType;
import com.mikesamuel.cil.ast.meta.StaticType.NumericType;
import com.mikesamuel.cil.ast.meta.StaticType.TypePool;
import com.mikesamuel.cil.ast.meta.TypeNameResolver;
import com.mikesamuel.cil.ast.meta.TypeSpecification;
import com.mikesamuel.cil.ast.traits.ExpressionNameScope;
import com.mikesamuel.cil.ast.traits.LimitedScopeElement;
import com.mikesamuel.cil.ast.traits.TypeScope;
import com.mikesamuel.cil.ast.traits.Typed;

/**
 * Attaches types to {@link Typed} expressions.
 */
final class TypingPass extends AbstractPass<Void> {

  final TypePool typePool;

  TypingPass(Logger logger, TypePool typePool) {
    super(logger);
    this.typePool = typePool;
  }


  private void run(
      BaseNode node,
      ExpressionNameResolver enr,
      DeclarationPositionMarker dpm,
      TypeNameResolver tnr) {

    ExpressionNameResolver exprNameResolver = enr;
    TypeNameResolver typeNameResolver = tnr;
    DeclarationPositionMarker marker = dpm;

    if (node instanceof ExpressionNameScope) {
      ExpressionNameScope scope = (ExpressionNameScope) node;
      exprNameResolver = scope.getExpressionNameResolver();
      if (exprNameResolver == null) {
        exprNameResolver = enr;
      } else {
        marker = DeclarationPositionMarker.EARLIEST;
      }
    }
    if (node instanceof TypeScope) {
      TypeScope scope = (TypeScope) node;
      typeNameResolver = scope.getTypeNameResolver();
      if (typeNameResolver == null) {
        typeNameResolver = tnr;
      }
    }
    if (node instanceof LimitedScopeElement) {
      LimitedScopeElement el = (LimitedScopeElement) node;
      marker = el.getDeclarationPositionMarker();
      if (marker == null) {
        marker = dpm;
      }
    }

    // TODO: propagate expected type information based on initializers and
    // calls to handle poly expressions.

    for (BaseNode child : node.getChildren()) {
      run(child, exprNameResolver, marker, typeNameResolver);
    }

    if (node instanceof Typed) {
      Typed t = (Typed) node;
      StaticType exprType;
      type_switch:
      switch (t.getNodeType()) {
        case BooleanLiteral:
          exprType = StaticType.T_BOOLEAN;
          break type_switch;
        case CharacterLiteral:
          exprType = StaticType.T_CHAR;
          break type_switch;
        case FloatingPointLiteral: {
          String value = node.getValue();
          Preconditions.checkState(value != null && !value.isEmpty());
          switch (value.charAt(value.length() - 1)) {
            default:
              exprType = StaticType.T_DOUBLE;
              break;
            case 'F': case 'f':
              exprType = StaticType.T_FLOAT;
              break;
          }
          break type_switch;
        }
        case IntegerLiteral: {
          String value = node.getValue();
          Preconditions.checkState(value != null && !value.isEmpty());
          switch (value.charAt(value.length() - 1)) {
            default:
              exprType = StaticType.T_INT;
              break;
            case 'L': case 'l':
              exprType = StaticType.T_LONG;
              break;
          }
          break type_switch;
        }
        case NullLiteral:
          exprType = typePool.T_NULL;
          break type_switch;
        case StringLiteral:
          exprType = typePool.type(
              new TypeSpecification(JAVA_LANG_STRING),
              node.getSourcePosition(), logger);
          break type_switch;
        case ExpressionAtom: {
          ExpressionAtomNode e = (ExpressionAtomNode) node;
          switch (e.getVariant()) {
            case ArrayConstructorReference:
              break;
            case ArrayCreationExpression:
              break;
            case ClassLiteral:
              break;
            case ConstructorReference:
              break;
            case FreeField:
              break;
            case Literal:
              exprType = passThru(node);
              break type_switch;
            case Local:
              break;
            case MethodInvocation:
              break;
            case Parenthesized:
              break;
            case StaticMember:
              break;
            case StaticReference:
              break;
            case Super:
              break;
            case This:
              break;
            case UnqualifiedClassInstanceCreationExpression:
              break;
            default:
              break;
          }
          throw new AssertionError(e);
        }

        case Primary: {
          PrimaryNode e = (PrimaryNode) node;
          switch (e.getVariant()) {
          }
          throw new AssertionError(e);
        }

        case MethodInvocation: {
          MethodInvocationNode e = (MethodInvocationNode) node;
          switch (e.getVariant()) {
          }
          throw new AssertionError(e);
        }

        case ClassInstanceCreationExpression: {
          ClassInstanceCreationExpressionNode e = (ClassInstanceCreationExpressionNode) node;
          switch (e.getVariant()) {
          }
          throw new AssertionError(e);
        }

        case ClassLiteral: {
          ClassLiteralNode e = (ClassLiteralNode) node;
          switch (e.getVariant()) {
          }
          throw new AssertionError(e);
        }

        case ArrayCreationExpression: {
          ArrayCreationExpressionNode e = (ArrayCreationExpressionNode) node;
          switch (e.getVariant()) {
          }
          throw new AssertionError(e);
        }
        case Expression:
          exprType = passThru(node);
          break type_switch;
        case Assignment: {
          AssignmentNode e = (AssignmentNode) node;
          switch (e.getVariant()) {
            // TODO
          }
          throw new AssertionError(e);
        }

        case ConditionalExpression: {
          ConditionalExpressionNode e = (ConditionalExpressionNode) node;
          switch (e.getVariant()) {
          }
          throw new AssertionError(e);
        }

        case ConditionalOrExpression: {
          ConditionalOrExpressionNode e = (ConditionalOrExpressionNode) node;
          switch (e.getVariant()) {
          }
          throw new AssertionError(e);
        }

        case ConditionalAndExpression: {
          ConditionalAndExpressionNode e = (ConditionalAndExpressionNode) node;
          switch (e.getVariant()) {
          }
          throw new AssertionError(e);
        }

        case InclusiveOrExpression: {
          InclusiveOrExpressionNode e = (InclusiveOrExpressionNode) node;
          switch (e.getVariant()) {
          }
          throw new AssertionError(e);
        }

        case ExclusiveOrExpression: {
          ExclusiveOrExpressionNode e = (ExclusiveOrExpressionNode) node;
          switch (e.getVariant()) {
          }
          throw new AssertionError(e);
        }

        case AndExpression: {
          AndExpressionNode e = (AndExpressionNode) node;
          switch (e.getVariant()) {
          }
          throw new AssertionError(e);
        }

        case EqualityExpression: {
          EqualityExpressionNode e = (EqualityExpressionNode) node;
          switch (e.getVariant()) {
          }
          throw new AssertionError(e);
        }

        case RelationalExpression: {
          RelationalExpressionNode e = (RelationalExpressionNode) node;
          switch (e.getVariant()) {
          }
          throw new AssertionError(e);
        }

        case ShiftExpression: {
          ShiftExpressionNode e = (ShiftExpressionNode) node;
          switch (e.getVariant()) {
          }
          throw new AssertionError(e);
        }

        case ShiftOperator: {
          ShiftOperatorNode e = (ShiftOperatorNode) node;
          switch (e.getVariant()) {
          }
          throw new AssertionError(e);
        }

        case AdditiveExpression: {
          AdditiveExpressionNode e = (AdditiveExpressionNode) node;
          switch (e.getVariant()) {
          }
          throw new AssertionError(e);
        }

        case MultiplicativeExpression: {
          MultiplicativeExpressionNode e = (MultiplicativeExpressionNode) node;
          switch (e.getVariant()) {
          }
          throw new AssertionError(e);
        }

        case UnaryExpression: {
          UnaryExpressionNode e = (UnaryExpressionNode) node;
          switch (e.getVariant()) {
            case CastExpression:
            case PostExpression:
            case PreExpression:
            case Primary:
              exprType = passThru(e);
              break type_switch;
            case PrefixOperatorUnaryExpression:
              BaseNode operand = nthOperandOf(0, e);
              System.err.println("operand=" + operand + " in " + e);
              if (operand != null) {
                exprType = unboxNumericAsNecessary(passThru(operand));
              } else {
                exprType = StaticType.ERROR_TYPE;
                error(e, "Missing operand");
              }
              break type_switch;
          }
          throw new AssertionError(e);
        }

        case PreExpression: {
          PreExpressionNode e = (PreExpressionNode) node;
          switch (e.getVariant()) {
          }
          throw new AssertionError(e);
        }

        case PostExpression: {
          PostExpressionNode e = (PostExpressionNode) node;
          switch (e.getVariant()) {
          }
          throw new AssertionError(e);
        }

        case CastExpression: {
          CastExpressionNode e = (CastExpressionNode) node;
          switch (e.getVariant()) {
          }
          throw new AssertionError(e);
        }

        default:
          throw new AssertionError(t);
      }
      System.err.println("Got " + exprType + " for " + node.getNodeType());
      t.setStaticType(exprType);
    }

  }

  private StaticType passThru(BaseNode node) {
    for (BaseNode child : node.getChildren()) {
      if (NodeTypeTables.NONSTANDARD.contains(child.getNodeType())) {
        continue;
      }
      System.err.println("Considering " + child.getNodeType() + " in " + node.getNodeType());
      if (child instanceof Typed) {
        StaticType t = ((Typed) child).getStaticType();
        if (t == null) {
          error(child, "Untyped");
          return StaticType.ERROR_TYPE;
        }
        return t;
      }
      return passThru(child);
    }
    error(node, "Untyped");
    return StaticType.ERROR_TYPE;
  }

  private StaticType unboxNumericAsNecessary(StaticType t) {
    System.err.println("Checking numeric " + t);
    // TODO: Unbox
    if (t instanceof NumericType) {
      return t;
    }
    return StaticType.ERROR_TYPE;
  }

  private static final ImmutableSet<NodeType> OPERATOR_NODE_TYPES =
      Sets.immutableEnumSet(
          NodeType.AdditiveOperator,
          NodeType.AmbiguousBinaryUnaryOperator,
          NodeType.AssignmentOperator,
          NodeType.EqualityOperator,
          NodeType.IncrDecrOperator,
          NodeType.MultiplicativeOperator,
          NodeType.PrefixOperator,
          NodeType.RelationalOperator,
          NodeType.ShiftOperator
          );

  private static BaseNode nthOperandOf(int n, BaseNode parent) {
    int nLeft = n;
    for (BaseNode child : parent.getChildren()) {
      if (OPERATOR_NODE_TYPES.contains(child.getNodeType())) {
        continue;
      }
      if (nLeft == 0) {
        return child;
      }
      --nLeft;
    }
    return null;
  }


  @Override
  Void run(Iterable<? extends CompilationUnitNode> compilationUnits) {
    for (CompilationUnitNode cu : compilationUnits) {
      run(cu,
          cu.getExpressionNameResolver(),
          DeclarationPositionMarker.EARLIEST,
          cu.getTypeNameResolver());
    }
    return null;
  }

  private static final Name JAVA =
      Name.DEFAULT_PACKAGE.child("java", Name.Type.PACKAGE);

  private static final Name JAVA_LANG =
      JAVA.child("lang", Name.Type.PACKAGE);

    private static final Name JAVA_LANG_STRING = JAVA_LANG
        .child("String", Name.Type.CLASS);
}
