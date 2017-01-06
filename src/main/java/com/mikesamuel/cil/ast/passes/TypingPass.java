package com.mikesamuel.cil.ast.passes;

import java.util.Arrays;
import java.util.BitSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import javax.annotation.Nullable;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.mikesamuel.cil.ast.AdditiveExpressionNode;
import com.mikesamuel.cil.ast.AndExpressionNode;
import com.mikesamuel.cil.ast.ArgumentListNode;
import com.mikesamuel.cil.ast.ArrayCreationExpressionNode;
import com.mikesamuel.cil.ast.ArrayTypeNode;
import com.mikesamuel.cil.ast.AssignmentNode;
import com.mikesamuel.cil.ast.BaseNode;
import com.mikesamuel.cil.ast.BaseNode.Builder;
import com.mikesamuel.cil.ast.BaseNode.InnerBuilder;
import com.mikesamuel.cil.ast.CastExpressionNode;
import com.mikesamuel.cil.ast.CastNode;
import com.mikesamuel.cil.ast.ClassLiteralNode;
import com.mikesamuel.cil.ast.ClassOrInterfaceTypeNode;
import com.mikesamuel.cil.ast.ConditionalAndExpressionNode;
import com.mikesamuel.cil.ast.ConditionalExpressionNode;
import com.mikesamuel.cil.ast.ConditionalOrExpressionNode;
import com.mikesamuel.cil.ast.ConfirmCastNode;
import com.mikesamuel.cil.ast.ConvertCastNode;
import com.mikesamuel.cil.ast.DimNode;
import com.mikesamuel.cil.ast.EqualityExpressionNode;
import com.mikesamuel.cil.ast.ExclusiveOrExpressionNode;
import com.mikesamuel.cil.ast.ExpressionAtomNode;
import com.mikesamuel.cil.ast.ExpressionNode;
import com.mikesamuel.cil.ast.FieldNameNode;
import com.mikesamuel.cil.ast.FloatingPointTypeNode;
import com.mikesamuel.cil.ast.IdentifierNode;
import com.mikesamuel.cil.ast.InclusiveOrExpressionNode;
import com.mikesamuel.cil.ast.IntegralTypeNode;
import com.mikesamuel.cil.ast.Intermediates;
import com.mikesamuel.cil.ast.MethodNameNode;
import com.mikesamuel.cil.ast.MultiplicativeExpressionNode;
import com.mikesamuel.cil.ast.NodeType;
import com.mikesamuel.cil.ast.NodeTypeTables;
import com.mikesamuel.cil.ast.NodeVariant;
import com.mikesamuel.cil.ast.NumericTypeNode;
import com.mikesamuel.cil.ast.PostExpressionNode;
import com.mikesamuel.cil.ast.PreExpressionNode;
import com.mikesamuel.cil.ast.PrimaryNode;
import com.mikesamuel.cil.ast.PrimitiveTypeNode;
import com.mikesamuel.cil.ast.ReferenceTypeNode;
import com.mikesamuel.cil.ast.RelationalExpressionNode;
import com.mikesamuel.cil.ast.ShiftExpressionNode;
import com.mikesamuel.cil.ast.ShiftOperatorNode;
import com.mikesamuel.cil.ast.TypeArgumentListNode;
import com.mikesamuel.cil.ast.TypeArgumentNode;
import com.mikesamuel.cil.ast.TypeArgumentsNode;
import com.mikesamuel.cil.ast.TypeNameNode;
import com.mikesamuel.cil.ast.TypeNode;
import com.mikesamuel.cil.ast.UnannTypeNode;
import com.mikesamuel.cil.ast.UnaryExpressionNode;
import com.mikesamuel.cil.ast.VariableInitializerNode;
import com.mikesamuel.cil.ast.WildcardBoundsNode;
import com.mikesamuel.cil.ast.WildcardNode;
import com.mikesamuel.cil.ast.meta.CallableInfo;
import com.mikesamuel.cil.ast.meta.ExpressionNameResolver;
import com.mikesamuel.cil.ast.meta.Name;
import com.mikesamuel.cil.ast.meta.ExpressionNameResolver.DeclarationPositionMarker;
import com.mikesamuel.cil.ast.meta.FieldInfo;
import com.mikesamuel.cil.ast.meta.MemberInfoPool;
import com.mikesamuel.cil.ast.meta.MemberInfoPool.ParameterizedMember;
import com.mikesamuel.cil.ast.meta.StaticType;
import com.mikesamuel.cil.ast.meta.StaticType.Cast;
import com.mikesamuel.cil.ast.meta.StaticType.NumericType;
import com.mikesamuel.cil.ast.meta.StaticType.PrimitiveType;
import com.mikesamuel.cil.ast.meta.StaticType.TypePool;
import com.mikesamuel.cil.ast.meta.StaticType.TypePool.ArrayType;
import com.mikesamuel.cil.ast.meta.StaticType.TypePool.ClassOrInterfaceType;
import com.mikesamuel.cil.ast.meta.StaticType.TypePool.ReferenceType;
import com.mikesamuel.cil.ast.meta.TypeInfo;
import com.mikesamuel.cil.ast.meta.TypeNameResolver;
import com.mikesamuel.cil.ast.meta.TypeSpecification;
import com.mikesamuel.cil.ast.meta.TypeSpecification.TypeBinding;
import com.mikesamuel.cil.ast.traits.ExpressionNameScope;
import com.mikesamuel.cil.ast.traits.LimitedScopeElement;
import com.mikesamuel.cil.ast.traits.TypeDeclaration;
import com.mikesamuel.cil.ast.traits.TypeScope;
import com.mikesamuel.cil.ast.traits.Typed;
import com.mikesamuel.cil.ast.traits.WholeType;
import com.mikesamuel.cil.parser.SList;

import jdk.nashorn.internal.ir.CaseNode;

/**
 * Attaches types to {@link Typed} expressions.
 */
final class TypingPass extends AbstractRewritingPass {

  final TypePool typePool;
  /**
   * Whether to add casts when there is a mismatch between the type of an
   * operand and the type consumed by an operator.  This results in casts
   * wherever boxing and unboxing occurs, whenever an unchecked generic
   * conversion occurs, and on numeric type promotion.
   */
  final boolean injectCasts;
  final MemberInfoPool memberInfoPool;

  private final LinkedList<ExpressionNameResolver> expressionNameResolvers
      = Lists.newLinkedList();
  private final LinkedList<DeclarationPositionMarker> declarationPositionMarkers
      = Lists.newLinkedList();
  private final LinkedList<TypeNameResolver> typeNameResolvers
      = Lists.newLinkedList();
  private final LinkedList<TypeInfo> containingTypes = Lists.newLinkedList();

  static final boolean DEBUG = false;

  TypingPass(Logger logger, TypePool typePool, boolean injectCasts) {
    super(logger);
    this.typePool = typePool;
    this.injectCasts = injectCasts;
    this.memberInfoPool = new MemberInfoPool(typePool);
  }


  @Override
  protected <N extends BaseNode> ProcessingStatus previsit(
      N node, @Nullable SList<Parent> pathFromRoot,
      BaseNode.Builder<N, ?> builder) {

    if (node instanceof ExpressionNameScope) {
      ExpressionNameScope scope = (ExpressionNameScope) node;
      ExpressionNameResolver exprNameResolver =
          scope.getExpressionNameResolver();
      DeclarationPositionMarker marker = DeclarationPositionMarker.EARLIEST;
      if (exprNameResolver == null) {
        exprNameResolver = expressionNameResolvers.peekLast();
        marker = declarationPositionMarkers.peekLast();
      }
      expressionNameResolvers.add(exprNameResolver);
      declarationPositionMarkers.add(marker);
    }
    if (node instanceof TypeScope) {
      TypeScope scope = (TypeScope) node;
      TypeNameResolver typeNameResolver = scope.getTypeNameResolver();
      if (typeNameResolver == null) {
        typeNameResolver = typeNameResolvers.peekLast();
      }
      typeNameResolvers.add(typeNameResolver);
    }

    if (node instanceof LimitedScopeElement) {
      LimitedScopeElement el = (LimitedScopeElement) node;
      DeclarationPositionMarker marker = el.getDeclarationPositionMarker();
      if (marker != null) {
        declarationPositionMarkers.set(
            declarationPositionMarkers.size() - 1, marker);
      }
    }

    if (node instanceof TypeDeclaration) {
      TypeInfo typeInfo = ((TypeDeclaration) node).getDeclaredTypeInfo();
      if (typeInfo == null) {
        typeInfo = containingTypes.peekLast();
      }
      containingTypes.add(typeInfo);
    }

    // TODO: propagate expected type information based on initializers and
    // calls to handle poly expressions.
    return ProcessingStatus.CONTINUE;
  }

  @Override
  protected <N extends BaseNode> ProcessingStatus postvisit(
      N node, @Nullable SList<Parent> pathFromRoot,
      BaseNode.Builder<N, ?> builder) {
    ProcessingStatus result = process(node, pathFromRoot, builder);

    if (node instanceof ExpressionNameScope) {
      expressionNameResolvers.removeLast();
      declarationPositionMarkers.removeLast();
    }

    if (node instanceof TypeScope) {
      typeNameResolvers.removeLast();
    }

    if (node instanceof TypeDeclaration) {
      containingTypes.removeLast();
    }

    return result;
  }

  private <N extends BaseNode>
  ProcessingStatus process(
      N unchangedNode, @Nullable SList<Parent> pathFromRoot,
      BaseNode.Builder<N, ?> builder) {

    if (unchangedNode instanceof Typed) {
      N node = builder.changed() ? builder.build() : unchangedNode;
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
              // TODO
              break;
            case ArrayCreationExpression:
              // TODO
              break;
            case ClassLiteral:
              // TODO
              break;
            case ConstructorReference:
              // TODO
              break;
            case FreeField:
              exprType = processFieldAccess(e, (PrimaryNode.Builder) builder);
              break type_switch;
            case Literal:
              exprType = passThru(node);
              break type_switch;
            case Local:
              // TODO
              break;
            case MethodInvocation:
              exprType = processMethodInvocation(
                  e, (ExpressionAtomNode.Builder) builder);
              break type_switch;
            case Parenthesized:
              // TODO
              break;
            case StaticMember: {
              TypeNameNode tn = e.firstChildWithType(TypeNameNode.class);
              TypeInfo ti = tn != null ? tn.getReferencedTypeInfo() : null;
              if (ti != null) {
                exprType = typePool.type(
                    new TypeSpecification(ti.canonName),
                    e.getSourcePosition(), logger);
              } else {
                error(e, "Cannot find info for type " + e.getTextContent("."));
                exprType = StaticType.ERROR_TYPE;
              }
              break type_switch;
            }
            case StaticReference:
              // TODO
              break;
            case Super:
              // TODO
              break;
            case This:
              exprType = typePool.type(
                  TypeSpecification.autoScoped(containingTypes.peekLast()),
                  e.getSourcePosition(), logger);
              break type_switch;
            case UnqualifiedClassInstanceCreationExpression:
              // TODO
              break;
            default:
              break;
          }
          throw new AssertionError(e);
        }

        case Primary: {
          PrimaryNode e = (PrimaryNode) node;
          switch (e.getVariant()) {
            case MethodInvocation: {
              exprType = processMethodInvocation(
                  e, (PrimaryNode.Builder) builder);
              break type_switch;
            }

            case FieldAccess: {
              exprType = processFieldAccess(e, (PrimaryNode.Builder) builder);
              break type_switch;
            }
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
              Operand operand = nthOperandOf(
                  0, builder, NodeType.UnaryExpression);
              if (operand != null) {
                exprType = unboxNumericAsNecessary(operand, passThru(operand));
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
          CastNode castNode = e.firstChildWithType(CastNode.class);
          exprType = null;
          for (WholeType wt
               : e.finder(WholeType.class)
                 .exclude(NodeType.UnaryExpression, NodeType.LambdaExpression)
                 .exclude(WholeType.class)
                 .find()) {
            exprType = wt.getStaticType();
          }
          if (exprType == null) {
            error(castNode, "Unrecognized target type for cast");
            exprType = StaticType.ERROR_TYPE;
          }
          break type_switch;
        }

        default:
          throw new AssertionError(t);
      }
      if (DEBUG) {
        System.err.println("Got " + exprType + " for " + node.getNodeType());
      }
      if (builder.changed()) {
        BaseNode replacement = builder.build();
        ((Typed) replacement).setStaticType(exprType);
        return ProcessingStatus.replace(replacement);
      } else {
        t.setStaticType(exprType);
        return ProcessingStatus.CONTINUE;
      }
    }

    if (unchangedNode instanceof VariableInitializerNode) {
      Operand op = nthOperandOf(0, builder, NodeType.Expression);
      if (op == null) { return ProcessingStatus.CONTINUE; }
      StaticType exprType = ((Typed) op.getNode()).getStaticType();
      if (exprType == null) { return ProcessingStatus.CONTINUE; }

      SList<Parent> path = pathFromRoot;
      int nDims = 0;
      for (; path != null; path = path.prev) {
        NodeType nt = path.x.parent.getNodeType();
        switch (nt) {
          case VariableDeclarator:
          case VariableDeclaratorList:
          case VariableInitializer:
            continue;
          case ArrayInitializer:
            ++nDims;
            continue;
          case FieldDeclaration:
          case ConstantDeclaration:
          case LocalVariableDeclaration:
            UnannTypeNode declarationTypeNode =
            path.x.parent.firstChildWithType(UnannTypeNode.class);
            if (declarationTypeNode == null) {
              break;
            }
            StaticType declarationType =
                declarationTypeNode.getStaticType();
            if (declarationType == null) {
              break;
            }
            while (nDims != 0) {
              if (declarationType instanceof ArrayType) {
                declarationType =
                    ((ArrayType) declarationType).elementType;
                --nDims;
              } else {
                break;
              }
            }
            if (nDims == 0) {
              Cast c = declarationType.assignableFrom(exprType);
              switch (Compatibility.of(c)) {
                case IMPLICIT_CAST:
                  op.cast(exprType, declarationType);
                  exprType = declarationType;
                  break;
                case COMPATIBLE_AS_IS:
                  break;
                case INCOMPATIBLE:
                  error(
                      unchangedNode,
                      "Cannot use expression of type " + exprType
                      + " to initialize declaration of type "
                      + declarationType);
              }
            } else {
              error(
                  unchangedNode,
                  "array initializer element assigned to non-array type");
            }
            break;
          default:
            break;
        }
        break;
      }
    }
    return ProcessingStatus.CONTINUE;
  }

  private StaticType processFieldAccess(
      BaseNode e, BaseNode.InnerBuilder<?, ?> builder) {
    @Nullable Typed container = e.firstChildWithType(Typed.class);
    StaticType containerType;
    if (container != null) {
      containerType = container.getStaticType();
      if (containerType == null) {
        error(container, "Missing type for field container");
        return StaticType.ERROR_TYPE;
      }
    } else {
      containerType = null;
    }
    FieldNameNode nameNode = e.firstChildWithType(FieldNameNode.class);
    IdentifierNode nameIdent = nameNode != null
        ? nameNode.firstChildWithType(IdentifierNode.class)
        : null;
    String fieldName = nameIdent != null ? nameIdent.getValue() : null;
    if (fieldName == null) {
      error(e, "Missing field name");
      return StaticType.ERROR_TYPE;
    }

    Name sourceType = containingTypes.peekLast().canonName;

    ImmutableList<ParameterizedMember<FieldInfo>> fields =
        ImmutableList.of();
    for (TypeSpecification oneContainer : getMemberContainers(containerType)) {
      fields = memberInfoPool.getMembers(
          FieldInfo.class,
          fieldName,
          sourceType,
          oneContainer);
      if (!fields.isEmpty()) {
        // Implement name shadowing by taking the first that has any accessible
        // with the right name.
        break;
      }
    }

    if (fields.isEmpty()) {
      error(
          e, "Reference to undefined or inaccessible field named " + fieldName);
    }

    ParameterizedMember<FieldInfo> f = fields.get(0);
    TypeSpecification typeInDeclaringClass = f.member.getValueType();

    TypeSpecification valueTypeInContext;

    if (f.declaringType.bindings.isEmpty()) {
      valueTypeInContext = typeInDeclaringClass;
    } else {
      Map<Name, TypeBinding> substMap = Maps.newLinkedHashMap();
      Optional<TypeInfo> tiOpt = typePool.r.resolve(
          f.declaringType.typeName);
      if (tiOpt.isPresent()) {
        TypeInfo ti = tiOpt.get();
        int nTypeParams = ti.parameters.size();
        Preconditions.checkState(
            nTypeParams == f.declaringType.bindings.size());
        for (int i = 0; i < nTypeParams; ++i) {
          substMap.put(
              ti.parameters.get(i), f.declaringType.bindings.get(i));
        }
      } else {
        error(e, "Missing info for declaring type " + f.declaringType);
      }
      valueTypeInContext = typeInDeclaringClass.subst(substMap);
    }

    return typePool.type(valueTypeInContext, e.getSourcePosition(), logger);
  }

  private StaticType processMethodInvocation(
      BaseNode e, BaseNode.InnerBuilder<?, ?> builder) {
    Typed callee = e.firstChildWithType(Typed.class);

    @Nullable TypeArgumentsNode args = e.firstChildWithType(
        TypeArgumentsNode.class);

    @Nullable Operand actualsOp = firstWithType(
        builder, NodeType.ArgumentList);
    @Nullable ArgumentListNode actuals = actualsOp != null
        ? (ArgumentListNode) actualsOp.getNode() : null;

    MethodNameNode nameNode = e.firstChildWithType(
        MethodNameNode.class);
    IdentifierNode nameIdent = nameNode != null
        ? nameNode.firstChildWithType(IdentifierNode.class) : null;
    String name = nameIdent != null ? nameIdent.getValue() : null;

    if (name == null) {
      error(e, "Cannot determine name of method invoked");
      return StaticType.ERROR_TYPE;
    }
    Preconditions.checkNotNull(nameNode);

    StaticType calleeType;
    if (callee == null) {
      // We can't commit to the callee type here because
      // class C {
      //   void f() {}
      //   class D {
      //     void g() {}
      //     {
      //       f();  // callee type is C
      //       g();  // callee type is D
      //     }
      //   }
      // }
      // We delay method scope checks and the application of
      // shadowing rules (JLS 6.4.1) until we've got more info.

      calleeType = null;
    } else {
      // There's no need to box callee.  For example:
      //    int i = 0;
      //    (i).toString();
      // fails at compile time, because primitive types cannot
      // be used as left hand sides.
      calleeType = callee.getStaticType();
      if (calleeType == null) {
        calleeType = StaticType.ERROR_TYPE;
        error(callee, "Cannot determine type for method target");
        return StaticType.ERROR_TYPE;
      } else if (calleeType instanceof PrimitiveType
                 || typePool.T_NULL.equals(calleeType)) {
        error(
            callee, calleeType + " cannot be target of invocation");
        return StaticType.ERROR_TYPE;
      } else if (StaticType.ERROR_TYPE.equals(calleeType)) {
        return StaticType.ERROR_TYPE;
      }
    }

    ImmutableList.Builder<StaticType> actualTypes =
        ImmutableList.builder();
    if (actuals != null) {
      for (BaseNode actual : actuals.getChildren()) {
        if (actual instanceof Typed) {
          StaticType actualType = ((Typed) actual).getStaticType();
          if (actualType == null) {
            actualType = StaticType.ERROR_TYPE;
          }
          actualTypes.add(actualType);
        }
      }
    }

    ImmutableList.Builder<TypeBinding> typeArguments =
        ImmutableList.builder();
    if (args != null) {
      TypeNameResolver canonResolver = typeNameResolvers.peekLast();
      for (TypeArgumentNode arg
          : args.finder(TypeArgumentNode.class)
            .exclude(NodeType.TypeArguments)
            .find()) {
        TypeBinding b = AmbiguousNames.typeBindingOf(
            arg, canonResolver, logger);
        typeArguments.add(b);
      }
    }

    Optional<MethodSearchResult> invokedMethodOpt = pickMethodOverload(
        e, calleeType, typeArguments.build(), name,
        actualTypes.build());
    if (!invokedMethodOpt.isPresent()) {
      error(e, "Unresolved use of method " + name);
      return StaticType.ERROR_TYPE;
    }

    MethodSearchResult invokedMethod = invokedMethodOpt.get();

    // Associate method descriptor with invokedMethod.
    nameNode.setMethodDescriptor(invokedMethod.m.member.getDescriptor());

    // Inject casts for actual parameters.
    if (this.injectCasts && actuals != null) {
      Preconditions.checkNotNull(actualsOp);
      boolean castNeeded = false;
      for (Cast c : invokedMethod.actualToFormalCasts) {
        switch (Compatibility.of(c)) {
          case IMPLICIT_CAST:
            castNeeded = true;
            break;
          case COMPATIBLE_AS_IS:
          case INCOMPATIBLE:
            break;
        }
      }
      if (castNeeded) {
        ArgumentListNode.Builder newActuals = actuals.builder();
        int actualIndex = -1;
        for (int i = 0, n = newActuals.getNChildren(); i < n; ++i) {
          BaseNode actual = newActuals.getChild(i);
          if (actual instanceof ExpressionNode) {
            ++actualIndex;
            ExpressionNode actualExpr = (ExpressionNode) actual;
            StaticType actualType =  actualExpr.getStaticType();
            StaticType formalType = invokedMethod.formalTypesInContext
                .get(actualIndex);
            if (Compatibility.of(
                    invokedMethod.actualToFormalCasts
                    .get(actualIndex))
                == Compatibility.IMPLICIT_CAST) {
              Operand op = new Operand(
                  newActuals, actualIndex, NodeType.Expression);
              op.cast(actualType, formalType);
            }
          }
        }

        actualsOp.parentBuilder.replace(
            actualsOp.indexInParent, newActuals.build());
      }
    }
    return invokedMethod.returnTypeInContext;
  }

  private StaticType passThru(Operand op) {
    return passThru(op.getNode());
  }

  private StaticType passThru(BaseNode node) {
    for (BaseNode child : node.getChildren()) {
      if (NodeTypeTables.NONSTANDARD.contains(child.getNodeType())) {
        continue;
      }
      if (DEBUG) {
        System.err.println(
            "Considering " + child.getNodeType() + " in " + node.getNodeType());
      }
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

  private StaticType unboxNumericAsNecessary(Operand op, StaticType t) {
    if (t instanceof NumericType) {
      return t;
    } else if (t instanceof TypePool.ClassOrInterfaceType) {
      TypePool.ClassOrInterfaceType ct = (TypePool.ClassOrInterfaceType) t;
      PrimitiveType pt = TO_WRAPPED.get(ct.info.canonName);
      if (pt != null && pt instanceof NumericType) {
        op.cast(ct, pt);
        return pt;
      }
    }
    error(op.getNode(), "Cannot unbox " + t + " to a numeric type");
    return StaticType.ERROR_TYPE;
  }

  private static final ImmutableMap<NumericType, NodeVariant>
      NUMERIC_TYPE_TO_VARIANT =
      ImmutableMap.<NumericType, NodeVariant>builder()
      .put(StaticType.T_BYTE,   IntegralTypeNode.Variant.Byte)
      .put(StaticType.T_CHAR,   IntegralTypeNode.Variant.Char)
      .put(StaticType.T_SHORT,  IntegralTypeNode.Variant.Short)
      .put(StaticType.T_INT,    IntegralTypeNode.Variant.Int)
      .put(StaticType.T_LONG,   IntegralTypeNode.Variant.Long)
      .put(StaticType.T_FLOAT,  FloatingPointTypeNode.Variant.Float)
      .put(StaticType.T_DOUBLE, FloatingPointTypeNode.Variant.Double)
      .build();

  private static final ImmutableMap<Name, PrimitiveType> TO_WRAPPED;
  static {
    ImmutableMap.Builder<Name, PrimitiveType> b =
        ImmutableMap.<Name, PrimitiveType>builder();
    for (PrimitiveType pt : StaticType.PRIMITIVE_TYPES) {
      b.put(pt.wrapperType, pt);
      if (pt instanceof NumericType) {
        Preconditions.checkState(NUMERIC_TYPE_TO_VARIANT.containsKey(pt));
      }
    }
    TO_WRAPPED = b.build();
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

  final class Operand {
    final BaseNode.InnerBuilder<?, ?> parentBuilder;
    final int indexInParent;
    /**
     * An ancestor type for the operand which may not be exactly the same due
     * to {@link NodeVariant#isAnon()}.
     */
    final NodeType containerType;

    Operand(
        BaseNode.InnerBuilder<?, ?> parentBuilder, int indexInParent,
        NodeType containerType) {
      this.parentBuilder = parentBuilder;
      this.indexInParent = indexInParent;
      this.containerType = containerType;
    }

    void cast(StaticType sourceType, StaticType targetType) {
      if (!injectCasts) { return; }
      if (typePool.T_NULL.equals(sourceType)) {
        if (targetType instanceof ReferenceType) {
          return;
        }
      }
      BaseNode toCast = getNode();
      Optional<BaseNode> castable = Intermediates.wrap(
          toCast, NodeType.UnaryExpression,
          new Function<BaseNode, Void> () {
            @Override
            public Void apply(BaseNode intermediate) {
              if (intermediate instanceof Typed) {
                ((Typed) intermediate).setStaticType(sourceType);
              }
              return null;
            }
          });
      if (castable.isPresent()) {
        toCast = castable.get();
      } else {
        error(toCast, "Cannot cast " + sourceType + " to " + targetType);
        return;
      }
      CastNode cast;
      if (targetType instanceof PrimitiveType) {
        PrimitiveTypeNode targetTypeNode = toPrimitiveTypeNode(
            (PrimitiveType) targetType);
        cast = CastNode.Variant.ConvertCast.nodeBuilder()
            .add(ConvertCastNode.Variant.PrimitiveType.nodeBuilder()
                .add(targetTypeNode)
                .build())
            .build();
      } else {
        // TODO: handle +/- unary op ambiguity.
        // Maybe, if it's not an ExpressionAtom.Parenthesized, then
        // wrap it.
        ReferenceTypeNode targetTypeNode = toReferenceTypeNode(
            (ReferenceType) targetType);
        cast = CastNode.Variant.ConfirmCast.nodeBuilder()
            .add(ConfirmCastNode.Variant.ReferenceTypeAdditionalBound
                .nodeBuilder()
                .add(targetTypeNode)
                .build())
            .build();
      }
      CastExpressionNode castExpr = CastExpressionNode.Variant.Expression
          .nodeBuilder()
          .add(cast)
          .add(toCast)
          .build();

      Optional<BaseNode> wrappedCast = Intermediates.wrap(
          castExpr, containerType,
          new Function<BaseNode, Void> () {
            @Override
            public Void apply(BaseNode intermediate) {
              if (intermediate instanceof Typed) {
                ((Typed) intermediate).setStaticType(targetType);
              }
              return null;
            }
          });

      if (wrappedCast.isPresent()) {
        parentBuilder.replace(indexInParent, wrappedCast.get());
      } else {
        error(toCast, "Cannot cast to " + targetType);
      }
    }

    BaseNode getNode() {
      return parentBuilder.getChild(indexInParent);
    }
  }

  private Operand nthOperandOf(
      int n, BaseNode.Builder<?, ?> parentBuilder, NodeType containerType) {
    if (parentBuilder instanceof BaseNode.InnerBuilder) {
      BaseNode.InnerBuilder<?, ?> ibuilder =
          (InnerBuilder<?, ?>) parentBuilder;

      int nLeft = n;
      int nChildren = ibuilder.getNChildren();
      for (int i = 0; i < nChildren; ++i) {
        BaseNode child = ibuilder.getChild(i);
        NodeType childNodeType = child.getNodeType();
        if (OPERATOR_NODE_TYPES.contains(childNodeType)) {
          continue;
        }
        if (nLeft == 0) {
          return new Operand(ibuilder, i, containerType);
        }
        --nLeft;
      }
    }
    return null;
  }

  private Operand firstWithType(Builder<?, ?> builder, NodeType t) {
    if (!(builder instanceof BaseNode.InnerBuilder)) {
      return null;
    }
    BaseNode.InnerBuilder<?, ?> ibuilder = (InnerBuilder<?, ?>) builder;
    for (int i = 0, n = ibuilder.getNChildren(); i < n; ++i) {
      BaseNode child = ibuilder.getChild(i);
      if (child.getNodeType() == t) {
        return new Operand(ibuilder, i, t);
      }
    }
    return null;
  }

  private static final Name JAVA =
      Name.DEFAULT_PACKAGE.child("java", Name.Type.PACKAGE);

  private static final Name JAVA_LANG =
      JAVA.child("lang", Name.Type.PACKAGE);

  private static final Name JAVA_LANG_STRING =
      JAVA_LANG.child("String", Name.Type.CLASS);

  /**
   * The containers to try in order when looking up a member.
   * <p>
   * @param explicitContainerType null if the member use is a free use.
   */
  private ImmutableList<TypeSpecification> getMemberContainers(
      @Nullable StaticType explicitContainerType) {
    if (explicitContainerType != null) {
      return ImmutableList.of(explicitContainerType.typeSpecification);
    }
    TypeInfo last = null;
    ImmutableList.Builder<TypeSpecification> b = ImmutableList.builder();
    for (TypeInfo containingType : Lists.reverse(containingTypes)) {
      if (last != containingType) {
        ImmutableList.Builder<TypeSpecification.TypeBinding> bindings =
            ImmutableList.builder();
        for (Name param : containingType.parameters) {
          bindings.add(new TypeSpecification.TypeBinding(param));
        }
        b.add(TypeSpecification.autoScoped(containingType));
        last = containingType;
      }
    }
    // TODO: Fall back to static imports.
    // TODO: This will require the member name be passed in.
    return b.build();
  }

  /**
   * @param sourceNode
   * @param calleeType
   * @param typeArguments
   * @param methodName
   * @param actualTypes
   * @return
   */
  private Optional<MethodSearchResult> pickMethodOverload(
      BaseNode sourceNode,
      @Nullable StaticType calleeType,
      ImmutableList<TypeBinding> typeArguments,
      String methodName,
      ImmutableList<StaticType> actualTypes) {
    if (DEBUG) {
      System.err.println("calleeType=" + calleeType);
      System.err.println("typeArguments=" + typeArguments);
      System.err.println("actualTypes=" + actualTypes);
    }
    Name sourceType = containingTypes.peekLast().canonName;

    ImmutableList<TypeSpecification> calleeTypes = getMemberContainers(
        calleeType);

    ImmutableList<ParameterizedMember<CallableInfo>> methods =
        ImmutableList.of();
    for (TypeSpecification oneContainingType : calleeTypes) {
      methods = memberInfoPool.getMembers(
          CallableInfo.class,
          methodName,
          sourceType,
          oneContainingType);
      if (!methods.isEmpty()) {
        // Implement name shadowing by taking the first that has any accessible
        // with the right name.
        break;
      }
    }

    // After we've found the methods, filter by arity and signature.
    methods = ImmutableList.copyOf(Iterables.filter(
        methods,
        new Predicate<ParameterizedMember<CallableInfo>>() {

          @Override
          public boolean apply(ParameterizedMember<CallableInfo> m) {
            // Arity check
            int minArity = m.member.getFormalTypes().size();
            int maxArity = minArity;
            if (m.member.isVariadic()) {
              --minArity;
              maxArity = Integer.MAX_VALUE;
            }

            int nActuals = actualTypes.size();
            if (nActuals < minArity || nActuals > maxArity) {
              return false;
            }

            // Parameter arity check
            int nTypeArguments = typeArguments.size();
            if (nTypeArguments != 0) {  // Type arguments provided explicitly.
              // filter based on type argument arity
              if (m.member.typeParameters.size() != nTypeArguments) {
                return false;
              }
            }

            return true;
          }

        }));
    if (DEBUG) {
      for (ParameterizedMember<CallableInfo> m : methods) {
        System.err.println("METHOD " + m.declaringType
            + " : " + m.member.canonName + " : " + m.member.getDescriptor());
      }
    }

    // Check that the parameter types are applicable.
    List<MethodSearchResult> results = Lists.newArrayList();
    {
      int nActuals = actualTypes.size();
      int nMethods = methods.size();
      for (int j = 0; j < nMethods; ++j) {
        ParameterizedMember<CallableInfo> m = methods.get(j);
        Map<Name, TypeBinding> substMap = Maps.newLinkedHashMap();
        if (!m.declaringType.bindings.isEmpty()) {
          Optional<TypeInfo> tiOpt = typePool.r.resolve(
              m.declaringType.typeName);
          if (tiOpt.isPresent()) {
            TypeInfo ti = tiOpt.get();
            int nTypeParams = ti.parameters.size();
            Preconditions.checkState(
                nTypeParams == m.declaringType.bindings.size());
            for (int i = 0; i < nTypeParams; ++i) {
              substMap.put(
                  ti.parameters.get(i), m.declaringType.bindings.get(i));
            }
          } else {
            error(
                sourceNode, "Missing info for declaring type " + m.declaringType);
          }
        }
        if (!typeArguments.isEmpty()) {
          for (int i = 0, n = typeArguments.size(); i < n; ++i) {
            substMap.put(
                m.member.typeParameters.get(i),
                typeArguments.get(i));
          }
        }

        ImmutableList<TypeSpecification> formalTypes =
            m.member.getFormalTypes();
        int arity = formalTypes.size();
        ImmutableList<StaticType> formalTypesInContext;
        {
          ImmutableList.Builder<StaticType> b = ImmutableList.builder();
          for (int i = 0; i < arity; ++i) {
            b.add(typePool.type(
                formalTypes.get(i).subst(substMap),
                sourceNode.getSourcePosition(), logger));
          }
          formalTypesInContext = b.build();
        }

        boolean compatible = true;
        ImmutableList.Builder<Cast> actualToFormalCasts =
            ImmutableList.builder();
        boolean requiresVariadicArrayConstruction = false;
        method_loop:
        for (int i = 0; i < nActuals; ++i) {
          StaticType formalType;
          StaticType actualType = actualTypes.get(i);
          if (m.member.isVariadic() && i >= arity - 1) {
            formalType = formalTypesInContext.get(i - 1);
            if (formalType instanceof ArrayType) {
              boolean isCompatibleArrayTypeOrNull = false;
              if (arity == nActuals) {
                // Handle the case where an array is passed instead of an array
                // being implicitly constructed to handle the extra arguments.
                Cast c = formalType.assignableFrom(actualType);
                switch (c) {
                  case BOX:
                  case CONFIRM_CHECKED:
                  case CONVERTING_LOSSLESS:
                  case CONVERTING_LOSSY:
                  case UNBOX:
                  case DISJOINT:
                    break;
                  case CONFIRM_SAFE:
                  case CONFIRM_UNCHECKED:  // Array of generics.
                  case SAME:
                    isCompatibleArrayTypeOrNull = true;
                    break;
                }
              }
              if (!isCompatibleArrayTypeOrNull) {
                requiresVariadicArrayConstruction = true;
                formalType = ((ArrayType) formalType).elementType;
              }
            } else {
              // Assume reason for ErrorType already logged.
            }
          } else {
            formalType = formalTypesInContext.get(i);
          }

          StaticType.Cast castRequired = formalType.assignableFrom(actualType);
          actualToFormalCasts.add(castRequired);
          switch (Compatibility.of(castRequired)) {
            case COMPATIBLE_AS_IS:
            case IMPLICIT_CAST:
              continue;
            case INCOMPATIBLE:
              // Explicit cast required.
              compatible = false;
              break method_loop;
          }
        }
        if (compatible) {
          StaticType returnTypeInContext = typePool.type(
                m.member.getReturnType().subst(substMap),
                sourceNode.getSourcePosition(), logger);
          results.add(new MethodSearchResult(
              m, formalTypesInContext, returnTypeInContext,
              actualToFormalCasts.build(),
              requiresVariadicArrayConstruction));
        }
      }
    }

    {
      // Now if any required no boxing/unboxing, eliminate any that require
      // boxing/unboxing.
      // 15.12.2. Compile-Time Step 2: Determine Method Signature says
      // """
      // The first phase (S15.12.2.2) performs overload resolution without
      // permitting boxing or unboxing conversion, or the use of variable
      // arity method invocation. If no applicable method is found during this
      // phase then processing continues to the second phase.
      // """
      BitSet requiresUnOrBoxing = new BitSet();
      for (int i = 0, n = results.size(); i < n; ++i) {
        MethodSearchResult result = results.get(i);
        for (Cast c : result.actualToFormalCasts) {
          if (c == Cast.BOX || c == Cast.UNBOX) {
            requiresUnOrBoxing.set(i);
            break;
          }
        }
      }
      boolean oneRequiresUnOrBoxing = requiresUnOrBoxing.nextSetBit(0) >= 0;
      boolean oneDoesntRequireUnOrBoxing =
          requiresUnOrBoxing.nextClearBit(0) < results.size();
      if (oneRequiresUnOrBoxing && oneDoesntRequireUnOrBoxing) {
        for (int i = results.size(); --i >= 0;) {
          if (requiresUnOrBoxing.get(i)) {
            results.remove(i);
          }
        }
      }

      // The second phase (S15.12.2.3) performs overload resolution while
      // allowing boxing and unboxing, but still precludes the use of variable
      // arity method invocation. If no applicable method is found during this
      // phase then processing continues to the third phase.
      if (results.size() > 1) {
        BitSet requiresVariadic = new BitSet();
        for (int i = 0, n = results.size(); i < n; ++i) {
          MethodSearchResult result = results.get(i);
          if (result.constructsVariadicArray) {
            requiresVariadic.set(i);
          }
        }
        boolean oneRequiresVariadic = requiresVariadic.nextSetBit(0) >= 0;
        boolean oneDoesntRequireVariadic =
            requiresVariadic.nextClearBit(0) < results.size();
        if (oneRequiresVariadic && oneDoesntRequireVariadic) {
          for (int i = results.size(); --i >= 0;) {
            if (requiresVariadic.get(i)) {
              results.remove(i);
            }
          }
        }
      }
    }

    // Now compare them to find the most specific.
    {
      int nResults = results.size();
      if (nResults > 1) {
        MethodSearchResult[] rarr = new MethodSearchResult[results.size()];
        rarr = results.toArray(rarr);

        // Specificity is a partial ordering so we can't just sort.
        // Instead we compare pairwise.
        // The number of overloads in any class should be small, but this
        // could become expensive for 1000s.
        specificity_loop:
        for (int j = 0; j < nResults; ++j) {
          MethodSearchResult mj = rarr[j];
          for (int i = 0; i < nResults; ++i) {
            MethodSearchResult mi = rarr[i];
            if (i == j || mi == null) { continue; }
            if (mi.isStrictlyMoreSpecificThan(mj)) {
              rarr[j] = null;
              continue specificity_loop;
            }
          }
        }

        results = ImmutableList.copyOf(
           Iterables.filter(Arrays.asList(rarr), Predicates.notNull()));
      }
    }

    if (results.isEmpty()) {
      return Optional.absent();
    }

    if (results.size() != 1) {
      StringBuilder sb = new StringBuilder(
          "Ambiguous invocation of method " + methodName);
      String sep = ": ";
      for (MethodSearchResult r : results) {
        sb.append(sep);
        sep = ", ";

        sb.append('(').append(r.formalTypesInContext).append(") from ")
          .append(r.m.declaringType);
      }
      error(sourceNode, sb.toString());
    }
    return Optional.of(results.get(0));
  }

  static final class MethodSearchResult {
    final ParameterizedMember<CallableInfo> m;
    final ImmutableList<StaticType> formalTypesInContext;
    final StaticType returnTypeInContext;
    final ImmutableList<Cast> actualToFormalCasts;
    final boolean constructsVariadicArray;

    MethodSearchResult(
        ParameterizedMember<CallableInfo> m,
        ImmutableList<StaticType> formalTypesInContext,
        StaticType returnTypeInContext,
        ImmutableList<Cast> actualToFormalCasts,
        boolean constructsVariadicArray) {
      this.m = m;
      this.formalTypesInContext = formalTypesInContext;
      this.returnTypeInContext = returnTypeInContext;
      this.actualToFormalCasts = actualToFormalCasts;
      this.constructsVariadicArray = constructsVariadicArray;
    }

    public boolean isStrictlyMoreSpecificThan(MethodSearchResult that) {
      ImmutableList<StaticType> aTypes = this.formalTypesInContext;
      ImmutableList<StaticType> bTypes = that.formalTypesInContext;

      boolean aIsVariadic = this.m.member.isVariadic();
      boolean bIsVariadic = that.m.member.isVariadic();

      int aArity = aTypes.size();
      int bArity = bTypes.size();

      StaticType aMarginalFormalType = null;
      if (aIsVariadic) {
        // The array type representing a variadic parameter should not be a
        // template variable that extends an array type since the array type
        // is manufactured.
        aMarginalFormalType = ((ArrayType) aTypes.get(aArity - 1)).elementType;
      }
      StaticType bMarginalFormalType = null;
      if (aIsVariadic) {
        // The array type representing a variadic parameter should not be a
        // template variable that extends an array type since the array type
        // is manufactured.
        bMarginalFormalType = ((ArrayType) bTypes.get(bArity - 1)).elementType;
      }

      int nonVarArgsArity = Math.min(
          aArity - (aIsVariadic ? 1 : 0),
          bArity - (bIsVariadic ? 1 : 0));

      boolean oneMoreSpecificThan = false;
      for (int i = 0; i < nonVarArgsArity; ++i) {
        Boolean b = parameterSpecificity(aTypes.get(i), bTypes.get(i));
        if (b == null) {
          continue;
        } else if (b) {
          oneMoreSpecificThan = true;
        } else {
          return false;
        }
      }

      if (!(aIsVariadic || bIsVariadic)) {
        return oneMoreSpecificThan && aArity == bArity;
      }

      // These are the cases for dealing with varargs specificity where
      // a could be more specific than b.

      // B VARIADIC, A has extra non-variadic arguments whether variadic or not
      //   (A0, A1)
      //   (B...)

      //   (A0, A1, A2[])
      //   (B...)

      //   (A0, A1, A2...)
      //   (B...)
      // where A0...n are as or more specific than B

      // We deal with these three cases by running through the extra arguments
      // and then delegating to handlers for the cases below.
      int aIndex = nonVarArgsArity;
      int bIndex = nonVarArgsArity;
      for (int aNonVariadicLimit = aArity - (aIsVariadic ? 1 : 0);
           aIndex < aNonVariadicLimit; ++aIndex) {
        Boolean b = parameterSpecificity(
            aTypes.get(aIndex), bMarginalFormalType);
        if (b == null) {
          // This is the case because the required min arity is now more
          // specific.
          oneMoreSpecificThan = true;
        } else if (b) {
          oneMoreSpecificThan = true;
        } else {
          return false;
        }
      }
      oneMoreSpecificThan = true;

      // A has no more, b accepts more.
      //   ()
      //   (B...)
      if (aIndex == aArity && !aIsVariadic && bIndex + 1 == bArity) {
        return true;
      }

      if (aIndex + 1 == aArity && bIndex + 1 == bArity) {
        Preconditions.checkState(bIsVariadic);  // Checked above.
        // BOTH VARIADIC, SAME COUNT
        //   (A...)
        //   (B...)
        // -> A is more specific than B
        Boolean specificity;
        if (aIsVariadic) {
          specificity = parameterSpecificity(
              aMarginalFormalType, bMarginalFormalType);
        } else {
          // B VARIADIC, A has Array type.
          //   (A[])
          //   (B...)
          // -> A is as or more specific as B
          specificity = parameterSpecificity(
              aTypes.get(aArity - 1), bTypes.get(bArity - 1));
        }
        return specificity != null ? specificity : oneMoreSpecificThan;
      }

      // We can safely return false here because the cases below do not
      // establish specificity.
      //   (A...)
      //   (Object)
      //
      //   (Object...)
      //   (Serializable)
      //
      //   (Object...)
      //   (Cloneable)
      // because in these cases B has a more specific arity than A.
      return false;
    }

    private static Boolean parameterSpecificity(
        StaticType aType, StaticType bType) {
      Cast c = bType.assignableFrom(aType);
      switch (c) {
        case CONFIRM_SAFE:
        case CONFIRM_UNCHECKED:
        case CONVERTING_LOSSLESS:
          return true;
        case CONVERTING_LOSSY:
        case BOX:
        case UNBOX:
        case DISJOINT:
        case CONFIRM_CHECKED:
          return false;
        case SAME:
          return null;
      }
      throw new AssertionError(c);
    }
  }


  private static PrimitiveTypeNode toPrimitiveTypeNode(PrimitiveType typ) {
    if (StaticType.T_BOOLEAN.equals(typ)) {
      return PrimitiveTypeNode.Variant.AnnotationBoolean
          .nodeBuilder()
          .setStaticType(typ)
          .build();
    }

    NumericType nt = (NumericType) typ;

    NodeVariant v = NUMERIC_TYPE_TO_VARIANT.get(nt);
    NumericTypeNode numericTypeNode =
        (nt.isFloaty ? NumericTypeNode.Variant.FloatingPointType
        : NumericTypeNode.Variant.IntegralType)
        .nodeBuilder()
        .setStaticType(typ)
        .add(v.nodeBuilder().build())
        .build();

    return PrimitiveTypeNode.Variant.AnnotationNumericType
        .nodeBuilder()
        .setStaticType(typ)
        .add(numericTypeNode)
        .build();
  }

  private ReferenceTypeNode toReferenceTypeNode(ReferenceType typ) {
    Preconditions.checkArgument(!typePool.T_NULL.equals(typ));
    Preconditions.checkArgument(!StaticType.ERROR_TYPE.equals(typ));
    if (typ instanceof TypePool.ArrayType) {
      TypePool.ArrayType at = (ArrayType) typ;
      StaticType baseElementType = at.baseElementType;
      TypeNode typeNode = toTypeNode(at.baseElementType);

      StaticType ct = baseElementType;
      Preconditions.checkState(at.dimensionality > 0);
      for (int nDims = at.dimensionality; --nDims >= 1;) {
        ct = typePool.type(ct.typeSpecification.arrayOf(), null, logger);
        typeNode = TypeNode.Variant.ReferenceType.nodeBuilder()
            .add(
                ReferenceTypeNode.Variant.ArrayType.nodeBuilder()
                .add(
                    ArrayTypeNode.Variant.TypeAnnotationDim.nodeBuilder()
                    .add(typeNode)
                    .add(DimNode.Variant.LsRs.nodeBuilder().build())
                    .setStaticType(ct)
                    .build())
                .setStaticType(ct)
                .build())
            .build();
      }
      return ReferenceTypeNode.Variant.ArrayType.nodeBuilder()
          .add(
              ArrayTypeNode.Variant.TypeAnnotationDim.nodeBuilder()
              .add(typeNode)
              .add(DimNode.Variant.LsRs.nodeBuilder().build())
              .setStaticType(at)
              .build())
          .setStaticType(at)
          .build();
    } else if (typ instanceof TypePool.ClassOrInterfaceType) {
      TypePool.ClassOrInterfaceType ct = (ClassOrInterfaceType) typ;
      ClassOrInterfaceTypeNode ciNode = toClassOrInterfaceTypeNode(
          ct.typeSpecification.typeName, ct.typeParameterBindings);
      ciNode.setStaticType(typ);
      return ReferenceTypeNode.Variant.ClassOrInterfaceType.nodeBuilder()
          .add(ciNode)
          .setStaticType(typ)
          .build();
    } else {
      throw new AssertionError(typ);
    }
  }

  private ClassOrInterfaceTypeNode toClassOrInterfaceTypeNode(
      Name nm, ImmutableList<TypeBinding> bindings) {
    ClassOrInterfaceTypeNode parent = nm.parent.equals(Name.DEFAULT_PACKAGE)
        ? null
        : toClassOrInterfaceTypeNode(nm.parent, ImmutableList.of());
    IdentifierNode ident = IdentifierNode.Variant.Builtin.nodeBuilder()
        .leaf(nm.identifier)
        .setNamePartType(nm.type)
        .build();
    TypeArgumentsNode arguments = null;
    if (!bindings.isEmpty()) {
      TypeArgumentListNode.Builder typeArgumentList =
          TypeArgumentListNode.Variant.TypeArgumentComTypeArgument
          .nodeBuilder();
      for (TypeBinding b : bindings) {
        ReferenceTypeNode rt = toReferenceTypeNode(
            (ReferenceType) typePool.type(b.typeSpec, null, logger));
        WildcardBoundsNode.Variant boundsVariant = null;
        switch (b.variance) {
          case EXTENDS:
            boundsVariant = WildcardBoundsNode.Variant.ExtendsReferenceType;
            break;
          case INVARIANT:
            break;
          case SUPER:
            boundsVariant = WildcardBoundsNode.Variant.SuperReferenceType;
            break;
        }
        if (boundsVariant == null) {
          typeArgumentList.add(
              TypeArgumentNode.Variant.ReferenceType.nodeBuilder()
              .add(rt)
              .build());
        } else {
          typeArgumentList.add(
              TypeArgumentNode.Variant.Wildcard.nodeBuilder()
              .add(WildcardNode.Variant.AnnotationQmWildcardBounds
                  .nodeBuilder()
                  .add(boundsVariant.nodeBuilder()
                     .add(rt)
                     .build())
                 .build())
            .build());
        }
      }
      arguments = TypeArgumentsNode.Variant.LtTypeArgumentListGt.nodeBuilder()
          .add(typeArgumentList.build())
          .build();
    }
    ClassOrInterfaceTypeNode.Builder b =
        ClassOrInterfaceTypeNode.Variant
        .ClassOrInterfaceTypeDotAnnotationIdentifierTypeArguments.nodeBuilder();
    if (parent != null) {
      b.add(parent);
    }
    b.add(ident);
    if (arguments != null) {
      b.add(arguments);
    }
    return b.build();
  }

  private TypeNode toTypeNode(StaticType typ) {
    if (typ instanceof PrimitiveType) {
      return TypeNode.Variant.PrimitiveType.nodeBuilder()
          .add(toPrimitiveTypeNode((PrimitiveType) typ))
          .build();
    } else {
      Preconditions.checkArgument(typ instanceof ReferenceType);
      return TypeNode.Variant.ReferenceType.nodeBuilder()
          .add(toReferenceTypeNode((ReferenceType) typ))
          .build();
    }
  }
}


enum Compatibility {
  COMPATIBLE_AS_IS,
  IMPLICIT_CAST,
  INCOMPATIBLE,
  ;

  static Compatibility of(Cast c) {
    switch (c) {
      case BOX:
      case CONFIRM_SAFE:
      case CONFIRM_UNCHECKED:
      case CONVERTING_LOSSLESS:
      case UNBOX:
        return IMPLICIT_CAST;
      case SAME:
        return COMPATIBLE_AS_IS;
      case CONFIRM_CHECKED:
      case CONVERTING_LOSSY:
      case DISJOINT:
        return INCOMPATIBLE;
    }
    throw new AssertionError(c);
  }
}
