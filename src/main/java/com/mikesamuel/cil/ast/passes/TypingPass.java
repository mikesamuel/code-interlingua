package com.mikesamuel.cil.ast.passes;

import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import javax.annotation.Nullable;

import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.mikesamuel.cil.ast.AdditiveExpressionNode;
import com.mikesamuel.cil.ast.AdditiveOperatorNode;
import com.mikesamuel.cil.ast.AndExpressionNode;
import com.mikesamuel.cil.ast.ArgumentListNode;
import com.mikesamuel.cil.ast.ArrayCreationExpressionNode;
import com.mikesamuel.cil.ast.ArrayElementTypeNode;
import com.mikesamuel.cil.ast.ArrayInitializerNode;
import com.mikesamuel.cil.ast.ArrayTypeNode;
import com.mikesamuel.cil.ast.AssignmentNode;
import com.mikesamuel.cil.ast.AssignmentOperatorNode;
import com.mikesamuel.cil.ast.BaseInnerNode;
import com.mikesamuel.cil.ast.BaseNode;
import com.mikesamuel.cil.ast.CastExpressionNode;
import com.mikesamuel.cil.ast.CastNode;
import com.mikesamuel.cil.ast.CatchTypeNode;
import com.mikesamuel.cil.ast.ClassLiteralNode;
import com.mikesamuel.cil.ast.ClassOrInterfaceTypeNode;
import com.mikesamuel.cil.ast.ClassOrInterfaceTypeToInstantiateNode;
import com.mikesamuel.cil.ast.ClassTypeNode;
import com.mikesamuel.cil.ast.ConditionalExpressionNode;
import com.mikesamuel.cil.ast.ConfirmCastNode;
import com.mikesamuel.cil.ast.ConvertCastNode;
import com.mikesamuel.cil.ast.DimExprNode;
import com.mikesamuel.cil.ast.DimNode;
import com.mikesamuel.cil.ast.DimsNode;
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
import com.mikesamuel.cil.ast.LastFormalParameterNode;
import com.mikesamuel.cil.ast.LocalNameNode;
import com.mikesamuel.cil.ast.MethodNameNode;
import com.mikesamuel.cil.ast.MultiplicativeExpressionNode;
import com.mikesamuel.cil.ast.MultiplicativeOperatorNode;
import com.mikesamuel.cil.ast.NodeType;
import com.mikesamuel.cil.ast.NodeTypeTables;
import com.mikesamuel.cil.ast.NodeVariant;
import com.mikesamuel.cil.ast.NumericTypeNode;
import com.mikesamuel.cil.ast.PrefixOperatorNode;
import com.mikesamuel.cil.ast.PrimaryNode;
import com.mikesamuel.cil.ast.PrimitiveTypeNode;
import com.mikesamuel.cil.ast.ReferenceTypeNode;
import com.mikesamuel.cil.ast.RelationalExpressionNode;
import com.mikesamuel.cil.ast.ShiftExpressionNode;
import com.mikesamuel.cil.ast.ShiftOperatorNode;
import com.mikesamuel.cil.ast.SingleStaticImportDeclarationNode;
import com.mikesamuel.cil.ast.StaticImportOnDemandDeclarationNode;
import com.mikesamuel.cil.ast.TypeArgumentListNode;
import com.mikesamuel.cil.ast.TypeArgumentNode;
import com.mikesamuel.cil.ast.TypeArgumentsNode;
import com.mikesamuel.cil.ast.TypeNameNode;
import com.mikesamuel.cil.ast.TypeNode;
import com.mikesamuel.cil.ast.UnannTypeNode;
import com.mikesamuel.cil.ast.UnaryExpressionNode;
import com.mikesamuel.cil.ast.UnqualifiedClassInstanceCreationExpressionNode;
import com.mikesamuel.cil.ast.VariableDeclaratorIdNode;
import com.mikesamuel.cil.ast.VariableInitializerNode;
import com.mikesamuel.cil.ast.WildcardBoundsNode;
import com.mikesamuel.cil.ast.WildcardNode;
import com.mikesamuel.cil.ast.meta.CallableInfo;
import com.mikesamuel.cil.ast.meta.ExpressionNameResolver;
import com.mikesamuel.cil.ast.meta.Name;
import com.mikesamuel.cil.ast.meta.ExpressionNameResolver.DeclarationPositionMarker;
import com.mikesamuel.cil.ast.meta.FieldInfo;
import com.mikesamuel.cil.ast.meta.JavaLang;
import com.mikesamuel.cil.ast.meta.MemberInfo;
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
import com.mikesamuel.cil.ast.meta.TypeSpecification.Variance;
import com.mikesamuel.cil.ast.traits.BinaryOp;
import com.mikesamuel.cil.ast.traits.ExpressionNameScope;
import com.mikesamuel.cil.ast.traits.LimitedScopeElement;
import com.mikesamuel.cil.ast.traits.LocalDeclaration;
import com.mikesamuel.cil.ast.traits.MemberDeclaration;
import com.mikesamuel.cil.ast.traits.MethodDescriptorReference;
import com.mikesamuel.cil.ast.traits.TypeDeclaration;
import com.mikesamuel.cil.ast.traits.TypeScope;
import com.mikesamuel.cil.ast.traits.Typed;
import com.mikesamuel.cil.ast.traits.WholeType;
import com.mikesamuel.cil.parser.SList;

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

  private final Multimap<String, TypeInfo> staticImports =
      ArrayListMultimap.create();
  private final List<TypeInfo> staticWildcardImports = Lists.newArrayList();

  private final Map<Name, StaticType> locals = Maps.newLinkedHashMap();

  static final boolean DEBUG = false;

  private static final Predicate<PrimitiveType> IS_INTEGRAL_NUMERIC =
      new Predicate<PrimitiveType>() {
        @Override
        public boolean apply(PrimitiveType pt) {
          return (pt instanceof NumericType) && !((NumericType) pt).isFloaty;
        }
      };

  TypingPass(Logger logger, TypePool typePool, boolean injectCasts) {
    super(logger);
    this.typePool = typePool;
    this.injectCasts = injectCasts;
    this.memberInfoPool = new MemberInfoPool(typePool);
  }


  @Override
  protected ProcessingStatus previsit(
      BaseNode node, @Nullable SList<Parent> pathFromRoot) {

    if (node instanceof SingleStaticImportDeclarationNode) {
      TypeNameNode typ = node.firstChildWithType(TypeNameNode.class);
      TypeInfo ti = typ != null ? typ.getReferencedTypeInfo() : null;

      IdentifierNode memberNameNode = node.firstChildWithType(
          IdentifierNode.class);
      String memberName = memberNameNode != null ? memberNameNode.getValue()
          : null;
      if (ti != null && memberName != null) {
        staticImports.put(memberName, ti);
      }
    } else if (node instanceof StaticImportOnDemandDeclarationNode) {
      TypeNameNode typ = node.firstChildWithType(TypeNameNode.class);
      TypeInfo ti = typ != null ? typ.getReferencedTypeInfo() : null;
      if (ti != null) {
        staticWildcardImports.add(ti);
      }
    }

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

    // Collect local declarations
    if (node instanceof VariableDeclaratorIdNode) {
      VariableDeclaratorIdNode id = (VariableDeclaratorIdNode) node;
      Name name = id.getDeclaredExpressionName();
      if (name != null && name.type == Name.Type.LOCAL) {
        for (SList<Parent> p = pathFromRoot; p != null; p = p.prev) {
          BaseNode anc = p.x.parent;
          if (anc instanceof LocalDeclaration) {
            LocalDeclaration localDecl = (LocalDeclaration) anc;
            BaseNode typ = localDecl.getDeclaredTypeNode();
            if (typ instanceof UnannTypeNode) {
              UnannTypeNode typeNode = (UnannTypeNode) typ;
              StaticType styp = typeNode.getStaticType();
              if (anc.getVariant()
                  == LastFormalParameterNode.Variant.Variadic) {
                styp = typePool.type(
                    styp.typeSpecification.arrayOf(), typ.getSourcePosition(),
                    logger);
              }
              this.locals.put(name, styp);
            } else if (typ != null) {
              CatchTypeNode ctyp = (CatchTypeNode) typ;
              ImmutableList.Builder<ReferenceType> excTypes =
                  ImmutableList.builder();
              boolean isError = false;
              for (BaseNode child : ctyp.getChildren()) {
                if (child instanceof ClassTypeNode) {
                  StaticType childType =
                      ((ClassTypeNode) child).getStaticType();
                  if (childType instanceof ReferenceType) {
                    excTypes.add((ReferenceType) childType);
                  } else {
                    if (!isError && !StaticType.ERROR_TYPE.equals(childType)) {
                      error(
                          ctyp,
                          "Catch type " + childType + " is not a reference type"
                          );
                    }
                    isError = true;
                    break;
                  }
                }
              }
              this.locals.put(
                  name,
                  isError
                  ? StaticType.ERROR_TYPE
                  : typePool.leastUpperBound(excTypes.build()));
            }
            break;
          }
        }
      }
    }

    return ProcessingStatus.CONTINUE;
  }

  @Override
  protected ProcessingStatus postvisit(
      BaseNode node, @Nullable SList<Parent> pathFromRoot) {
    process(node, pathFromRoot);

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

    return ProcessingStatus.CONTINUE;
  }

  private void process(BaseNode node, @Nullable SList<Parent> pathFromRoot) {

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
              JavaLang.JAVA_LANG_STRING, node.getSourcePosition(), logger);
          break type_switch;
        case ExpressionAtom: {
          ExpressionAtomNode e = (ExpressionAtomNode) node;
          switch (e.getVariant()) {
            case ArrayConstructorReference:
            case ConstructorReference:
            case StaticReference:
              exprType = StaticType.ERROR_TYPE;  // Skip LAMBDA
              break type_switch;
            case ArrayCreationExpression:
              exprType = passThru(node);
              break type_switch;
            case ClassLiteral:
              exprType = passThru(node);
              break type_switch;
            case FreeField:
              exprType = processFieldAccess(e);
              break type_switch;
            case Literal:
              exprType = passThru(node);
              break type_switch;
            case Local: {
              LocalNameNode nameNode = e.firstChildWithType(
                  LocalNameNode.class);
              Name exprName = nameNode.getReferencedExpressionName();
              if (exprName == null) {
                error(e, "Ambiguous local " + nameNode.getTextContent("."));
                exprType = StaticType.ERROR_TYPE;
                break type_switch;
              }
              exprType = locals.get(exprName);
              if (exprType == null) {
                error(e, "Missing type for local " + exprName);
                exprType = StaticType.ERROR_TYPE;
              }
              break type_switch;
            }
            case MethodInvocation:
              exprType = processMethodInvocation(e);
              break type_switch;
            case Parenthesized:
              exprType = passThru(e);
              break type_switch;
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
            case Super:
              // TODO: can we just fall through to This or do we need to handle
              // cases like
              // class C { Object f() { ... } }
              // class D extends C {
              //   @Override
              //   String f() { ... }
              //   {
              //     super.f();  // Object or String?
              //   }
              // }
              //$FALL-THROUGH$
              // Maybe we can handle this by skipping the first element in
              // findMembers.
            case This:
              exprType = typePool.type(
                  TypeSpecification.autoScoped(containingTypes.peekLast()),
                  e.getSourcePosition(), logger);
              break type_switch;
            case UnqualifiedClassInstanceCreationExpression: {
              UnqualifiedClassInstanceCreationExpressionNode ctorCall =
                  e.firstChildWithType(
                      UnqualifiedClassInstanceCreationExpressionNode.class);
              Optional<ClassOrInterfaceTypeNode> type = node.finder(
                  ClassOrInterfaceTypeNode.class)
                  .exclude(NodeType.TypeArguments)
                  .exclude(NodeType.ArgumentList)
                  .exclude(NodeType.ClassBody)
                  .findOne();
              if (type.isPresent()) {
                exprType = type.get().getStaticType();
                processCallableInvocation(
                    ctorCall, exprType, "<init>", ctorCall);
              } else {
                error(node, "Class to instantiate unspecified");
                exprType = StaticType.ERROR_TYPE;
              }
              break type_switch;
            }
          }
          throw new AssertionError(e);
        }

        case Primary: {
          PrimaryNode e = (PrimaryNode) node;
          switch (e.getVariant()) {
            case Ambiguous:  // Should not reach here.
            case ExpressionAtom:  // @anon
            case MethodReference:  // Skip LAMBDA
              exprType = StaticType.ERROR_TYPE;
              break type_switch;

            case MethodInvocation: {
              exprType = processMethodInvocation(e);
              break type_switch;
            }

            case ArrayAccess: {
              Operand arr = nthOperandOf(0, e, NodeType.Primary);
              Operand index = nthOperandOf(1, e, NodeType.Expression);
              if (index != null) {
                index.cast(passThru(index), StaticType.T_INT);
              }
              if (arr != null) {
                StaticType arrayType = maybePassThru(arr);
                // Array types can't be used as bounds for type parameters
                // so we can test directly here.
                if (arrayType instanceof ArrayType) {
                  StaticType elementType = ((ArrayType) arrayType).elementType;
                  exprType = elementType;
                  break type_switch;
                }
              }
              error(e, "Cannot find array type for left of array access");
              exprType = StaticType.ERROR_TYPE;
              break type_switch;
            }

            case FieldAccess: {
              exprType = processFieldAccess(e);
              break type_switch;
            }

            case InnerClassCreation: {
              Operand outerInstance = nthOperandOf(
                  0, e, NodeType.Primary);
              StaticType outerType = outerInstance != null
                  ? maybePassThru(outerInstance)
                  : null;

              UnqualifiedClassInstanceCreationExpressionNode instantiation =
                  node.firstChildWithType(
                      UnqualifiedClassInstanceCreationExpressionNode.class);
              ClassOrInterfaceTypeToInstantiateNode typeToInstantiate =
                  instantiation != null
                  ? instantiation.firstChildWithType(
                      ClassOrInterfaceTypeToInstantiateNode.class)
                  : null;
              ClassOrInterfaceTypeNode type = typeToInstantiate != null
                  ? typeToInstantiate.firstChildWithType(
                      ClassOrInterfaceTypeNode.class)
                  : null;
              Name innerName = AmbiguousNames.ambiguousNameOf(type);
              if (outerType != null && innerName != null) {
                TypeSpecification outerSpec = outerType.typeSpecification;
                if (outerSpec.nDims == 0
                    && outerSpec.typeName.type == Name.Type.CLASS) {
                  SList<Name> names = null;
                  for (Name nm = innerName; nm != null; nm = nm.parent) {
                    Preconditions.checkState(nm.type == Name.Type.CLASS);
                    names = SList.append(names, nm);
                  }

                  Name fullInnerTypeName = outerSpec.typeName;
                  for (SList<Name> nm = names; nm != null; nm = nm.prev) {
                    fullInnerTypeName = fullInnerTypeName.child(
                        nm.x.identifier, nm.x.type);
                  }

                  // TODO: grab from ClassOrInterfaceTypeToInstantiate
                  // or infer for diamond.
                  ImmutableList<TypeBinding> bindings = ImmutableList.of();
                  TypeSpecification innerTypeSpec = new TypeSpecification(
                      fullInnerTypeName, bindings);
                  exprType = typePool.type(
                      innerTypeSpec, e.getSourcePosition(), logger);
                  Preconditions.checkNotNull(type).setStaticType(exprType);
                  processCallableInvocation(
                      instantiation, exprType, "<init>", instantiation);
                  break type_switch;
                }
              }
              error(node, "Inner class to instantiate unspecified");
              exprType = StaticType.ERROR_TYPE;
              break type_switch;
            }
          }
          throw new AssertionError(e);
        }

        case ClassLiteral: {
          ClassLiteralNode e = (ClassLiteralNode) node;
          int dimensionality = 0;
          for (BaseNode child : e.getChildren()) {
            if (child instanceof DimNode) {
              ++dimensionality;
            }
          }
          TypeSpecification spec = null;
          switch (e.getVariant()) {
            case BooleanDimDotClass:
              spec = dimensionality == 0
                  ? new TypeSpecification(StaticType.T_BOOLEAN.wrapperType)
                  : StaticType.T_BOOLEAN.typeSpecification
                    .withNDims(dimensionality);
              break;
            case NumericTypeDimDotClass:
              NumericTypeNode nt = e.firstChildWithType(NumericTypeNode.class);
              if (nt != null) {
                StaticType st = nt.getStaticType();
                if (st instanceof NumericType) {
                  spec = new TypeSpecification(
                      ((NumericType) st).wrapperType,
                      dimensionality);
                  NumericType numType = (NumericType) st;
                  spec = dimensionality == 0
                      ? new TypeSpecification(numType.wrapperType)
                      : numType.typeSpecification.withNDims(dimensionality);
                }
              }
              break;
            case TypeNameDimDotClass:
              TypeNameNode tn = e.firstChildWithType(TypeNameNode.class);
              if (tn != null) {
                TypeInfo ti = tn.getReferencedTypeInfo();
                if (ti != null) {
                  ImmutableList.Builder<TypeBinding> bindings =
                      ImmutableList.builder();
                  for (@SuppressWarnings("unused") Name param : ti.parameters) {
                    bindings.add(TypeBinding.WILDCARD);
                  }
                  spec = new TypeSpecification(
                      ti.canonName, bindings.build(), dimensionality);
                }
              }
              break;
            case VoidDotClass:
              Preconditions.checkState(dimensionality == 0);
              spec = JavaLang.JAVA_LANG_VOID;
              break;
          }
          if (spec != null) {
            TypeSpecification classSpec = new TypeSpecification(
                JavaLang.JAVA_LANG_CLASS.typeName,
                ImmutableList.of(new TypeBinding(spec)));
            exprType = typePool.type(
                classSpec, e.getSourcePosition(), logger);
          } else {
            error(e, "Missing class in class literal");
            exprType = StaticType.ERROR_TYPE;
          }
          break type_switch;
        }

        case ArrayCreationExpression: {
          ArrayCreationExpressionNode e = (ArrayCreationExpressionNode) node;

          ArrayElementTypeNode elementTypeNode = e.firstChildWithType(
              ArrayElementTypeNode.class);
          WholeType wholeElementType = elementTypeNode != null
              ? elementTypeNode.firstChildWithType(WholeType.class) : null;
          StaticType elementType = wholeElementType != null
              ? wholeElementType.getStaticType()
              : null;
          if (elementType == null) { elementType = StaticType.ERROR_TYPE; }
          int nExtraDims = 0;
          for (@SuppressWarnings("unused") DimNode dimNode
              : e.finder(DimNode.class)
                .exclude(
                    NodeType.Annotation, NodeType.DimExpr,
                    NodeType.ArrayInitializer, NodeType.ArrayElementType)
                .find()) {
            ++nExtraDims;
          }
          for (@SuppressWarnings("unused") DimExprNode dimExprNode
              : e.finder(DimExprNode.class)
                .exclude(
                    NodeType.Annotation, NodeType.DimExpr,
                    NodeType.ArrayInitializer, NodeType.ArrayElementType)
                .find()) {
            ++nExtraDims;
          }
          Preconditions.checkNotNull(elementType);
          if (StaticType.ERROR_TYPE.equals(elementType)) {
            exprType = StaticType.ERROR_TYPE;
          } else {
            exprType = typePool.type(
                elementType.typeSpecification.withNDims(
                    elementType.typeSpecification.nDims + nExtraDims),
                e.getSourcePosition(), logger);
          }
          break type_switch;
        }

        case Expression:
          exprType = passThru(node);
          break type_switch;

        case Assignment: {
          AssignmentNode e = (AssignmentNode) node;
          Operand left = nthOperandOf(0, e, NodeType.LeftHandSide);
          Operand right = nthOperandOf(1, e, NodeType.Expression);
          AssignmentOperatorNode operator = e.firstChildWithType(
              AssignmentOperatorNode.class);

          if (left == null || right == null || operator == null) {
            error(e, "Missing operand or operator");
            exprType = StaticType.ERROR_TYPE;
            break;
          }

          StaticType leftType = maybePassThru(left);
          StaticType rightType = maybePassThru(right);

          exprType = leftType;

          // If a compound assignment like x += y, rewrite using
          AssignmentOperatorNode.Variant opVariant = operator.getVariant();
          OpVariants effectiveRightHandSideVariants =
              COMPLEX_ASSIGNMENT_OPERATOR_TO_BINARY_OPERATOR_VARIANT.get(
                  opVariant);

          if (effectiveRightHandSideVariants == null) {
            Preconditions.checkState(
                opVariant == AssignmentOperatorNode.Variant.Eq);
            switch (Compatibility.of(leftType.assignableFrom(rightType))) {
              case COMPATIBLE_AS_IS:
                break;
              case IMPLICIT_CAST:
                right.cast(rightType, leftType);
                break;
              case INCOMPATIBLE:
                error(e, "Incompatible types for assignment: "
                    + leftType + " * " + rightType);
                break;
            }

          } else {
            // Insert any casts needed to unbox the right hand side.
            BaseNode leftClone = left.getNode().shallowClone();
            Operand leftExpr = nthOperandOf(0, leftClone, NodeType.Primary);
            BaseNode completeRightHandSideOp =
                effectiveRightHandSideVariants.buildNode(
                        leftExpr.getNode(), right.getNode());
            completeRightHandSideOp.setSourcePosition(node.getSourcePosition());
            process(completeRightHandSideOp, null);
            StaticType completeRightType = maybePassThru(
                completeRightHandSideOp);
            if (StaticType.ERROR_TYPE.equals(completeRightType)) {
              break type_switch;
            }

            BinaryOp completeRightHandSideBinOp =
                (BinaryOp) completeRightHandSideOp;
            BaseNode adjustedLeft =
                completeRightHandSideBinOp.getLeftOperand();
            BaseNode adjustedRight =
                completeRightHandSideBinOp.getRightOperand();

            StaticType adjustedLeftType = maybePassThru(adjustedLeft);
            StaticType adjustedRightType = maybePassThru(adjustedRight);

            if (!rightType.equals(adjustedRightType)) {
              right.cast(rightType, adjustedRightType);
            }

            if (!completeRightType.equals(leftType)
                || !adjustedLeftType.equals(leftType)) {
              warn(
                  left.getNode(),
                  "Cast needed before assigning "
                  + leftType + " * " + rightType
                  + " -> " + completeRightType + " -> " + leftType);
            }
          }
          break type_switch;
        }

        case ConditionalExpression: {

          ConditionalExpressionNode e = (ConditionalExpressionNode) node;
          switch (e.getVariant()) {
            case ConditionalOrExpression:
              throw new AssertionError("@anon");
            case ConditionalOrExpressionQmExpressionClnConditionalExpression:
              Operand condition = nthOperandOf(
                  0, e, NodeType.ConditionalOrExpression);
              Operand thenClause = nthOperandOf(
                  1, e, NodeType.Expression);
              Operand elseClause = nthOperandOf(
                  2, e, NodeType.ConditionalOrExpression);

              if (condition == null
                  || thenClause == null || elseClause == null) {
                exprType = StaticType.ERROR_TYPE;
                break type_switch;
              }

              StaticType conditionType = maybePassThru(condition);
              StaticType thenType = maybePassThru(thenClause);
              StaticType elseType = maybePassThru(elseClause);
              if (conditionType == null
                  || thenType == null || elseType == null) {
                exprType = StaticType.ERROR_TYPE;
                break type_switch;
              }

              boolean bothNull =
                  typePool.T_NULL.equals(thenType)
                  && typePool.T_NULL.equals(elseType);

              conditionType = maybeUnbox(
                  condition, conditionType,
                  Predicates.equalTo(StaticType.T_BOOLEAN));

              // Boolean branch.
              if (!bothNull
                  && Compatibility.INCOMPATIBLE != Compatibility.of(
                      StaticType.T_BOOLEAN.assignableFrom(thenType))
                  && Compatibility.INCOMPATIBLE != Compatibility.of(
                      StaticType.T_BOOLEAN.assignableFrom(elseType))) {
                exprType = StaticType.T_BOOLEAN;
                maybeUnbox(
                    thenClause, thenType,
                    Predicates.equalTo(StaticType.T_BOOLEAN));
                maybeUnbox(
                    elseClause, elseType,
                    Predicates.equalTo(StaticType.T_BOOLEAN));
                break type_switch;
              }

              @Nullable StaticType contextTypeHint =
                  getPolyExprTypeFromContext(pathFromRoot);

              // Numeric type branch
              if (!bothNull
                  && convertibleToNumeric(thenType)
                  && convertibleToNumeric(elseType)) {
                if (contextTypeHint != null) {
                  if (!convertibleToNumeric(contextTypeHint)) {
                    warn(e, "(?:) has numeric operands " + thenType + " * "
                        + elseType + " but used in a non-numeric context: "
                        + contextTypeHint);
                    contextTypeHint = null;
                  } else {
                    contextTypeHint = unboxedType(
                        e, contextTypeHint,
                        Predicates.instanceOf(NumericType.class));
                  }
                }
                thenType = unboxNumericAsNecessary(thenClause, thenType);
                elseType = unboxNumericAsNecessary(elseClause, elseType);
                // TODO: if either argument is a numeric literal, check whether
                // it fits in the other type
                if (contextTypeHint instanceof NumericType) {
                  thenClause.cast(thenType, contextTypeHint);
                  elseClause.cast(elseType, contextTypeHint);
                  exprType = contextTypeHint;
                } else {
                  exprType = promoteNumericBinary(
                      thenClause, thenType, elseClause, elseType, false);
                }
                break type_switch;
              }

              // Reference type branch
              if (contextTypeHint != null) {
                contextTypeHint = boxedType(e, contextTypeHint);
                if (StaticType.ERROR_TYPE.equals(contextTypeHint)) {
                  contextTypeHint = null;
                }
              }
              if (contextTypeHint != null) {
                exprType = contextTypeHint;
              } else if (typePool.T_NULL.equals(thenType)) {
                exprType = elseType;
              } else if (typePool.T_NULL.equals(elseType)) {
                exprType = thenType;
              } else {
                thenType = boxedType(thenClause.getNode(), thenType);
                elseType = boxedType(elseClause.getNode(), elseType);
                if (StaticType.ERROR_TYPE.equals(elseType)
                    || StaticType.ERROR_TYPE.equals(thenType)) {
                  exprType = StaticType.ERROR_TYPE;
                } else if (thenType instanceof ReferenceType
                           && elseType instanceof ReferenceType) {
                  exprType = typePool.leastUpperBound(ImmutableList.of(
                      (ReferenceType) thenType,
                      (ReferenceType) elseType));
                } else {
                  exprType = StaticType.ERROR_TYPE;
                }
              }
              break type_switch;

            case ConditionalOrExpressionQmExpressionClnLambdaExpression:
              exprType = StaticType.ERROR_TYPE;  // Lambda
              break type_switch;
          }
          throw new AssertionError(e);
        }

        case ConditionalOrExpression:
        case ConditionalAndExpression: {
          // The pass thru kind are all @anon so we only have to deal with the
          // operation case.
          exprType = StaticType.T_BOOLEAN;
          Operand left = nthOperandOf(0, node, node.getNodeType());
          Operand right = nthOperandOf(
              1, node,
              node.getNodeType() == NodeType.ConditionalOrExpression
              ? NodeType.ConditionalAndExpression
              : NodeType.InclusiveOrExpression);
          if (left != null && right != null) {
            StaticType leftType = maybePassThru(left);
            StaticType rightType = maybePassThru(right);
            if (leftType != null && rightType != null) {
              Predicate<Object> isBoolean = Predicates.equalTo(
                  StaticType.T_BOOLEAN);
              leftType = maybeUnbox(left, leftType, isBoolean);
              rightType = maybeUnbox(right, rightType, isBoolean);
              break type_switch;
            }
          }
          error(node, "Missing operand or type info");
          break type_switch;
        }

        case RelationalExpression: {
          exprType = StaticType.T_BOOLEAN;
          RelationalExpressionNode e = (RelationalExpressionNode) node;
          switch (e.getVariant()) {
            case RelationalExpressionInstanceofReferenceType: {
              Operand left = nthOperandOf(0, e, NodeType.RelationalExpression);
              StaticType leftType = left != null ? maybePassThru(left) : null;
              WholeType right = e.firstChildWithType(WholeType.class);
              StaticType rightType = right != null
                  ? right.getStaticType()
                  : null;
              if (left != null && leftType != null
                  && !StaticType.ERROR_TYPE.equals(leftType)
                  && right != null && rightType != null
                  && !StaticType.ERROR_TYPE.equals(rightType)) {
                switch (leftType.assignableFrom(rightType)) {
                  case BOX:
                  case UNBOX:
                  case CONVERTING_LOSSLESS:
                  case CONVERTING_LOSSY:
                  case DISJOINT:
                    error(
                        left.getNode(),
                        "instanceof not applicable to " + leftType);
                    break type_switch;
                  case CONFIRM_CHECKED:
                  case CONFIRM_SAFE:
                  case CONFIRM_UNCHECKED:
                  case SAME:
                    break type_switch;
                }
              } else {
                error(e, "Missing operand or type info");
              }
              break type_switch;
            }
            case RelationalExpressionRelationalOperatorShiftExpression:
              processNumericBinary(e);
              break type_switch;
            case ShiftExpression:
              throw new IllegalArgumentException("@anon");
          }
          throw new AssertionError(e);
        }

        case EqualityExpression: {
          EqualityExpressionNode e = (EqualityExpressionNode) node;
          exprType = StaticType.T_BOOLEAN;
          // no operand boxing, but check that the types are not disjoint.
          Operand left = nthOperandOf(0, e, NodeType.EqualityExpression);
          Operand right = nthOperandOf(1, e, NodeType.RelationalExpression);
          if (left != null && right != null) {
            StaticType leftType = maybePassThru(left);
            StaticType rightType = maybePassThru(right);
            if (leftType != null && rightType != null) {
              if (leftType instanceof PrimitiveType
                  || rightType instanceof PrimitiveType) {
                leftType = unboxAsNecessary(left, leftType);
                rightType = unboxAsNecessary(right, rightType);
                if (!(leftType instanceof PrimitiveType
                      && rightType instanceof PrimitiveType)) {
                  // Failure to unbox already reported.
                  break type_switch;
                }
                int numericCount =
                    (leftType instanceof NumericType ? 1 : 0)
                    + (rightType instanceof NumericType ? 1 : 0);
                if (numericCount == 2) {
                  promoteNumericBinary(left, leftType, right, rightType);
                } else if (!leftType.equals(rightType)) {
                  error(
                      e,
                      "Incompatible types for comparison "
                      + leftType + " * " + rightType);
                }
                break type_switch;
              }
              Cast c = leftType.assignableFrom(rightType);
              switch (c) {
                case BOX:
                case CONVERTING_LOSSLESS:
                case CONVERTING_LOSSY:
                case DISJOINT:
                case UNBOX:
                  error(
                      e,
                      "Incompatible types for comparison "
                          + leftType + " * " + rightType);
                  break type_switch;
                case CONFIRM_CHECKED:
                case CONFIRM_SAFE:
                case CONFIRM_UNCHECKED:
                case SAME:
                  // OK
                  break type_switch;
              }
              throw new AssertionError(c);
            }
          }
          error(e, "Missing operand or type info");
          break type_switch;
        }

        case InclusiveOrExpression:
        case ExclusiveOrExpression:
        case AndExpression: {
          NodeType rightNodeType;
          switch (node.getNodeType()) {
            case InclusiveOrExpression:
              rightNodeType = NodeType.ExclusiveOrExpression;
              break;
            case ExclusiveOrExpression:
              rightNodeType = NodeType.AndExpression;
              break;
            case AndExpression:
              rightNodeType = NodeType.EqualityExpression;
              break;
            default: throw new AssertionError(node);
          }
          Operand left = nthOperandOf(0, node, node.getNodeType());
          Operand right = nthOperandOf(1, node, rightNodeType);
          if (left != null && right != null) {
            // If the left is boolean, the right is boolean.
            StaticType leftType = maybePassThru(left);
            switch (Compatibility.of(
                StaticType.T_BOOLEAN.assignableFrom(leftType))) {
              case IMPLICIT_CAST:
                left.cast(leftType, StaticType.T_BOOLEAN);
                //$FALL-THROUGH$
              case COMPATIBLE_AS_IS:
                exprType = StaticType.T_BOOLEAN;
                right.cast(maybePassThru(right), StaticType.T_BOOLEAN);
                break type_switch;
              case INCOMPATIBLE:
                break;
            }
            // Otherwise it should be an integral type.
            StaticType rightType = maybePassThru(right);
            leftType = maybeUnbox(left, leftType, IS_INTEGRAL_NUMERIC);
            if (!StaticType.ERROR_TYPE.equals(leftType)) {
              rightType = maybeUnbox(right, rightType, IS_INTEGRAL_NUMERIC);
              if (!StaticType.ERROR_TYPE.equals(rightType)) {
                exprType = promoteNumericBinary(
                    left, leftType, right, rightType);
                break type_switch;
              }
            }
          } else {
            error(node, "Missing operand");
          }
          exprType = StaticType.ERROR_TYPE;
          break type_switch;
        }

        case ShiftExpression: {
          // docs.oracle.com/javase/specs/jls/se8/html/jls-15.html#jls-15.19
          // Shift expressions do not do binary numeric promotion.
          // The bit field and the shift amount are promoted independently
          // and are then required to be integral.
          Operand left = nthOperandOf(0, node, NodeType.ShiftExpression);
          Operand right = nthOperandOf(1, node, NodeType.AdditiveExpression);
          exprType = null;
          if (left != null && right != null) {
            StaticType leftType = maybePassThru(left);
            StaticType rightType = maybePassThru(right);
            leftType = unboxNumericAsNecessary(left, leftType);
            rightType = unboxNumericAsNecessary(right, rightType);
            if (leftType instanceof NumericType
                && rightType instanceof NumericType) {
              leftType = promoteNumericUnary(left, leftType);
              rightType = promoteNumericUnary(right, rightType);
              if (leftType instanceof NumericType
                  && rightType instanceof NumericType) {
                if (leftType instanceof NumericType
                    && !((NumericType) leftType).isFloaty
                    && rightType instanceof NumericType
                    && !((NumericType) rightType).isFloaty) {
                  exprType = leftType;
                } else {
                  error(
                      node,
                      "Expected integral shift operands not "
                      + leftType + " * " + rightType);
                }
              }
            }
          }
          if (exprType == null) {
            exprType = StaticType.ERROR_TYPE;
          }
          break type_switch;
        }

        case AdditiveExpression: {
          AdditiveExpressionNode e = (AdditiveExpressionNode) node;
          Preconditions.checkState(
              e.getVariant() ==
              AdditiveExpressionNode.Variant.
              AdditiveExpressionAdditiveOperatorMultiplicativeExpression);
          Operand left = nthOperandOf(
              0, e, NodeType.AdditiveExpression);
          Operand right = nthOperandOf(
              1, e, NodeType.MultiplicativeExpression);
          if (left == null || right == null) {
            error(e, "Missing operand");
            exprType = StaticType.ERROR_TYPE;
            break type_switch;
          }
          AdditiveOperatorNode operator = e.firstChildWithType(
              AdditiveOperatorNode.class);
          // JLS S15.18 says
          // If the type of either operand of a + operator is String, then the
          // operation is string concatenation.
          // Otherwise, the type of each of the operands of the + operator must
          // be a type that is convertible (S5.1.8) to a primitive numeric
          // type, or a compile-time error occurs.
          StaticType leftType = maybePassThru(left);
          StaticType rightType = maybePassThru(right);
          StaticType stringType = typePool.type(
              JavaLang.JAVA_LANG_STRING, null, logger);
          boolean isStringConcat = operator != null
              && operator.getVariant() == AdditiveOperatorNode.Variant.Pls
              && (
                  leftType != null
                      && (Compatibility.of(stringType.assignableFrom(leftType))
                          != Compatibility.INCOMPATIBLE)
                  || rightType != null
                      && (Compatibility.of(stringType.assignableFrom(rightType))
                          != Compatibility.INCOMPATIBLE));
          if (isStringConcat) {
            exprType = stringType;
          } else {
            exprType = promoteNumericBinary(left, leftType, right, rightType);
          }
          break type_switch;
        }

        case MultiplicativeExpression: {
          exprType = processNumericBinary(node);
          break type_switch;
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
              PrefixOperatorNode operator = e.firstChildWithType(
                  PrefixOperatorNode.class);
              Operand operand = nthOperandOf(
                  0, e, NodeType.UnaryExpression);
              if (operand == null || operator == null) {
                exprType = StaticType.ERROR_TYPE;
                error(e,
                      operand == null
                      ? "Missing operand" : "Missing operator");
                break type_switch;
              } else {
                switch (operator.getVariant()) {
                  case Bng:
                    exprType = StaticType.T_BOOLEAN;
                    StaticType operandType = unboxAsNecessary(
                        operand, passThru(operand));
                    if (!StaticType.T_BOOLEAN.equals(operandType)
                        && !StaticType.ERROR_TYPE.equals(operandType)) {
                      error(operand.getNode(),
                            "Expected boolean not " + operandType);
                    }
                    break type_switch;
                  case Dsh:
                  case Pls:
                  case Tld:  // TODO: check integral, and promote to int.
                    exprType = promoteNumericUnary(
                        operand, passThru(operand));
                    break type_switch;
                }
              }
              break;
          }
          throw new AssertionError(e);
        }

        case PreExpression:
        case PostExpression: {
          Operand op = nthOperandOf(0, node, NodeType.LeftHandSideExpression);
          if (op == null) {
            error(node, "Missing operand");
            exprType = StaticType.ERROR_TYPE;
            break type_switch;
          }
          StaticType opType = maybePassThru(op);
          opType = maybeUnbox(
              op, opType, Predicates.instanceOf(NumericType.class));
          exprType = opType;
          break type_switch;
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

        case ArrayInitializer: {
          // Look rootwards for the type.
          StaticType typeHint = getPolyExprTypeFromContext(pathFromRoot);
          exprType = typeHint != null ? typeHint : StaticType.ERROR_TYPE;
          break type_switch;
        }

        default:
          throw new AssertionError(t);
      }
      if (DEBUG) {
        System.err.println("Got " + exprType + " for " + node.getNodeType());
      }
      t.setStaticType(exprType);
      return;
    }

    if (node instanceof VariableInitializerNode) {
      Operand op = nthOperandOf(0, node, NodeType.Expression);
      if (op == null) { return; }
      StaticType exprType = ((Typed) op.getNode()).getStaticType();
      if (exprType == null) { return; }

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
                      node,
                      "Cannot use expression of type " + exprType
                      + " to initialize declaration of type "
                      + declarationType);
              }
            } else {
              error(
                  node,
                  "array initializer element assigned to non-array type");
            }
            break;
          default:
            break;
        }
        break;
      }
    }
  }

  private StaticType processFieldAccess(BaseNode e) {
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

    ImmutableList<ParameterizedMember<FieldInfo>> fields = findMembers(
        fieldName,
        containerType,
        new Function<
            TypeSpecification,
            Optional<ImmutableList<ParameterizedMember<FieldInfo>>>>() {

          @Override
          public
          Optional<ImmutableList<ParameterizedMember<FieldInfo>>> apply(
              TypeSpecification oneContainer) {
            ImmutableList<ParameterizedMember<FieldInfo>> fieldsFound =
                memberInfoPool.getMembers(
                    FieldInfo.class,
                    fieldName,
                    sourceType,
                    oneContainer);
            if (!fieldsFound.isEmpty()) {
              // Implement name shadowing by taking the first that has any
              // accessible with the right name.
              return Optional.of(fieldsFound);
            }
            return Optional.absent();
          }
        });
        ImmutableList.of();

    if (fields.isEmpty()) {
      if (containerType instanceof ArrayType && "length".equals(fieldName)) {
        return StaticType.T_INT;
      }
      error(
          e, "Reference to undefined or inaccessible field named " + fieldName);
      return StaticType.ERROR_TYPE;
    }

    ParameterizedMember<FieldInfo> f = fields.get(0);
    TypeSpecification typeInDeclaringClass = f.member.getValueType();
    Preconditions.checkNotNull(nameNode)
        .setReferencedExpressionName(f.member.canonName);

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
      valueTypeInContext = typeInDeclaringClass.subst(
          Functions.forMap(substMap, null));
    }

    return typePool.type(valueTypeInContext, e.getSourcePosition(), logger);
  }

  private StaticType processMethodInvocation(BaseNode e) {
    Typed callee = e.firstChildWithType(Typed.class);

    MethodNameNode nameNode = e.firstChildWithType(
        MethodNameNode.class);
    IdentifierNode nameIdent = nameNode != null
        ? nameNode.firstChildWithType(IdentifierNode.class) : null;
    String name = nameIdent != null ? nameIdent.getValue() : null;
    if (name == null) {
      error(e, "Cannot determine name of method invoked");
      return StaticType.ERROR_TYPE;
    }

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

    return processCallableInvocation(e, calleeType, name, nameNode);
  }

  private StaticType processCallableInvocation(
      BaseNode e, StaticType calleeType, String name,
      MethodDescriptorReference descriptorRef) {
    @Nullable TypeArgumentsNode args = e.firstChildWithType(
        TypeArgumentsNode.class);

    @Nullable Operand actualsOp = firstWithType(e, NodeType.ArgumentList);
    @Nullable ArgumentListNode actuals = actualsOp != null
        ? (ArgumentListNode) actualsOp.getNode() : null;

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
      TypeArgumentListNode argListNode = args.firstChildWithType(
          TypeArgumentListNode.class);
      if (argListNode != null) {
        for (TypeArgumentNode arg
            : argListNode.finder(TypeArgumentNode.class)
            .exclude(NodeType.TypeArguments)
            .find()) {
          TypeBinding b = AmbiguousNames.typeBindingOf(
              arg, canonResolver, logger);
          typeArguments.add(b);
        }
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
    descriptorRef.setMethodDescriptor(invokedMethod.m.member.getDescriptor());
    descriptorRef.setMethodDeclaringType(invokedMethod.m.declaringType);

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
        ArgumentListNode newActuals = actuals.shallowClone();
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

        actualsOp.parent.replace(actualsOp.indexInParent, newActuals);
      }
    }
    return invokedMethod.returnTypeInContext;
  }

  private StaticType passThru(Operand op) {
    return passThru(op.getNode());
  }

  private StaticType maybePassThru(Operand op) {
    return maybePassThru(op.getNode());
  }

  private StaticType maybePassThru(BaseNode node) {
    if (node instanceof Typed) {
      StaticType t = ((Typed) node).getStaticType();
      if (t == null) {
        error(node, "Untyped " + node.getVariant());
        return StaticType.ERROR_TYPE;
      }
      return t;
    }
    return passThru(node);
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
          error(child, "Untyped " + child.getVariant());
          return StaticType.ERROR_TYPE;
        }
        return t;
      }
      return passThru(child);
    }
    error(node, "Untyped " + node.getVariant());
    return StaticType.ERROR_TYPE;
  }

  private StaticType unboxNumericAsNecessary(Operand op, StaticType t) {
    return maybeUnbox(op, t, Predicates.instanceOf(NumericType.class));
  }

  private StaticType unboxAsNecessary(Operand op, StaticType t) {
    return maybeUnbox(
        op, t,
        new Predicate<PrimitiveType>() {

          @Override
          public boolean apply(PrimitiveType pt) {
            return pt != null && !StaticType.T_VOID.equals(pt);
          }

        });
  }

  private StaticType maybeUnbox(
      Operand op, StaticType t, Predicate<? super PrimitiveType> ok) {
    StaticType unboxed = unboxedType(op.getNode(), t, ok);
    if (!StaticType.ERROR_TYPE.equals(unboxed)) {
      op.cast(t, unboxed);
    }
    return unboxed;
  }

  private StaticType unboxedType(
      BaseNode context, StaticType t, Predicate<? super PrimitiveType> ok) {
    if (t instanceof PrimitiveType && ok.apply((PrimitiveType) t)) {
      return t;
    } else if (t instanceof TypePool.ClassOrInterfaceType) {
      TypePool.ClassOrInterfaceType ct = (TypePool.ClassOrInterfaceType) t;
      PrimitiveType pt = TO_WRAPPED.get(ct.info.canonName);
      if (pt != null && ok.apply(pt)) {
        return pt;
      }
      error(context, "Cannot unbox " + t);
    } else {
      error(context, "Invalid operand of type " + t);
    }
    return StaticType.ERROR_TYPE;
  }

  private StaticType boxedType(BaseNode context, StaticType t) {
    if (StaticType.ERROR_TYPE.equals(t)) {
      return t;
    }
    if (StaticType.T_VOID.equals(t)) {
      error(context, "void where reference type expected");
      return StaticType.ERROR_TYPE;
    }
    if (t instanceof PrimitiveType) {
      return typePool.type(
          new TypeSpecification(
              Preconditions.checkNotNull(((PrimitiveType) t).wrapperType)),
          context.getSourcePosition(), logger);
    }
    return t;
  }

  private boolean convertibleToNumeric(StaticType t) {
    if (t instanceof NumericType) {
      return true;
    }
    if (typePool.T_NULL.equals(t)) {
      return true;  // statically, though fails at runtime.
    }
    if (t instanceof TypePool.ClassOrInterfaceType) {
      TypePool.ClassOrInterfaceType ct = (TypePool.ClassOrInterfaceType) t;
      PrimitiveType pt = TO_WRAPPED.get(ct.info.canonName);
      return pt instanceof NumericType;
    }
    return false;
  }

  private @Nullable StaticType getPolyExprTypeFromContext(
      SList<Parent> pathFromRoot) {
    SList<Parent> anc;
    for (anc = pathFromRoot; anc != null; anc = anc.prev) {
      NodeVariant v = anc.x.parent.getVariant();
      if (v.getDelegate() != null) {
        continue;
      }
      switch (v.getNodeType()) {
        case VariableDeclarator:
        case VariableDeclaratorList:
        case VariableInitializerList:
          continue;
        default:
          break;
      }
      break;
    }

    if (anc == null) {
      return null;
    }

    BaseNode node = anc.x.parent;
    if (node instanceof LocalDeclaration) {
      WholeType typeNode = (WholeType)
          ((LocalDeclaration) node).getDeclaredTypeNode();
      StaticType declaredType = typeNode != null
          ? typeNode.getStaticType() : null;
      if (declaredType == null) {
        error(node, "Missing type info for declaration");
        return StaticType.ERROR_TYPE;
      }
      return declaredType;
    } else if (node instanceof MemberDeclaration) {
      MemberDeclaration decl = (MemberDeclaration) node;
      MemberInfo info = decl.getMemberInfo();
      if (info instanceof FieldInfo) {
        return typePool.type(
            ((FieldInfo) info).getValueType(),
            decl.getSourcePosition(), logger);
      }
    } else if (node instanceof ArrayCreationExpressionNode) {
      ArrayCreationExpressionNode newArr = (ArrayCreationExpressionNode) node;
      ArrayElementTypeNode elementTypeNode =
          newArr.firstChildWithType(ArrayElementTypeNode.class);
      WholeType elementTypeNodeWholeType = elementTypeNode != null
          ? elementTypeNode.firstChildWithType(WholeType.class)
          : null;
      StaticType elementType = elementTypeNodeWholeType != null
          ? elementTypeNodeWholeType.getStaticType()
          : null;
      DimsNode dimsNode = newArr.firstChildWithType(DimsNode.class);
      if (elementType == null) {
        error(newArr, "Missing element type for new array");
        return StaticType.ERROR_TYPE;
      }
      if (dimsNode == null) {
        error(newArr, "Missing dimension count for new array");
        return StaticType.ERROR_TYPE;
      }
      int nDims = dimsNode
          .finder(DimNode.class)
          .exclude(NodeType.Annotation)
          .find().size();
      return typePool.type(
          elementType.typeSpecification.withNDims(
              elementType.typeSpecification.nDims + nDims),
          null,
          null);
    } else if (node instanceof ArgumentListNode) {
      // Look at applicable callees and infer from argument type.
      // TODO
      return null;
    } else if (node instanceof ArrayInitializerNode) {
      StaticType arrayType = getPolyExprTypeFromContext(anc.prev);
      if (arrayType == null) {
        error(node, "Missing type for array initializer");
        return StaticType.ERROR_TYPE;
      } else if (StaticType.ERROR_TYPE.equals(arrayType)) {
        return StaticType.ERROR_TYPE;
      } else if (arrayType instanceof ArrayType) {
        return ((ArrayType) arrayType).elementType;
      } else {
        error(
            node,
            "Cannot initialize declaration with type " + arrayType
            + " with array");
        return StaticType.ERROR_TYPE;
      }
    }
    return null;  // Don't know.
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


  private static final ImmutableMap<AssignmentOperatorNode.Variant, OpVariants>
      COMPLEX_ASSIGNMENT_OPERATOR_TO_BINARY_OPERATOR_VARIANT;

  private static final class OpVariants {
    final NodeVariant operationVariant;
    final @Nullable NodeVariant operatorVariant;

    OpVariants(
        NodeVariant operationVariant,
        @Nullable NodeVariant operatorVariant) {
      this.operationVariant = operationVariant;
      this.operatorVariant = operatorVariant;
    }

    BaseNode buildNode(BaseNode leftOperand, BaseNode rightOperand) {
      ImmutableList.Builder<BaseNode> children = ImmutableList.builder();
      children.add(leftOperand);
      if (operatorVariant != null) {
        children.add(operatorVariant.buildNode(ImmutableList.of()));
      }
      children.add(rightOperand);
      return operationVariant.buildNode(children.build());
    }
  }

  static {
    EnumMap<AssignmentOperatorNode.Variant, OpVariants> m = new EnumMap<>(
        AssignmentOperatorNode.Variant.class);

    // TODO: It would be nice to be able to infer this by reflecting over the
    // ptree.
    m.put(AssignmentOperatorNode.Variant.AmpEq,
        new OpVariants(
            AndExpressionNode.Variant.AndExpressionAmpEqualityExpression,
            null));
    m.put(AssignmentOperatorNode.Variant.DshEq,
        new OpVariants(
            AdditiveExpressionNode.Variant
            .AdditiveExpressionAdditiveOperatorMultiplicativeExpression,
            AdditiveOperatorNode.Variant.Dsh));
    m.put(AssignmentOperatorNode.Variant.PlsEq,
        new OpVariants(
            AdditiveExpressionNode.Variant
            .AdditiveExpressionAdditiveOperatorMultiplicativeExpression,
            AdditiveOperatorNode.Variant.Pls));
    m.put(AssignmentOperatorNode.Variant.FwdEq,
        new OpVariants(
            MultiplicativeExpressionNode.Variant
            .MultiplicativeExpressionMultiplicativeOperatorUnaryExpression,
            MultiplicativeOperatorNode.Variant.Fwd));
    m.put(AssignmentOperatorNode.Variant.PctEq,
        new OpVariants(
            MultiplicativeExpressionNode.Variant
            .MultiplicativeExpressionMultiplicativeOperatorUnaryExpression,
            MultiplicativeOperatorNode.Variant.Pct));
    m.put(AssignmentOperatorNode.Variant.StrEq,
        new OpVariants(
            MultiplicativeExpressionNode.Variant
            .MultiplicativeExpressionMultiplicativeOperatorUnaryExpression,
            MultiplicativeOperatorNode.Variant.Str));
    m.put(AssignmentOperatorNode.Variant.Gt2Eq,
        new OpVariants(
            ShiftExpressionNode.Variant
            .ShiftExpressionShiftOperatorAdditiveExpression,
            ShiftOperatorNode.Variant.Gt2));
    m.put(AssignmentOperatorNode.Variant.Gt3Eq,
        new OpVariants(
            ShiftExpressionNode.Variant
            .ShiftExpressionShiftOperatorAdditiveExpression,
            ShiftOperatorNode.Variant.Gt3));
    m.put(AssignmentOperatorNode.Variant.Lt2Eq,
        new OpVariants(
            ShiftExpressionNode.Variant
            .ShiftExpressionShiftOperatorAdditiveExpression,
            ShiftOperatorNode.Variant.Lt2));
    m.put(AssignmentOperatorNode.Variant.HatEq,
        new OpVariants(
            ExclusiveOrExpressionNode.Variant
            .ExclusiveOrExpressionHatAndExpression,
            null));
    m.put(AssignmentOperatorNode.Variant.PipEq,
        new OpVariants(
            InclusiveOrExpressionNode.Variant
            .InclusiveOrExpressionPipExclusiveOrExpression,
            null));

    COMPLEX_ASSIGNMENT_OPERATOR_TO_BINARY_OPERATOR_VARIANT =
        Maps.immutableEnumMap(m);
  }


  final class Operand {
    final BaseInnerNode parent;
    final int indexInParent;
    /**
     * An ancestor type for the operand which may not be exactly the same due
     * to {@link NodeVariant#isAnon()}.
     */
    final NodeType containerType;

    Operand(BaseInnerNode parent, int indexInParent, NodeType containerType) {
      this.parent = parent;
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
      if (sourceType.equals(targetType)) {
        return;
      }
      if (StaticType.ERROR_TYPE.equals(targetType)
          || StaticType.ERROR_TYPE.equals(sourceType)) {
        return;
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
        @SuppressWarnings("synthetic-access")
        PrimitiveTypeNode targetTypeNode = toPrimitiveTypeNode(
            (PrimitiveType) targetType);
        cast = CastNode.Variant.ConvertCast.buildNode(
            ConvertCastNode.Variant.PrimitiveType.buildNode(targetTypeNode));
      } else {
        // TODO: handle +/- unary op ambiguity.
        // Maybe, if it's not an ExpressionAtom.Parenthesized, then
        // wrap it.  This may already necessarily happen due to the
        // Intermediates call below, but it's non-obvious and a maintenance
        // hazard as-is.
        @SuppressWarnings("synthetic-access")
        ReferenceTypeNode targetTypeNode = toReferenceTypeNode(
            (ReferenceType) targetType);
        cast = CastNode.Variant.ConfirmCast.buildNode(
            ConfirmCastNode.Variant.ReferenceTypeAdditionalBound.buildNode(
                targetTypeNode));
      }
      CastExpressionNode castExpr = CastExpressionNode.Variant.Expression
          .buildNode(cast, toCast);

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
      castExpr.setStaticType(targetType);

      if (wrappedCast.isPresent()) {
        parent.replace(indexInParent, wrappedCast.get());
      } else {
        error(toCast, "Cannot cast to " + targetType);
      }
    }

    BaseNode getNode() {
      return parent.getChild(indexInParent);
    }
  }

  private Operand nthOperandOf(
      int n, BaseNode parent, NodeType containerType) {
    if (parent instanceof BaseInnerNode) {
      BaseInnerNode inode = (BaseInnerNode) parent;

      int nLeft = n;
      int nChildren = inode.getNChildren();
      for (int i = 0; i < nChildren; ++i) {
        BaseNode child = inode.getChild(i);
        NodeType childNodeType = child.getNodeType();
        if (NodeTypeTables.OPERATOR.contains(childNodeType)) {
          continue;
        }
        if (nLeft == 0) {
          return new Operand(inode, i, containerType);
        }
        --nLeft;
      }
    }
    return null;
  }

  private Operand firstWithType(BaseNode node, NodeType t) {
      if (!(node instanceof BaseInnerNode)) {
        return null;
      }
      BaseInnerNode inode = (BaseInnerNode) node;
      for (int i = 0, n = inode.getNChildren(); i < n; ++i) {
        BaseNode child = inode.getChild(i);
        if (child.getNodeType() == t) {
          return new Operand(inode, i, t);
        }
      }
      return null;
    }

  private static final ImmutableSet<StaticType> PROMOTE_TO_INT =
      ImmutableSet.of(
          StaticType.T_BYTE, StaticType.T_CHAR, StaticType.T_SHORT);

  private StaticType processNumericBinary(BaseNode operation) {
    Operand left = nthOperandOf(
        0, operation, NodeType.AdditiveExpression);
    Operand right = nthOperandOf(
        1, operation, NodeType.MultiplicativeExpression);
    if (left == null || right == null) {
      error(operation, "Missing operand");
      return StaticType.ERROR_TYPE;
    } else {
      return promoteNumericBinary(
          left, maybePassThru(left), right, maybePassThru(right));
    }
  }

  private StaticType promoteNumericBinary(
      Operand left, StaticType leftType, Operand right, StaticType rightType) {
    return promoteNumericBinary(left, leftType, right, rightType, true);
  }

  private StaticType promoteNumericBinary(
      Operand left, StaticType leftType, Operand right, StaticType rightType,
      boolean promoteToInt) {
    StaticType lt = unboxNumericAsNecessary(left, leftType);
    if (StaticType.ERROR_TYPE.equals(lt)) {
      return StaticType.ERROR_TYPE;
    }
    StaticType rt = unboxNumericAsNecessary(right, rightType);
    if (StaticType.ERROR_TYPE.equals(rt)) {
      return StaticType.ERROR_TYPE;
    }
    Cast c = lt.assignableFrom(rt);
    switch (c) {
      case BOX:
      case CONFIRM_CHECKED:
      case CONFIRM_SAFE:
      case CONFIRM_UNCHECKED:
      case UNBOX:
        throw new AssertionError("Should already be unboxed");
      case CONVERTING_LOSSLESS:
        // left is wider.
        if (promoteToInt && PROMOTE_TO_INT.contains(lt)) {
          left.cast(lt, StaticType.T_INT);
          lt = StaticType.T_INT;
        }
        right.cast(rt, lt);
        return requireNumeric(left, lt);
      case CONVERTING_LOSSY:
        // right is wider
        if (promoteToInt && PROMOTE_TO_INT.contains(rt)) {
          right.cast(rt, StaticType.T_INT);
          rt = StaticType.T_INT;
        }
        left.cast(lt, rt);
        return requireNumeric(right, rt);
      case DISJOINT:
        error(
            left.getNode(),
            "Cannot reconcile operand types " + left + " and " + right);
        return StaticType.ERROR_TYPE;
      case SAME:
        if (promoteToInt && PROMOTE_TO_INT.contains(lt)) {
          left.cast(lt, StaticType.T_INT);
          right.cast(rt, StaticType.T_INT);
          return StaticType.T_INT;
        }
        return requireNumeric(left, lt);
    }
    throw new AssertionError(c);
  }

  private StaticType promoteNumericUnary(Operand op, StaticType type) {
    StaticType ut = unboxNumericAsNecessary(op, type);
    // left is wider.
    if (PROMOTE_TO_INT.contains(ut)) {
      op.cast(ut, StaticType.T_INT);
      ut = StaticType.T_INT;
    }

    return requireNumeric(op, ut);
  }

  private StaticType requireNumeric(Operand op, StaticType typ) {
    return requireNumeric(op.getNode(), typ);
  }

  private StaticType requireNumeric(BaseNode node, StaticType typ) {
    if (typ instanceof NumericType) {
      return typ;
    } else {
      error(node, "Expected numeric type not " + typ);
      return StaticType.ERROR_TYPE;
    }
  }

  /**
   * The containers to try in order when looking up a member.
   * <p>
   * @param explicitContainerType null if the member use is a free use.
   */
  private <T extends MemberInfo>
  ImmutableList<ParameterizedMember<T>> findMembers(
      String memberSimpleName,
      @Nullable StaticType explicitContainerType,
      Function<TypeSpecification,
               Optional<ImmutableList<ParameterizedMember<T>>>> searchOne) {
    if (explicitContainerType != null) {
      return searchOne.apply(explicitContainerType.typeSpecification)
          .or(ImmutableList.of());
    }
    TypeInfo last = null;
    for (TypeInfo containingType : Lists.reverse(containingTypes)) {
      if (last != containingType) {
        ImmutableList.Builder<TypeSpecification.TypeBinding> bindings =
            ImmutableList.builder();
        for (Name param : containingType.parameters) {
          bindings.add(new TypeSpecification.TypeBinding(param));
        }
        Optional<ImmutableList<ParameterizedMember<T>>> results =
            searchOne.apply(TypeSpecification.autoScoped(containingType));
        if (results.isPresent()) {
          return results.get();
        }
        last = containingType;
      }
    }
    // Fall back to static imports.
    Collection<TypeInfo> staticImportsForMember = staticImports.get(
        memberSimpleName);
    if (!staticImportsForMember.isEmpty()) {
      ImmutableList.Builder<ParameterizedMember<T>> staticallyImported =
          ImmutableList.builder();
      for (TypeInfo imported : staticImportsForMember) {
        Optional<ImmutableList<ParameterizedMember<T>>> results =
            searchOne.apply(TypeSpecification.autoScoped(imported));
        if (results.isPresent()) {
          staticallyImported.addAll(results.get());
        }
      }
      ImmutableList<ParameterizedMember<T>> fromStaticImports =
          staticallyImported.build();
      if (!fromStaticImports.isEmpty()) {
        return fromStaticImports;
      }
    }
    // Fall back to wildcard imports.
    ImmutableList.Builder<ParameterizedMember<T>> staticallyImported =
        ImmutableList.builder();
    for (TypeInfo imported : staticWildcardImports) {
      Optional<ImmutableList<ParameterizedMember<T>>> results =
          searchOne.apply(TypeSpecification.autoScoped(imported));
      if (results.isPresent()) {
        staticallyImported.addAll(results.get());
      }
    }
    return staticallyImported.build();
  }

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

    ImmutableList<ParameterizedMember<CallableInfo>> methods = findMembers(
        methodName,
        calleeType,
        new Function<
            TypeSpecification,
            Optional<ImmutableList<ParameterizedMember<CallableInfo>>>>() {
          @Override
          public
          Optional<ImmutableList<ParameterizedMember<CallableInfo>>> apply(
              TypeSpecification oneContainingType) {
            ImmutableList<ParameterizedMember<CallableInfo>> methodsFound =
                memberInfoPool.getMembers(
                    CallableInfo.class,
                    methodName,
                    sourceType,
                    oneContainingType);
            if (!methodsFound.isEmpty()) {
              // Non-private field declarations mask all super-type
              // declarations of the same name.
              return Optional.of(methodsFound);
            }
            return Optional.absent();
          }
        });

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
            if (DEBUG) {
              System.err.println(
                  "For " + m.member.getDescriptor() + ", minArity=" + minArity
                  + ", maxArity=" + maxArity + ", nActuals=" + nActuals);
            }
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
      System.err.println("\n* " + sourceNode.getSourcePosition());
      for (ParameterizedMember<CallableInfo> m : methods) {
        System.err.println(
            "METHOD " + m.declaringType
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
                sourceNode,
                "Missing info for declaring type " + m.declaringType);
          }
        }
        if (!typeArguments.isEmpty()) {
          for (int i = 0, n = typeArguments.size(); i < n; ++i) {
            TypeBinding typeArgument = typeArguments.get(i);
            Name typeParameter = m.member.typeParameters.get(i);
            if (TypeBinding.WILDCARD.equals(typeArgument)) {
              StaticType lowerBound = typePool.type(
                  new TypeSpecification(typeParameter),
                  null,
                  logger);
              if (lowerBound instanceof ReferenceType) {
                lowerBound = ((ReferenceType) lowerBound).lowerBound();
              }
              typeArgument = new TypeBinding(
                  Variance.EXTENDS, lowerBound.typeSpecification);
            }
            substMap.put(typeParameter, typeArgument);
          }
        }

        Function<Name, TypeBinding> contextualize =
            new Function<Name, TypeBinding>() {
              @Override
              public TypeBinding apply(Name nm) {
                TypeBinding b;
                if (nm.type == Name.Type.TYPE_PARAMETER) {
                  b = substMap.get(nm);
                  if (b == null) {
                    StaticType t = typePool.type(
                        new TypeSpecification(nm),
                        sourceNode.getSourcePosition(), logger);
                    if (t instanceof ReferenceType) {
                      StaticType lowerBound = ((ReferenceType) t).lowerBound();
                      b = new TypeBinding(
                          Variance.EXTENDS, lowerBound.typeSpecification);
                    }
                  }
                } else {
                  b = null;
                }
                return b;
              }
            };

        ImmutableList<TypeSpecification> formalTypes =
            m.member.getFormalTypes();
        int arity = formalTypes.size();
        ImmutableList<StaticType> formalTypesInContext;
        {
          ImmutableList.Builder<StaticType> b = ImmutableList.builder();
          for (int i = 0; i < arity; ++i) {
            b.add(typePool.type(
                formalTypes.get(i).subst(contextualize),
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
            formalType = formalTypesInContext.get(arity - 1);
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

          // TODO: This castRequired check fails when either formalType or
          // actualType are parameterized forms.  Actually implement
          // https://docs.oracle.com/javase/specs/jls/se8/html/jls-18.html#jls-18.5
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
                m.member.getReturnType().subst(contextualize),
                sourceNode.getSourcePosition(), logger);
          ImmutableList<Cast> casts = actualToFormalCasts.build();
          if (DEBUG) {
            System.err.println("For " + m.member.getDescriptor()
                + ", formalTypesInContext=" + formalTypesInContext
                + ", casts=" + casts
                + ", requiresVariadicArrayConstruction="
                + requiresVariadicArrayConstruction
                + ", returnTypeInContext=" + returnTypeInContext);
          }
          results.add(new MethodSearchResult(
              m, formalTypesInContext, returnTypeInContext,
              casts, requiresVariadicArrayConstruction));
        } else {
          if (DEBUG) {
            System.err.println(
                "Method " + m.member.canonName + " not compatible");
          }
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
      if (DEBUG) {
        System.err.println("requiresUnOrBoxing=" + requiresUnOrBoxing);
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
              if (DEBUG) {
                System.err.println(
                    "Eliminated " + rarr[j].m.member.canonName
                    + "#" + rarr[j].m.member.getDescriptor()
                    + "(" + Joiner.on(", ").join(rarr[j].formalTypesInContext)
                    + ")");
              }
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
      // TODO: JLS S 15.12 does not use parameters after explicit replacement to
      // infer bounds.  We need to take the actual expressions too.
      // For e.g., test f(byte) vs f(int) given integral constants that fit in
      // [-128,127].
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

      if (DEBUG) {
        System.err.println(
            "Testing " + this.m.member.canonName.identifier + "\n"
            + this.m.member.getDescriptor()
            + "(" + Joiner.on(", ").join(this.formalTypesInContext) + ")\n"
            + "more specific than\n"
            + that.m.member.getDescriptor()
            + "(" + Joiner.on(", ").join(that.formalTypesInContext) + ")\n"
            + "aArity=" + aArity + ", bArity=" + bArity + "\n"
            + "aMarginalFormalType=" + aMarginalFormalType
            + ", bMarginalFormalType=" + bMarginalFormalType + "\n"
            + "nonVarArgsArity=" + nonVarArgsArity);
      }

      boolean oneMoreSpecificThan = false;
      for (int i = 0; i < nonVarArgsArity; ++i) {
        Boolean b = parameterSpecificity(aTypes.get(i), bTypes.get(i));
        if (b == null) {
          continue;
        } else if (b) {
          oneMoreSpecificThan = true;
        } else {
          if (DEBUG) {
            System.err.println("\tparameterSpecificity " + i + " = false");
          }
          return false;
        }
      }

      if (!(aIsVariadic || bIsVariadic)) {
        if (DEBUG) {
          System.err.println(
              "\tNeither variadic oneMoreSpecificThan=" + oneMoreSpecificThan
              + ", same arity=" + (aArity == bArity));
        }
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
          if (DEBUG) {
            System.err.println("\tExtra arg not more specific " + aIndex);
          }
          return false;
        }
      }
      oneMoreSpecificThan = true;

      // A has no more, b accepts more.
      //   ()
      //   (B...)
      if (aIndex == aArity && !aIsVariadic && bIndex + 1 == bArity) {
        if (DEBUG) {
          System.err.println("\tMore specific due to empty variadic match");
        }
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
        if (DEBUG) {
          System.err.println(
              "\tBoth variadic specificity=" + specificity
              + ", oneMoreSpecificThan=" + oneMoreSpecificThan);
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
      if (DEBUG) {
        System.err.println("\tRan out of more specific cases");
      }
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
          .buildNode(ImmutableList.of())
          .setStaticType(typ);
    }

    NumericType nt = (NumericType) typ;

    NodeVariant v = NUMERIC_TYPE_TO_VARIANT.get(nt);
    NumericTypeNode numericTypeNode =
        (nt.isFloaty ? NumericTypeNode.Variant.FloatingPointType
        : NumericTypeNode.Variant.IntegralType)
        .buildNode(v.buildNode(ImmutableList.of()))
        .setStaticType(typ);

    return PrimitiveTypeNode.Variant.AnnotationNumericType
        .buildNode(numericTypeNode)
        .setStaticType(typ);
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
        typeNode = TypeNode.Variant.ReferenceType.buildNode(
            ReferenceTypeNode.Variant.ArrayType.buildNode(
                ArrayTypeNode.Variant.TypeAnnotationDim.buildNode(
                    typeNode, DimNode.Variant.LsRs.buildNode())
                .setStaticType(ct))
            .setStaticType(ct));
      }
      return ReferenceTypeNode.Variant.ArrayType.buildNode(
              ArrayTypeNode.Variant.TypeAnnotationDim.buildNode(
                  typeNode, DimNode.Variant.LsRs.buildNode())
              .setStaticType(at))
          .setStaticType(at);
    } else if (typ instanceof TypePool.ClassOrInterfaceType) {
      TypePool.ClassOrInterfaceType ct = (ClassOrInterfaceType) typ;
      ClassOrInterfaceTypeNode ciNode = toClassOrInterfaceTypeNode(
          ct.typeSpecification.typeName, ct.typeParameterBindings);
      ciNode.setStaticType(typ);
      return ReferenceTypeNode.Variant.ClassOrInterfaceType.buildNode(ciNode)
          .setStaticType(typ);
    } else {
      throw new AssertionError(typ);
    }
  }

  private ClassOrInterfaceTypeNode toClassOrInterfaceTypeNode(
      Name nm, ImmutableList<TypeBinding> bindings) {
    ClassOrInterfaceTypeNode parent = nm.parent.equals(Name.DEFAULT_PACKAGE)
        ? null
        : toClassOrInterfaceTypeNode(nm.parent, ImmutableList.of());
    IdentifierNode ident = IdentifierNode.Variant.Builtin
        .buildNode(nm.identifier)
        .setNamePartType(nm.type);
    TypeArgumentsNode arguments = null;
    if (!bindings.isEmpty()) {
      TypeArgumentListNode typeArgumentList =
          TypeArgumentListNode.Variant.TypeArgumentComTypeArgument
          .buildNode();
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
              TypeArgumentNode.Variant.ReferenceType.buildNode(rt));
        } else {
          typeArgumentList.add(
              TypeArgumentNode.Variant.Wildcard.buildNode(
              WildcardNode.Variant.AnnotationQmWildcardBounds
                  .buildNode(boundsVariant.buildNode(rt))));
        }
      }
      arguments = TypeArgumentsNode.Variant.LtTypeArgumentListGt.buildNode(
          typeArgumentList);
    }
    ClassOrInterfaceTypeNode newTypeNode = ClassOrInterfaceTypeNode.Variant
        .ClassOrInterfaceTypeDotAnnotationIdentifierTypeArguments.buildNode();
    if (parent != null) {
      newTypeNode.add(parent);
    }
    newTypeNode.add(ident);
    if (arguments != null) {
      newTypeNode.add(arguments);
    }
    return newTypeNode;
  }

  private TypeNode toTypeNode(StaticType typ) {
    if (typ instanceof PrimitiveType) {
      return TypeNode.Variant.PrimitiveType.buildNode(
          toPrimitiveTypeNode((PrimitiveType) typ));
    } else {
      Preconditions.checkArgument(typ instanceof ReferenceType);
      return TypeNode.Variant.ReferenceType.buildNode(
          toReferenceTypeNode((ReferenceType) typ));
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
