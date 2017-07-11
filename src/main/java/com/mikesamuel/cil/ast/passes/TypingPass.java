package com.mikesamuel.cil.ast.passes;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.EnumMap;
import java.util.Iterator;
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
import com.google.common.collect.Sets;
import com.mikesamuel.cil.ast.j8.AdditiveExpressionNode;
import com.mikesamuel.cil.ast.j8.AdditiveOperatorNode;
import com.mikesamuel.cil.ast.j8.AndExpressionNode;
import com.mikesamuel.cil.ast.j8.ArgumentListNode;
import com.mikesamuel.cil.ast.j8.ArrayCreationExpressionNode;
import com.mikesamuel.cil.ast.j8.ArrayElementTypeNode;
import com.mikesamuel.cil.ast.j8.ArrayInitializerNode;
import com.mikesamuel.cil.ast.j8.AssignmentNode;
import com.mikesamuel.cil.ast.j8.AssignmentOperatorNode;
import com.mikesamuel.cil.ast.j8.CaseValueNode;
import com.mikesamuel.cil.ast.j8.CastExpressionNode;
import com.mikesamuel.cil.ast.j8.CastNode;
import com.mikesamuel.cil.ast.j8.CatchTypeNode;
import com.mikesamuel.cil.ast.j8.ClassLiteralNode;
import com.mikesamuel.cil.ast.j8.ClassOrInterfaceTypeNode;
import com.mikesamuel.cil.ast.j8.ClassOrInterfaceTypeToInstantiateNode;
import com.mikesamuel.cil.ast.j8.ClassTypeNode;
import com.mikesamuel.cil.ast.j8.ConditionalExpressionNode;
import com.mikesamuel.cil.ast.j8.ConfirmCastNode;
import com.mikesamuel.cil.ast.j8.ConvertCastNode;
import com.mikesamuel.cil.ast.j8.DimExprNode;
import com.mikesamuel.cil.ast.j8.DimNode;
import com.mikesamuel.cil.ast.j8.DimsNode;
import com.mikesamuel.cil.ast.j8.EnumConstantNameNode;
import com.mikesamuel.cil.ast.j8.EqualityExpressionNode;
import com.mikesamuel.cil.ast.j8.ExclusiveOrExpressionNode;
import com.mikesamuel.cil.ast.j8.ExpressionAtomNode;
import com.mikesamuel.cil.ast.j8.ExpressionNode;
import com.mikesamuel.cil.ast.j8.FieldNameNode;
import com.mikesamuel.cil.ast.j8.IdentifierNode;
import com.mikesamuel.cil.ast.j8.InclusiveOrExpressionNode;
import com.mikesamuel.cil.ast.j8.Intermediates;
import com.mikesamuel.cil.ast.j8.J8BaseInnerNode;
import com.mikesamuel.cil.ast.j8.J8BaseNode;
import com.mikesamuel.cil.ast.j8.J8BinaryOp;
import com.mikesamuel.cil.ast.j8.J8ExpressionNameReference;
import com.mikesamuel.cil.ast.j8.J8ExpressionNameScope;
import com.mikesamuel.cil.ast.j8.J8LimitedScopeElement;
import com.mikesamuel.cil.ast.j8.J8LocalDeclaration;
import com.mikesamuel.cil.ast.j8.J8MemberDeclaration;
import com.mikesamuel.cil.ast.j8.J8MethodDescriptorReference;
import com.mikesamuel.cil.ast.j8.J8NodeType;
import com.mikesamuel.cil.ast.j8.J8NodeTypeTables;
import com.mikesamuel.cil.ast.j8.J8NodeVariant;
import com.mikesamuel.cil.ast.j8.J8TypeDeclaration;
import com.mikesamuel.cil.ast.j8.J8TypeScope;
import com.mikesamuel.cil.ast.j8.J8Typed;
import com.mikesamuel.cil.ast.j8.J8WholeType;
import com.mikesamuel.cil.ast.j8.LastFormalParameterNode;
import com.mikesamuel.cil.ast.j8.LocalNameNode;
import com.mikesamuel.cil.ast.j8.MethodNameNode;
import com.mikesamuel.cil.ast.j8.Mixins;
import com.mikesamuel.cil.ast.j8.MultiplicativeExpressionNode;
import com.mikesamuel.cil.ast.j8.MultiplicativeOperatorNode;
import com.mikesamuel.cil.ast.j8.NumericTypeNode;
import com.mikesamuel.cil.ast.j8.PrefixOperatorNode;
import com.mikesamuel.cil.ast.j8.PrimaryNode;
import com.mikesamuel.cil.ast.j8.PrimitiveTypeNode;
import com.mikesamuel.cil.ast.j8.ReferenceTypeNode;
import com.mikesamuel.cil.ast.j8.RelationalExpressionNode;
import com.mikesamuel.cil.ast.j8.ShiftExpressionNode;
import com.mikesamuel.cil.ast.j8.ShiftOperatorNode;
import com.mikesamuel.cil.ast.j8.SingleStaticImportDeclarationNode;
import com.mikesamuel.cil.ast.j8.StaticImportOnDemandDeclarationNode;
import com.mikesamuel.cil.ast.j8.SwitchStatementNode;
import com.mikesamuel.cil.ast.j8.TypeArgumentListNode;
import com.mikesamuel.cil.ast.j8.TypeArgumentNode;
import com.mikesamuel.cil.ast.j8.TypeArgumentsNode;
import com.mikesamuel.cil.ast.j8.TypeNameNode;
import com.mikesamuel.cil.ast.j8.UnannTypeNode;
import com.mikesamuel.cil.ast.j8.UnaryExpressionNode;
import com.mikesamuel.cil.ast.j8.UnqualifiedClassInstanceCreationExpressionNode;
import com.mikesamuel.cil.ast.j8.VariableDeclaratorIdNode;
import com.mikesamuel.cil.ast.j8.VariableInitializerNode;
import com.mikesamuel.cil.ast.meta.CallableInfo;
import com.mikesamuel.cil.ast.meta.ExpressionNameResolver;
import com.mikesamuel.cil.ast.meta.ExpressionNameResolver
    .DeclarationPositionMarker;
import com.mikesamuel.cil.ast.meta.FieldInfo;
import com.mikesamuel.cil.ast.meta.JavaLang;
import com.mikesamuel.cil.ast.meta.MemberInfo;
import com.mikesamuel.cil.ast.meta.MemberInfoPool;
import com.mikesamuel.cil.ast.meta.MemberInfoPool.ParameterizedMember;
import com.mikesamuel.cil.ast.meta.Name;
import com.mikesamuel.cil.ast.meta.PartialTypeSpecification;
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
import com.mikesamuel.cil.parser.SList;
import com.mikesamuel.cil.parser.SourcePosition;
import com.mikesamuel.cil.util.TriState;

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
      J8BaseNode node, @Nullable SList<Parent> pathFromRoot) {

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

    if (node instanceof J8ExpressionNameScope) {
      J8ExpressionNameScope scope = (J8ExpressionNameScope) node;
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
    if (node instanceof J8TypeScope) {
      J8TypeScope scope = (J8TypeScope) node;
      TypeNameResolver typeNameResolver = scope.getTypeNameResolver();
      if (typeNameResolver == null) {
        typeNameResolver = typeNameResolvers.peekLast();
      }
      typeNameResolvers.add(typeNameResolver);
    }

    if (node instanceof J8LimitedScopeElement) {
      J8LimitedScopeElement el = (J8LimitedScopeElement) node;
      DeclarationPositionMarker marker = el.getDeclarationPositionMarker();
      if (marker != null) {
        declarationPositionMarkers.set(
            declarationPositionMarkers.size() - 1, marker);
      }
    }

    if (node instanceof J8TypeDeclaration) {
      TypeInfo typeInfo = ((J8TypeDeclaration) node).getDeclaredTypeInfo();
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
          J8BaseNode anc = p.x.parent;
          if (anc instanceof J8LocalDeclaration) {
            J8LocalDeclaration localDecl = (J8LocalDeclaration) anc;
            J8BaseNode typeNode = Mixins.getDeclaredTypeNode(localDecl);
            if (typeNode instanceof CatchTypeNode) {
              CatchTypeNode ctyp = (CatchTypeNode) typeNode;
              ImmutableList.Builder<ReferenceType> excTypes =
                  ImmutableList.builder();
              boolean isError = false;
              for (J8BaseNode child : ctyp.getChildren()) {
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
            } else if (typeNode instanceof J8WholeType) {
              StaticType styp = ((J8WholeType) typeNode).getStaticType();
              if (anc.getVariant()
                  == LastFormalParameterNode.Variant.Variadic) {
                styp = typePool.type(
                    styp.typeSpecification.arrayOf(),
                    typeNode.getSourcePosition(),
                    logger);
              }
              this.locals.put(name, styp);
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
      J8BaseNode node, @Nullable SList<Parent> pathFromRoot) {
    process(node, pathFromRoot);

    if (node instanceof J8ExpressionNameScope) {
      expressionNameResolvers.removeLast();
      declarationPositionMarkers.removeLast();
    }

    if (node instanceof J8TypeScope) {
      typeNameResolvers.removeLast();
    }

    if (node instanceof J8TypeDeclaration) {
      containingTypes.removeLast();
    }

    return ProcessingStatus.CONTINUE;
  }

  private void process(J8BaseNode node, @Nullable SList<Parent> pathFromRoot) {

    if (node instanceof J8Typed) {
      J8Typed t = (J8Typed) node;
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
              exprType = processFieldAccess(e, Optional.absent());
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
              exprType = processMethodInvocation(e, Optional.absent());
              break type_switch;
            case Parenthesized:
              exprType = passThru(e);
              break type_switch;
            case QuotedName:
              exprType = typePool.type(  // TODO
                  JavaLang.JAVA_LANG_OBJECT, e.getSourcePosition(), logger);
              break type_switch;
            case StaticMember: {
              TypeNameNode tn = e.firstChildWithType(TypeNameNode.class);
              TypeInfo ti = tn != null ? tn.getReferencedTypeInfo() : null;
              if (ti != null) {
                exprType = typePool.type(
                    TypeSpecification.unparameterized(ti.canonName),
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
                  TypeSpecification.autoScoped(
                      containingTypes.peekLast().canonName,
                      typePool.r),
                  e.getSourcePosition(), logger);
              break type_switch;
            case UnqualifiedClassInstanceCreationExpression: {
              UnqualifiedClassInstanceCreationExpressionNode ctorCall =
                  e.firstChildWithType(
                      UnqualifiedClassInstanceCreationExpressionNode.class);
              Optional<ClassOrInterfaceTypeNode> type = node.finder(
                  ClassOrInterfaceTypeNode.class)
                  .exclude(
                      J8NodeType.ArgumentList,
                      J8NodeType.ClassBody,
                      J8NodeType.ClassOrInterfaceType,
                      J8NodeType.TypeArguments)
                  .findOne();
              if (type.isPresent()) {
                exprType = type.get().getStaticType();
                processCallableInvocation(
                    ctorCall, exprType, Optional.absent(),
                    Name.CTOR_INSTANCE_INITIALIZER_SPECIAL_NAME, ctorCall);
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
              ExpressionAtomNode receiverAtom = e.firstChildWithType(
                  ExpressionAtomNode.class);
              Optional<Name> superExclusion = Optional.absent();
              if (receiverAtom != null
                  && (receiverAtom.getVariant()
                      == ExpressionAtomNode.Variant.Super)) {
                TypeNameNode tnn = e.firstChildWithType(TypeNameNode.class);
                TypeInfo ti;
                if (tnn != null) {
                  ti = tnn.getReferencedTypeInfo();
                } else {
                  ti = containingTypes.peekLast();
                }
                if (ti != null) {
                  superExclusion = Optional.of(ti.canonName);
                } else {
                  error(tnn, "Missing type info");
                }
              }
              exprType = processMethodInvocation(e, superExclusion);
              break type_switch;
            }

            case ArrayAccess: {
              Operand arr = nthOperandOf(0, e, J8NodeType.Primary);
              Operand index = nthOperandOf(1, e, J8NodeType.Expression);
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
              ExpressionAtomNode receiverAtom = e.firstChildWithType(
                  ExpressionAtomNode.class);
              Optional<Name> superExclusion = Optional.absent();
              if (receiverAtom != null
                  && (receiverAtom.getVariant()
                      == ExpressionAtomNode.Variant.Super)) {
                TypeNameNode tnn = e.firstChildWithType(TypeNameNode.class);
                TypeInfo ti;
                if (tnn != null) {
                  ti = tnn.getReferencedTypeInfo();
                } else {
                  ti = containingTypes.peekLast();
                }
                if (ti != null) {
                  superExclusion = Optional.of(ti.canonName);
                } else {
                  error(tnn, "Missing type info");
                }
              }
              exprType = processFieldAccess(e, superExclusion);
              break type_switch;
            }

            case InnerClassCreation: {
              Operand outerInstance = nthOperandOf(
                  0, e, J8NodeType.Primary);
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
              Map<Name, TypeArgumentListNode> argumentsPerInnerName =
                  Maps.newLinkedHashMap();
              Name innerName = type != null
                  ? AmbiguousNames.ambiguousNameOf(
                      null, type, argumentsPerInnerName)
                  : null;
              if (outerType != null && innerName != null) {
                TypeSpecification outerSpec = outerType.typeSpecification;
                if (outerSpec.nDims == 0
                    && outerSpec.rawName.type == Name.Type.CLASS) {
                  SList<Name> names = null;
                  for (Name nm = innerName; nm != null; nm = nm.parent) {
                    Preconditions.checkState(nm.type == Name.Type.CLASS);
                    names = SList.append(names, nm);
                  }

                  TypeSpecification fullInnerTypeSpec = outerSpec;
                  for (SList<Name> nm = names; nm != null; nm = nm.prev) {
                    TypeArgumentListNode typeArguments =
                        argumentsPerInnerName.get(nm);
                    ImmutableList<TypeSpecification.TypeBinding> bindings =
                        typeArguments != null
                        ? AmbiguousNames.bindingsOf(
                            typeArguments, typeNameResolvers.peekLast(), logger)
                        : ImmutableList.of();
                    fullInnerTypeSpec = new TypeSpecification(
                        fullInnerTypeSpec, nm.x.identifier, nm.x.type,
                        bindings, 0);
                    // TODO: grab bindings from
                    // ClassOrInterfaceTypeToInstantiate or infer for diamond.
                  }
                  exprType = typePool.type(
                      fullInnerTypeSpec, e.getSourcePosition(), logger);
                  Preconditions.checkNotNull(type).setStaticType(exprType);
                  processCallableInvocation(
                      instantiation, exprType, Optional.absent(),
                      Name.CTOR_INSTANCE_INITIALIZER_SPECIAL_NAME,
                      instantiation);
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
          for (J8BaseNode child : e.getChildren()) {
            if (child instanceof DimNode) {
              ++dimensionality;
            }
          }
          TypeSpecification spec = null;
          switch (e.getVariant()) {
            case BooleanDimDotClass:
              spec = dimensionality == 0
                  ? TypeSpecification.unparameterized(
                      StaticType.T_BOOLEAN.wrapperType)
                  : StaticType.T_BOOLEAN.typeSpecification
                    .withNDims(dimensionality);
              break;
            case NumericTypeDimDotClass:
              NumericTypeNode nt = e.firstChildWithType(NumericTypeNode.class);
              if (nt != null) {
                StaticType st = nt.getStaticType();
                if (st instanceof NumericType) {
                  spec = TypeSpecification
                      .unparameterized(((NumericType) st).wrapperType)
                      .withNDims(dimensionality);
                  NumericType numType = (NumericType) st;
                  spec = dimensionality == 0
                      ? TypeSpecification.unparameterized(numType.wrapperType)
                      : numType.typeSpecification.withNDims(dimensionality);
                }
              }
              break;
            case TypeNameDimDotClass:
              TypeNameNode tn = e.firstChildWithType(TypeNameNode.class);
              if (tn != null) {
                TypeInfo ti = tn.getReferencedTypeInfo();
                if (ti != null) {
                  spec = TypeSpecification.unparameterized(ti.canonName);
                  // Classes always represent the lower-bound regardless of type
                  // parameters present.  For example, List.class represents
                  // Class<List<? extends Object>>
                  spec = spec.withBindings(
                      new Function<PartialTypeSpecification,
                                   ImmutableList<TypeBinding>>() {

                        @Override
                        public ImmutableList<TypeBinding> apply(
                            PartialTypeSpecification s) {
                          if (s instanceof TypeSpecification) {
                            Optional<TypeInfo> sti = typePool.r.resolve(
                                s.getRawName());
                            if (sti.isPresent()) {
                              ImmutableList.Builder<TypeBinding> b =
                                  ImmutableList.builder();
                              for (@SuppressWarnings("unused")
                                   Name param : sti.get().parameters) {
                                b.add(TypeBinding.WILDCARD);
                              }
                              return b.build();
                            }
                          }
                          return ImmutableList.of();
                        }

                      });
                  // Let the type pool canonicalize the given specification to
                  // resolve any wildcards inserted above.
                  spec = typePool.type(spec, e.getSourcePosition(), logger)
                      .typeSpecification;
                  if (!StaticType.ERROR_TYPE.typeSpecification.equals(spec)) {
                    spec = spec.withNDims(dimensionality);
                  }
                }
              }
              break;
            case VoidDotClass:
              Preconditions.checkState(dimensionality == 0);
              spec = JavaLang.JAVA_LANG_VOID;
              break;
          }
          if (spec != null) {
            TypeSpecification classSpec = JavaLang.JAVA_LANG_CLASS.withBindings(
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
          J8WholeType wholeElementType = elementTypeNode != null
              ? elementTypeNode.firstChildWithType(J8WholeType.class) : null;
          StaticType elementType = wholeElementType != null
              ? wholeElementType.getStaticType()
              : null;
          if (elementType == null) { elementType = StaticType.ERROR_TYPE; }
          int nExtraDims = 0;
          for (@SuppressWarnings("unused") DimNode dimNode
              : e.finder(DimNode.class)
                .exclude(
                    J8NodeType.Annotation, J8NodeType.DimExpr,
                    J8NodeType.ArrayInitializer, J8NodeType.ArrayElementType)
                .find()) {
            ++nExtraDims;
          }
          for (@SuppressWarnings("unused") DimExprNode dimExprNode
              : e.finder(DimExprNode.class)
                .exclude(
                    J8NodeType.Annotation, J8NodeType.DimExpr,
                    J8NodeType.ArrayInitializer, J8NodeType.ArrayElementType)
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
          Operand left = nthOperandOf(0, e, J8NodeType.LeftHandSide);
          Operand right = nthOperandOf(1, e, J8NodeType.Expression);
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
            J8BaseNode leftClone = left.getNode().shallowClone();
            Operand leftExpr = nthOperandOf(0, leftClone, J8NodeType.Primary);
            J8BaseNode completeRightHandSideOp =
                effectiveRightHandSideVariants.buildNode(
                        leftExpr.getNode(), right.getNode());
            completeRightHandSideOp.setSourcePosition(node.getSourcePosition());
            process(completeRightHandSideOp, null);
            StaticType completeRightType = maybePassThru(
                completeRightHandSideOp);
            if (StaticType.ERROR_TYPE.equals(completeRightType)) {
              break type_switch;
            }

            J8BinaryOp completeRightHandSideBinOp =
                (J8BinaryOp) completeRightHandSideOp;
            J8BaseNode adjustedLeft = (J8BaseNode)
                completeRightHandSideBinOp.getLeftOperand();
            J8BaseNode adjustedRight = (J8BaseNode)
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
                  0, e, J8NodeType.ConditionalOrExpression);
              Operand thenClause = nthOperandOf(
                  1, e, J8NodeType.Expression);
              Operand elseClause = nthOperandOf(
                  2, e, J8NodeType.ConditionalOrExpression);

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
                  if (convertibleToNumeric(contextTypeHint)) {
                    contextTypeHint = unboxedType(
                        e, contextTypeHint,
                        Predicates.instanceOf(NumericType.class));
                  } else {
                    // Possibly a widening conversion like Object or Number,
                    // but there's no info to be had.
                    contextTypeHint = null;
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
              node.getNodeType() == J8NodeType.ConditionalOrExpression
              ? J8NodeType.ConditionalAndExpression
              : J8NodeType.InclusiveOrExpression);
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
              Operand left = nthOperandOf(
                  0, e, J8NodeType.RelationalExpression);
              StaticType leftType = left != null ? maybePassThru(left) : null;
              J8WholeType right = e.firstChildWithType(J8WholeType.class);
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
          Operand left = nthOperandOf(0, e, J8NodeType.EqualityExpression);
          Operand right = nthOperandOf(1, e, J8NodeType.RelationalExpression);
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
          J8NodeType rightNodeType;
          switch (node.getNodeType()) {
            case InclusiveOrExpression:
              rightNodeType = J8NodeType.ExclusiveOrExpression;
              break;
            case ExclusiveOrExpression:
              rightNodeType = J8NodeType.AndExpression;
              break;
            case AndExpression:
              rightNodeType = J8NodeType.EqualityExpression;
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
          Operand left = nthOperandOf(0, node, J8NodeType.ShiftExpression);
          Operand right = nthOperandOf(1, node, J8NodeType.AdditiveExpression);
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
                if (!((NumericType) leftType).isFloaty
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
              0, e, J8NodeType.AdditiveExpression);
          Operand right = nthOperandOf(
              1, e, J8NodeType.MultiplicativeExpression);
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
                  0, e, J8NodeType.UnaryExpression);
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
                        operand, maybePassThru(operand));
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
          Operand op = nthOperandOf(0, node, J8NodeType.LeftHandSideExpression);
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
          for (J8WholeType wt
               : e.finder(J8WholeType.class)
                 .exclude(J8NodeType.UnaryExpression,
                          J8NodeType.LambdaExpression)
                 .exclude(J8WholeType.class)
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

        case CaseValue: {
          CaseValueNode e = (CaseValueNode) node;
          SList<Parent> pathToSwitchStmt = pathFromRoot;
          while (pathToSwitchStmt != null &&
                 BETWEEN_SWITCH_AND_CASE.contains(
                     pathToSwitchStmt.x.parent.getNodeType())) {
            pathToSwitchStmt = pathToSwitchStmt.prev;
          }
          if (pathToSwitchStmt != null
              && pathToSwitchStmt.x.parent.getNodeType()
                 == J8NodeType.SwitchStatement) {
            SwitchStatementNode switchStmt =
                (SwitchStatementNode) pathToSwitchStmt.x.parent;
            Operand expr = firstWithType(switchStmt, J8NodeType.Expression);
            // We assume here that the switch expression is processed before
            // the cases which works because the switch expression appears
            // lexically before the cases.
            exprType = ((ExpressionNode) expr.getNode()).getStaticType();
            if (exprType instanceof ClassOrInterfaceType
                && TO_WRAPPED.containsKey(
                    ((ClassOrInterfaceType) exprType).info.canonName)) {
              exprType = unboxAsNecessary(expr, exprType);
            }
            if (exprType != null) {
              if (e.getVariant() == CaseValueNode.Variant.Ambiguous) {
                // TODO: Do all the other case values have to be ambiguous
                // for any to be interpreted as enum case values?
                if (rewriteAmbiguousCaseValue(exprType, e)) {
                  // Type the replacement expression.
                  for (int i = 0, n = e.getNChildren(); i < n; ++i) {
                    process(
                        e.getChild(i),
                        SList.append(pathFromRoot, new Parent(i, e)));
                  }
                }
              }
              break type_switch;
            }
          }
          exprType = StaticType.ERROR_TYPE;
          error(e, "Failed to find expression type for case statement");
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
      Operand op = nthOperandOf(0, node, J8NodeType.Expression);
      if (op == null) { return; }
      StaticType exprType = ((J8Typed) op.getNode()).getStaticType();
      if (exprType == null) { return; }

      SList<Parent> path = pathFromRoot;
      int nDims = 0;
      for (; path != null; path = path.prev) {
        J8NodeType nt = path.x.parent.getNodeType();
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


  private boolean rewriteAmbiguousCaseValue(
      StaticType exprType, CaseValueNode e) {
    Operand caseIdentOp = firstWithType(e, J8NodeType.Identifier);
    if (caseIdentOp == null) {
      error(e, "Missing identifier");
      return false;
    }
    IdentifierNode caseIdentNode = (IdentifierNode) caseIdentOp.getNode();
    String caseIdent = caseIdentNode.getValue();
    if (caseIdent != null) {
      // Rewrite as free field or as enum constant
      StaticType enumType = typePool.type(
          JavaLang.JAVA_LANG_ENUM, null, logger);
      if (enumType instanceof ClassOrInterfaceType) {
        // TODO: Should we check an isEnum bit instead to distinguish
        // btw `enum E { ... }` and `class E extends Enum<E>`
        Name enumFieldName = null;
        switch (Compatibility.of(enumType.assignableFrom(exprType))) {
          case IMPLICIT_CAST:
            ClassOrInterfaceType cit = (ClassOrInterfaceType) exprType;
            for (MemberInfo mi : cit.info.getDeclaredMembers()) {
              if (mi instanceof FieldInfo
                  // TODO: check a bit to differentiate enum
                  // declarations from other fields of the enum
                  && Modifier.isStatic(mi.modifiers)
                  && mi.canonName.identifier.matches(caseIdent)) {
                enumFieldName = mi.canonName;
                break;
              }
            }
            break;
          case COMPATIBLE_AS_IS:  // The type Enum has no enum members.
          case INCOMPATIBLE:
            break;
        }

        SourcePosition pos = caseIdentNode.getSourcePosition();
        if (enumFieldName != null) {
          e.setVariant(CaseValueNode.Variant.EnumConstantNameExpCln);
          FieldNameNode fieldName = FieldNameNode.Variant.Identifier
              .buildNode(caseIdentNode)
              .setReferencedExpressionName(enumFieldName);
          fieldName.setSourcePosition(pos);
          EnumConstantNameNode constantName =
              EnumConstantNameNode.Variant.FieldName
              .buildNode(fieldName);
          constantName.setSourcePosition(pos);
          e.replace(caseIdentOp.indexInParent, constantName);
          return true;
        } else {
          Iterator<ExpressionNameResolver> enrIt =
              expressionNameResolvers.iterator();
          Iterator<DeclarationPositionMarker> dpmIt =
              declarationPositionMarkers.iterator();
          while (enrIt.hasNext()) {
            Preconditions.checkState(dpmIt.hasNext());
            ExpressionNameResolver enr = enrIt.next();
            DeclarationPositionMarker dpm = dpmIt.next();
            Optional<Name> nameOpt = enr.resolveReference(caseIdent, dpm);
            if (nameOpt.isPresent()) {
              Name name = nameOpt.get();
              ExpressionAtomNode constantExpr = null;
              switch (name.type) {
                case LOCAL:
                  LocalNameNode local = LocalNameNode.Variant.Identifier
                      .buildNode(caseIdentNode);
                  local.setReferencedExpressionName(name);
                  local.setSourcePosition(pos);

                  constantExpr = ExpressionAtomNode.Variant.Local
                      .buildNode(local);
                  break;
                case FIELD:
                  FieldNameNode fieldName = FieldNameNode.Variant.Identifier
                      .buildNode(caseIdentNode)
                      .setReferencedExpressionName(name);
                  fieldName.setSourcePosition(pos);
                  constantExpr = ExpressionAtomNode.Variant.FreeField
                      .buildNode(fieldName);
                  break;
                case AMBIGUOUS:
                case CLASS:
                case METHOD:
                case PACKAGE:
                case TYPE_PARAMETER:
                  throw new AssertionError(name);
              }
              Preconditions.checkNotNull(constantExpr);
              PrimaryNode primary = PrimaryNode.Variant.ExpressionAtom
                  .buildNode(constantExpr);
              primary.setSourcePosition(pos);

              Optional<J8BaseNode> constExpr = Intermediates.wrap(
                  primary, J8NodeType.ConstantExpression,
                  new Function<J8BaseNode, Void>() {
                    @Override
                    public Void apply(J8BaseNode node) {
                      node.setSourcePosition(pos);
                      return null;
                    }
                  });
              Preconditions.checkState(constExpr.isPresent());

              e.setVariant(CaseValueNode.Variant.ConstantExpression);
              e.replace(caseIdentOp.indexInParent, constExpr.get());
              return true;
            }
          }
          error(e, "Failed to disambiguate case " + caseIdent);
        }
      }
    }
    return false;
  }

  private StaticType processFieldAccess(
      J8BaseNode e, Optional<Name> superExclusion) {
    @Nullable J8Typed container = e.firstChildWithType(J8Typed.class);
    StaticType containerType;
    if (container != null) {
      containerType = container.getStaticType();
      if (containerType == null) {
        error(container, "Missing type for field container");
        return StaticType.ERROR_TYPE;
      }
    } else if (superExclusion.isPresent()) {
      // A reference to super.foo is explicitly specifying the field is a
      // super type of the narrowest containing class.
      containerType = typePool.type(
          TypeSpecification.autoScoped(superExclusion.get(), typePool.r),
          e, logger);
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
                    oneContainer,
                    superExclusion);
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
      Optional<TypeInfo> tiOpt = typePool.r.resolve(f.declaringType.rawName);
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

  private StaticType processMethodInvocation(
      J8BaseNode e, Optional<Name> superExclusion) {
    J8Typed callee = e.firstChildWithType(J8Typed.class);

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
      if (superExclusion.isPresent()) {
        // A call to super.method is explicitly looking for a method visible
        // in a super type of the narrowest containing class.
        calleeType = typePool.type(
            TypeSpecification.autoScoped(superExclusion.get(), typePool.r),
            e, logger);
      } else {
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
      }
    } else {
      // There's no need to box callee.  For example:
      //    int i = 0;
      //    (i).toString();
      // fails at compile time, because primitive types cannot
      // be used as left hand sides.
      calleeType = callee.getStaticType();
      if (calleeType == null) {
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

    return processCallableInvocation(
        e, calleeType, superExclusion, name, nameNode);
  }

  private StaticType processCallableInvocation(
      J8BaseNode e, StaticType calleeType, Optional<Name> superExclusion,
      String name, J8MethodDescriptorReference descriptorRef) {
    @Nullable TypeArgumentsNode args = e.firstChildWithType(
        TypeArgumentsNode.class);

    @Nullable Operand actualsOp = firstWithType(e, J8NodeType.ArgumentList);
    @Nullable ArgumentListNode actuals = actualsOp != null
        ? (ArgumentListNode) actualsOp.getNode() : null;

    ImmutableList.Builder<StaticType> actualTypes =
        ImmutableList.builder();
    if (actuals != null) {
      for (J8BaseNode actual : actuals.getChildren()) {
        if (actual instanceof J8Typed) {
          StaticType actualType = ((J8Typed) actual).getStaticType();
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
            .exclude(J8NodeType.TypeArguments)
            .find()) {
          TypeBinding b = AmbiguousNames.typeBindingOf(
              arg, canonResolver, logger);
          typeArguments.add(b);
        }
      }
    }

    Optional<MethodSearchResult> invokedMethodOpt = pickMethodOverload(
        e, calleeType, typeArguments.build(), superExclusion, name,
        actualTypes.build());
    if (!invokedMethodOpt.isPresent()) {
      error(e, "Unresolved use of method " + name);
      return StaticType.ERROR_TYPE;
    }

    MethodSearchResult invokedMethod = invokedMethodOpt.get();

    // Associate method descriptor with invokedMethod.
    descriptorRef.setMethodDescriptor(invokedMethod.m.member.getDescriptor());
    descriptorRef.setMethodDeclaringType(invokedMethod.m.declaringType);
    if (descriptorRef instanceof J8ExpressionNameReference) {
      ((J8ExpressionNameReference) descriptorRef)
          .setReferencedExpressionName(invokedMethod.m.member.canonName);
    }

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
          J8BaseNode actual = newActuals.getChild(i);
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
                  newActuals, actualIndex, J8NodeType.Expression);
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

  private StaticType maybePassThru(J8BaseNode node) {
    if (node instanceof J8Typed) {
      StaticType t = ((J8Typed) node).getStaticType();
      if (t == null) {
        error(node, "Untyped " + node.getVariant());
        return StaticType.ERROR_TYPE;
      }
      return t;
    }
    return passThru(node);
  }

  private StaticType passThru(J8BaseNode node) {
    for (J8BaseNode child : node.getChildren()) {
      if (J8NodeTypeTables.NONSTANDARD.contains(child.getNodeType())) {
        continue;
      }
      if (DEBUG) {
        System.err.println(
            "Considering " + child.getNodeType() + " in " + node.getNodeType());
      }
      if (child instanceof J8Typed) {
        StaticType t = ((J8Typed) child).getStaticType();
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
      J8BaseNode context, StaticType t, Predicate<? super PrimitiveType> ok) {
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

  private StaticType boxedType(J8BaseNode context, StaticType t) {
    if (StaticType.ERROR_TYPE.equals(t)) {
      return t;
    }
    if (StaticType.T_VOID.equals(t)) {
      error(context, "void where reference type expected");
      return StaticType.ERROR_TYPE;
    }
    if (t instanceof PrimitiveType) {
      return typePool.type(
          TypeSpecification.unparameterized(
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
      J8NodeVariant v = anc.x.parent.getVariant();
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

    J8BaseNode node = anc.x.parent;
    if (node instanceof J8LocalDeclaration) {
      J8WholeType typeNode = (J8WholeType)
          Mixins.getDeclaredTypeNode(((J8LocalDeclaration) node));
      StaticType declaredType = typeNode != null
          ? typeNode.getStaticType() : null;
      if (declaredType == null) {
        error(node, "Missing type info for declaration");
        return StaticType.ERROR_TYPE;
      }
      return declaredType;
    } else if (node instanceof J8MemberDeclaration) {
      J8MemberDeclaration decl = (J8MemberDeclaration) node;
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
      J8WholeType elementTypeNodeWholeType = elementTypeNode != null
          ? elementTypeNode.firstChildWithType(J8WholeType.class)
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
          .exclude(J8NodeType.Annotation)
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

  private static final ImmutableMap<Name, PrimitiveType> TO_WRAPPED;
  static {
    ImmutableMap.Builder<Name, PrimitiveType> b =
        ImmutableMap.<Name, PrimitiveType>builder();
    for (PrimitiveType pt : StaticType.PRIMITIVE_TYPES) {
      b.put(pt.wrapperType, pt);
      if (pt instanceof NumericType) {
        Preconditions.checkState(
            TypeNodeFactory.NUMERIC_TYPE_TO_VARIANT.containsKey(pt));
      }
    }
    TO_WRAPPED = b.build();
  }


  private static final ImmutableMap<AssignmentOperatorNode.Variant, OpVariants>
      COMPLEX_ASSIGNMENT_OPERATOR_TO_BINARY_OPERATOR_VARIANT;

  private static final class OpVariants {
    final J8NodeVariant operationVariant;
    final @Nullable J8NodeVariant operatorVariant;

    OpVariants(
        J8NodeVariant operationVariant,
        @Nullable J8NodeVariant operatorVariant) {
      this.operationVariant = operationVariant;
      this.operatorVariant = operatorVariant;
    }

    J8BaseNode buildNode(J8BaseNode leftOperand, J8BaseNode rightOperand) {
      ImmutableList.Builder<J8BaseNode> children = ImmutableList.builder();
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
    final J8BaseInnerNode parent;
    final int indexInParent;
    /**
     * An ancestor type for the operand which may not be exactly the same due
     * to {@link J8NodeVariant#isAnon()}.
     */
    final J8NodeType containerType;

    Operand(J8BaseInnerNode parent, int indexInParent,
            J8NodeType containerType) {
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
      J8BaseNode toCast = getNode();
      Optional<J8BaseNode> castable = Intermediates.wrap(
          toCast, J8NodeType.UnaryExpression,
          new Function<J8BaseNode, Void> () {
            @Override
            public Void apply(J8BaseNode intermediate) {
              if (intermediate instanceof J8Typed) {
                ((J8Typed) intermediate).setStaticType(sourceType);
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
        PrimitiveTypeNode targetTypeNode = TypeNodeFactory.toPrimitiveTypeNode(
            (PrimitiveType) targetType);
        cast = CastNode.Variant.ConvertCast.buildNode(
            ConvertCastNode.Variant.PrimitiveType.buildNode(targetTypeNode));
      } else {
        TypeNodeFactory typeNodeFactory = new TypeNodeFactory(logger, typePool);
        // TODO: handle +/- unary op ambiguity.
        // Maybe, if it's not an ExpressionAtom.Parenthesized, then
        // wrap it.  This may already necessarily happen due to the
        // Intermediates call below, but it's non-obvious and a maintenance
        // hazard as-is.
        ReferenceTypeNode targetTypeNode = typeNodeFactory.toReferenceTypeNode(
            (ReferenceType) targetType);
        cast = CastNode.Variant.ConfirmCast.buildNode(
            ConfirmCastNode.Variant.ReferenceTypeAdditionalBound.buildNode(
                targetTypeNode));
      }
      CastExpressionNode castExpr = CastExpressionNode.Variant.Expression
          .buildNode(cast, toCast);

      Optional<J8BaseNode> wrappedCast = Intermediates.wrap(
          castExpr, containerType,
          new Function<J8BaseNode, Void> () {
            @Override
            public Void apply(J8BaseNode intermediate) {
              if (intermediate instanceof J8Typed) {
                ((J8Typed) intermediate).setStaticType(targetType);
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

    J8BaseNode getNode() {
      return parent.getChild(indexInParent);
    }
  }

  private Operand nthOperandOf(
      int n, J8BaseNode parent, J8NodeType containerType) {
    if (parent instanceof J8BaseInnerNode) {
      J8BaseInnerNode inode = (J8BaseInnerNode) parent;

      int nLeft = n;
      int nChildren = inode.getNChildren();
      for (int i = 0; i < nChildren; ++i) {
        J8BaseNode child = inode.getChild(i);
        J8NodeType childNodeType = child.getNodeType();
        if (J8NodeTypeTables.OPERATOR.contains(childNodeType)) {
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

  private Operand firstWithType(J8BaseNode node, J8NodeType t) {
    if (!(node instanceof J8BaseInnerNode)) {
      return null;
    }
    J8BaseInnerNode inode = (J8BaseInnerNode) node;
    for (int i = 0, n = inode.getNChildren(); i < n; ++i) {
      J8BaseNode child = inode.getChild(i);
      if (child.getNodeType() == t) {
        return new Operand(inode, i, t);
      }
    }
    return null;
  }

  private static final ImmutableSet<J8NodeType> BETWEEN_SWITCH_AND_CASE =
      Sets.immutableEnumSet(
          J8NodeType.SwitchLabel, J8NodeType.SwitchLabels,
          J8NodeType.SwitchBlockStatementGroup,
          J8NodeType.SwitchBlock);


  private static final ImmutableSet<StaticType> PROMOTE_TO_INT =
      ImmutableSet.of(
          StaticType.T_BYTE, StaticType.T_CHAR, StaticType.T_SHORT);

  private StaticType processNumericBinary(J8BaseNode operation) {
    Operand left = nthOperandOf(
        0, operation, J8NodeType.AdditiveExpression);
    Operand right = nthOperandOf(
        1, operation, J8NodeType.MultiplicativeExpression);
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

  private StaticType requireNumeric(J8BaseNode node, StaticType typ) {
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
   * @param superExclusions if present, the class whose declared (not inherited)
   *     members should be excluded from search results.
   *     This allows us to implement {@code super}.member uses by excluding the
   *
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
          bindings.add(new TypeSpecification.TypeBinding(
              TypeSpecification.unparameterized(param)));
        }
        Optional<ImmutableList<ParameterizedMember<T>>> results =
            searchOne.apply(TypeSpecification.autoScoped(
                containingType.canonName, typePool.r));
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
            searchOne.apply(
                TypeSpecification.autoScoped(imported.canonName, typePool.r));
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
          searchOne.apply(TypeSpecification.autoScoped(
              imported.canonName, typePool.r));
      if (results.isPresent()) {
        staticallyImported.addAll(results.get());
      }
    }
    return staticallyImported.build();
  }

  private Optional<MethodSearchResult> pickMethodOverload(
      J8BaseNode sourceNode,
      @Nullable StaticType calleeType,
      ImmutableList<TypeBinding> typeArguments,
      Optional<Name> superExclusion,
      String methodName,
      ImmutableList<StaticType> actualTypes) {
    if (DEBUG) {
      System.err.println("calleeType=" + calleeType);
      System.err.println("typeArguments=" + typeArguments);
      System.err.println("actualTypes=" + actualTypes);
    }
    if (containingTypes.isEmpty()) {
      error(sourceNode, "no containing type for " + methodName);
      return Optional.absent();
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
                    oneContainingType,
                    superExclusion);
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
              m.declaringType.rawName);
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
                  TypeSpecification.unparameterized(typeParameter),
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
                        TypeSpecification.unparameterized(nm),
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
          // //docs.oracle.com/javase/specs/jls/se8/html/jls-18.html#jls-18.5
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
        TriState s = parameterSpecificity(aTypes.get(i), bTypes.get(i));
        switch (s) {
          case OTHER:
            continue;
          case TRUE:
            oneMoreSpecificThan = true;
            break;
          case FALSE:
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
        TriState s = parameterSpecificity(
            aTypes.get(aIndex), bMarginalFormalType);
        switch (s) {
          case OTHER:
          case TRUE:
            // This is the case because the required min arity is now more
            // specific.
            oneMoreSpecificThan = true;
            break;
          case FALSE:
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
        TriState specificity;
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
        switch (specificity) {
          case TRUE:  return true;
          case FALSE: return false;
          case OTHER: return oneMoreSpecificThan;
        }
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

    /**
     * True -> a is more specific than b;
     * Other -> a is the same as b.
     * False -> a is not more specific than b.
     */
    private static TriState parameterSpecificity(
        StaticType aType, StaticType bType) {
      Cast c = bType.assignableFrom(aType);
      switch (c) {
        case CONFIRM_SAFE:
        case CONFIRM_UNCHECKED:
        case CONVERTING_LOSSLESS:
          return TriState.TRUE;
        case CONVERTING_LOSSY:
        case BOX:
        case UNBOX:
        case DISJOINT:
        case CONFIRM_CHECKED:
          return TriState.FALSE;
        case SAME:
          return TriState.OTHER;
      }
      throw new AssertionError(c);
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
