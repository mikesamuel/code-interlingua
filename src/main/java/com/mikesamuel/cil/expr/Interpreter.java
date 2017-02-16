package com.mikesamuel.cil.expr;

import javax.annotation.Nullable;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.mikesamuel.cil.ast.AdditiveExpressionNode;
import com.mikesamuel.cil.ast.AdditiveOperatorNode;
import com.mikesamuel.cil.ast.ArgumentListNode;
import com.mikesamuel.cil.ast.ArrayCreationExpressionNode;
import com.mikesamuel.cil.ast.ArrayInitializerNode;
import com.mikesamuel.cil.ast.AssignmentNode;
import com.mikesamuel.cil.ast.AssignmentOperatorNode;
import com.mikesamuel.cil.ast.BaseNode;
import com.mikesamuel.cil.ast.BlockNode;
import com.mikesamuel.cil.ast.BlockStatementsNode;
import com.mikesamuel.cil.ast.BooleanLiteralNode;
import com.mikesamuel.cil.ast.ContextFreeNameNode;
import com.mikesamuel.cil.ast.DimExprNode;
import com.mikesamuel.cil.ast.DimExprsNode;
import com.mikesamuel.cil.ast.EnhancedForStatementNode;
import com.mikesamuel.cil.ast.EqualityExpressionNode;
import com.mikesamuel.cil.ast.EqualityOperatorNode;
import com.mikesamuel.cil.ast.ExpressionAtomNode;
import com.mikesamuel.cil.ast.ExpressionNode;
import com.mikesamuel.cil.ast.FieldNameNode;
import com.mikesamuel.cil.ast.FinallyNode;
import com.mikesamuel.cil.ast.IdentifierNode;
import com.mikesamuel.cil.ast.IncrDecrOperatorNode;
import com.mikesamuel.cil.ast.LabelNode;
import com.mikesamuel.cil.ast.LeftHandSideNode;
import com.mikesamuel.cil.ast.LocalNameNode;
import com.mikesamuel.cil.ast.LocalVariableDeclarationNode;
import com.mikesamuel.cil.ast.MethodNameNode;
import com.mikesamuel.cil.ast.MultiplicativeExpressionNode;
import com.mikesamuel.cil.ast.MultiplicativeOperatorNode;
import com.mikesamuel.cil.ast.NodeType;
import com.mikesamuel.cil.ast.PrefixOperatorNode;
import com.mikesamuel.cil.ast.PrimaryNode;
import com.mikesamuel.cil.ast.RelationalExpressionNode;
import com.mikesamuel.cil.ast.RelationalOperatorNode;
import com.mikesamuel.cil.ast.ResourceNode;
import com.mikesamuel.cil.ast.ResourceSpecificationNode;
import com.mikesamuel.cil.ast.StatementNode;
import com.mikesamuel.cil.ast.SwitchBlockNode;
import com.mikesamuel.cil.ast.SwitchBlockStatementGroupNode;
import com.mikesamuel.cil.ast.SwitchLabelNode;
import com.mikesamuel.cil.ast.SwitchLabelsNode;
import com.mikesamuel.cil.ast.UnannTypeNode;
import com.mikesamuel.cil.ast.UnaryExpressionNode;
import com.mikesamuel.cil.ast.UnqualifiedClassInstanceCreationExpressionNode;
import com.mikesamuel.cil.ast.VariableDeclaratorIdNode;
import com.mikesamuel.cil.ast.VariableDeclaratorNode;
import com.mikesamuel.cil.ast.VariableInitializerListNode;
import com.mikesamuel.cil.ast.VariableInitializerNode;
import com.mikesamuel.cil.ast.meta.CallableInfo;
import com.mikesamuel.cil.ast.meta.FieldInfo;
import com.mikesamuel.cil.ast.meta.JavaLang;
import com.mikesamuel.cil.ast.meta.MemberInfo;
import com.mikesamuel.cil.ast.meta.Name;
import com.mikesamuel.cil.ast.meta.StaticType;
import com.mikesamuel.cil.ast.meta.StaticType.PrimitiveType;
import com.mikesamuel.cil.ast.meta.StaticType.TypePool.ArrayType;
import com.mikesamuel.cil.ast.meta.StaticType.TypePool.ClassOrInterfaceType;
import com.mikesamuel.cil.ast.meta.StaticType.TypePool.ReferenceType;
import com.mikesamuel.cil.ast.meta.TypeInfo;
import com.mikesamuel.cil.ast.meta.TypeInfoResolver;
import com.mikesamuel.cil.ast.meta.TypeSpecification;
import com.mikesamuel.cil.ast.traits.Typed;
import com.mikesamuel.cil.ast.traits.WholeType;
import com.mikesamuel.cil.parser.SourcePosition;
import com.mikesamuel.cil.ptree.Tokens;
import com.mikesamuel.cil.util.LogUtils;
import com.mikesamuel.cil.util.TriState;

import static com.mikesamuel.cil.expr.Completion.normal;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/**
 * An AST interpreter for Java 8 statements and expressions.
 */
public final class Interpreter<VALUE> {
  /** Used to perform primitive operations. */
  public final InterpretationContext<VALUE> context;
  private final Completion<VALUE> nullCompletion;
  final Completion<VALUE> errorCompletion;
  private SourcePosition currentSourcePosition;

  Interpreter(InterpretationContext<VALUE> context) {
    this.context = context;
    nullCompletion = Completion.normal(context.nullValue());
    errorCompletion = Completion.normal(context.errorValue());
  }

  void error(@Nullable BaseNode node, String message) {
    LogUtils.log(context.getLogger(), Level.SEVERE, node, message, null);
  }

  /** Interprets the given AST fragment. */
  public Completion<VALUE> interpret(BaseNode node) {
    return interpret(node, new Locals<>());
  }

  /**
   * Interprets the given AST fragment.
   *
   * @param locals used to resolve free variables.
   */
  public Completion<VALUE> interpret(BaseNode node, Locals<VALUE> locals) {
    Supplier<SourcePosition> oldSourcePositionSupplier =
        context.getSourcePositionSupplier();

    context.setSourcePositionSupplier(
        new Supplier<SourcePosition>() {

          @SuppressWarnings("synthetic-access")
          @Override
          public SourcePosition get() {
            return currentSourcePosition;
          }

        });
    Completion<VALUE> result = interpret(node, locals, null);
    context.setSourcePositionSupplier(oldSourcePositionSupplier);
    return result;
  }

  private static final ImmutableSet<NodeType> NONSPECIFIC_DELEGATES =
      Sets.immutableEnumSet(
          NodeType.FieldName, NodeType.TypeName, NodeType.LocalName);

  /**
   * @param parentLabel the label of the containing block so that interpretation
   *     can properly handle continues.
   */
  @SuppressWarnings({ "synthetic-access" })
  private Completion<VALUE> interpret(
      BaseNode nodei, Locals<VALUE> locals,
      @Nullable String parentLabel) {
    // Handle delegates by recursing
    BaseNode node = nodei;
    if (node == null) {
      return errorCompletion;
    }
    while (true) {
      NodeType delegate = node.getVariant().getDelegate();
      if (delegate != null && !NONSPECIFIC_DELEGATES.contains(delegate)
          && node.getNChildren() == 1) {
        node = node.getChild(0);
      } else {
        break;
      }
    }

    SourcePosition pos = node.getSourcePosition();
    if (pos != null) {
      this.currentSourcePosition = pos;
    }

    NodeType nodeType = node.getNodeType();
    switch (nodeType) {
      case AdditionalBound:
      case AdditiveOperator:
      case AmbiguousBinaryUnaryOperator:
      case AssignmentOperator:
      case ClassDeclaration:
      case EmptyStatement:
      case EqualityOperator:
      case IncrDecrOperator:
      case MultiplicativeOperator:
      case PrefixOperator:
      case RelationalOperator:
      case ShiftOperator:
      case TypeName:
        return nullCompletion;
      case AdditiveExpression: {
        AdditiveExpressionNode e = (AdditiveExpressionNode) node;
        return new BinaryOp<AdditiveOperatorNode>() {

          @Override
          VALUE apply(VALUE left, AdditiveOperatorNode op, VALUE right) {
            switch (op.getVariant()) {
              case Dsh:
                return context.primitiveSubtraction(left, right);
              case Pls:
                BaseNode ln = e.getChild(0), rn = e.getChild(1);
                if (ambiguousPlusShouldConcatenate(ln, left, rn, right)) {
                  return context.stringConcatenation(left, right);
                } else {
                  return context.primitiveAddition(left, right);
                }
            }
            throw new AssertionError(op);
          }

        }.apply(e, AdditiveOperatorNode.class, locals);
      }
      case AndExpression:
        return new BinaryOp<BaseNode>() {
          @Override
          VALUE apply(VALUE left, BaseNode op, VALUE right) {
            return context.primitiveAnd(left, right);
          }
        }.apply(node, null, locals);
      case Annotation:
        break;
      case AnnotationTypeBody:
        break;
      case AnnotationTypeDeclaration:
        break;
      case AnnotationTypeElementDeclaration:
        break;
      case AnnotationTypeElementModifier:
        break;
      case AnnotationTypeMemberDeclaration:
        break;
      case ArgumentList:
        break;
      case ArrayCreationExpression: {
        ArrayCreationExpressionNode e = (ArrayCreationExpressionNode) node;
        switch (e.getVariant()) {
          case NewArrayElementTypeDimExprsDimsNotLs:
            StaticType resultType = e.getStaticType();
            if (resultType instanceof ArrayType) {
              ArrayType arrType = (ArrayType) resultType;
              DimExprsNode dimExprsNode = e.firstChildWithType(
                  DimExprsNode.class);
              ImmutableList<DimExprNode> dimExprs = dimExprsNode
                  .finder(DimExprNode.class)
                  .exclude(NodeType.Annotation, NodeType.Expression)
                  .find();
              int n = dimExprs.size();
              int[] dims = new int[n];
              for (int i = 0; i < n; ++i) {
                Completion<VALUE> dimResult = interpret(
                    dimExprs.get(i), locals, null);
                if (!completedNormallyWithoutError(dimResult)) {
                  return dimResult;
                }
                Optional<Integer> length = context.toInt(dimResult.value);
                if (length.isPresent()) {
                  dims[i] = length.get();
                  if (dims[i] < 0) {
                    error(dimExprs.get(i), "Negative array length: " + dims[i]);
                    return errorCompletion;
                  }
                } else {
                  error(dimExprs.get(i), "Invalid array length: " + dimResult);
                  return errorCompletion;
                }
              }
              return normal(buildMultiDimArray(dims, 0, arrType));
            } else {
              error(e, "Missing type info");
              return errorCompletion;
            }
          case NewArrayElementTypeDimsArrayInitializer:
            ArrayInitializerNode initializer = e.firstChildWithType(
                ArrayInitializerNode.class);
            return interpret(initializer, locals, null);
        }
        throw new AssertionError(e);
      }
      case ArrayElementType:
        break;
      case ArrayInitializer: {
        ArrayInitializerNode e = (ArrayInitializerNode) node;
        StaticType arrayType = e.getStaticType();
        StaticType elementType;
        if (arrayType instanceof ArrayType) {
          elementType = ((ArrayType) arrayType).elementType;
        } else {
          elementType = context.getTypePool().type(
              JavaLang.JAVA_LANG_OBJECT, e.getSourcePosition(),
              context.getLogger());
        }
        VariableInitializerListNode ilist = e.firstChildWithType(
            VariableInitializerListNode.class);

        int n = ilist != null ? ilist.getNChildren() : 0;
        VALUE arr = context.newArray(elementType, n);
        if (ilist != null) {
          for (int i = 0; i < n; ++i) {
            Completion<VALUE> element =
                interpret(ilist.getChild(i), locals, null);
            if (completedNormallyWithoutError(element)) {
              context.arraySet(arr, i, element.value);
            } else {
              return element;
            }
          }
        }
        return normal(arr);
      }
      case ArrayType:
        break;
      case AssertStatement:
        break;
      case Assignment: {
        AssignmentNode e = (AssignmentNode) node;
        if (e.getNChildren() != 3) {
          error(e, "Malformed assignment");
          return errorCompletion;
        }
        LeftHandSideNode lhs = e.firstChildWithType(LeftHandSideNode.class);
        AssignmentOperatorNode op = e.firstChildWithType(
            AssignmentOperatorNode.class);
        BaseNode rhs = rightOperand(e);
        if (lhs == null || op == null || rhs == null) {
          error(e, "Malformed assignment");
          return errorCompletion;
        }
        // We need to evaluate the rhs after the left-hand-side to get the
        // proper order of side-effects.
        Supplier<Completion<VALUE>> rhsSupplier =
            new Supplier<Completion<VALUE>>() {
              @Override
              public Completion<VALUE> get() {
                return interpret(rhs, locals, null);
              }
            };

        final LazyBinaryOp computeNewValue = binaryOpFor(
            lhs, op.getVariant(), rhs);

        return withLeftHandSide(
            lhs, locals,
            new AssignLeftHandSideHandler(computeNewValue, rhsSupplier));

      }
      case BasicForStatement: {
        BaseNode forInit = null;
        BaseNode expression = null;
        BaseNode forUpdate = null;
        BaseNode statement = null;
        for (int i = 0, n = node.getNChildren(); i < n; ++i) {
          BaseNode child = node.getChild(i);
          switch (child.getNodeType()) {
            case ForInit: forInit = child; break;
            case Expression: expression = child; break;
            case ForUpdate: forUpdate = child; break;
            case Statement: statement = child; break;
            default:
              throw new AssertionError(child);
          }
        }
        Locals<VALUE> forLocals = new Locals<>(locals);
        return interpretLoop(
            forInit, expression, forUpdate, statement,
            forLocals, parentLabel);
      }
      case Block:
        Locals<VALUE> blockLocals = new Locals<>(locals);
        return interpret(
            node.firstChildWithType(BlockStatementsNode.class),
            blockLocals, null);
      case BlockStatement:
        break;
      case BlockStatements: {
        int n = node.getNChildren();
        for (int i = 0; i < n; ++i) {
          Completion<VALUE> result = interpret(
              node.getChild(i), locals, parentLabel);
          if (result.kind == Completion.Kind.NORMAL
              && !context.isErrorValue(result.value)) {
            continue;
          }
          if (result.kind == Completion.Kind.BREAK
              && result.label != null && result.label.equals(parentLabel)) {
            break;
          }
          return result;
        }
        return nullCompletion;
      }
      case BlockTypeScope: {
        BaseNode stmts = node.firstChildWithType(BlockStatementsNode.class);
        if (stmts == null) { return nullCompletion; }
        return interpret(stmts, locals, parentLabel);
      }
      case BooleanLiteral:
        switch (((BooleanLiteralNode) node).getVariant()) {
          case False: return normal(context.from(false));
          case True: return normal(context.from(true));
        }
        break;
      case BreakStatement:
      case ContinueStatement: {
        String label = null;
        LabelNode labelNode = node.firstChildWithType(LabelNode.class);
        if (labelNode != null) {
          IdentifierNode ident = labelNode.firstChildWithType(
              IdentifierNode.class);
          if (ident == null) {
            error(labelNode, "Missing label");
            return errorCompletion;
          }
          label = ident.getValue();
        }
        return NodeType.BreakStatement == node.getNodeType()
            ? Completion.breakTo(label)
            : Completion.continueTo(label);
      }
      case CaseValue:
        break;
      case Cast:
        break;
      case CastExpression: {
        if (node.getNChildren() != 2) {
          error(node, "Malformed cast");
          return errorCompletion;
        }
        BaseNode cast = node.getChild(0);
        Completion<VALUE> operandResult = interpret(
            node.getChild(1), locals, null);
        Optional<WholeType> targetTypeOpt = cast.finder(WholeType.class)
            .exclude(WholeType.class).findOne();
        if (!targetTypeOpt.isPresent()
            || !completedNormallyWithoutError(operandResult)) {
          error(node, "Missing cast target type");
          return errorCompletion;
        }
        StaticType targetType = targetTypeOpt.get().getStaticType();
        if (targetType == null || StaticType.ERROR_TYPE.equals(targetType)) {
          error(node, "Missing cast target type");
          return errorCompletion;
        }
        return normal(context.coercion(targetType).apply(operandResult.value));
      }
      case CatchClause:
        break;
      case CatchFormalParameter:
        break;
      case CatchType:
        break;
      case Catches:
        break;
      case CharacterLiteral: {
        String charLit = node.getValue();
        long posAndChar = Tokens.decodeChar(charLit, 1);
        char ch = posAndChar < 0 || (posAndChar >> 32) + 1 != charLit.length()
            ? '\ufffd' : (char) posAndChar;
        return normal(context.from(ch));
      }
      case ClassBody:
        break;
      case ClassBodyDeclaration:
        break;
      case ClassInstanceCreationExpression:
        break;
      case ClassLiteral:
        break;
      case ClassMemberDeclaration:
        break;
      case ClassModifier:
        break;
      case ClassOrInterfaceType:
        break;
      case ClassOrInterfaceTypeNoTypeArguments:
        break;
      case ClassOrInterfaceTypeToInstantiate:
        break;
      case ClassType:
        break;
      case CompilationUnit:
        break;
      case ConditionalAndExpression:
      case ConditionalOrExpression: {
        BaseNode leftOperand = leftOperand(node);
        Completion<VALUE> leftResult = interpret(leftOperand, locals, null);
        if (!completedNormallyWithoutError(leftResult)) {
          return leftResult;
        }
        boolean isAnd = node.getNodeType() == NodeType.ConditionalAndExpression;
        // Short circuit as appropriate.
        switch (context.toBoolean(leftResult.value)) {
          case FALSE:
            if (isAnd) { return leftResult; }
            break;
          case OTHER:  // Unbox of null
            error(node, "Invalid result for (||): " + leftResult);
            return errorCompletion;
          case TRUE:
            if (!isAnd) { return leftResult; }
            break;
        }
        BaseNode rightOperand = rightOperand(node);
        Completion<VALUE> rightResult = interpret(rightOperand, locals, null);
        if (!completedNormallyWithoutError(rightResult)) {
          return rightResult;
        }
        switch (context.toBoolean(rightResult.value)) {
          case FALSE: case TRUE:
            return rightResult;
          case OTHER:
            return errorCompletion;
        }
        throw new AssertionError(rightResult);
      }
      case ConditionalExpression:
      case IfStatement: {
        int nChildren = node.getNChildren();
        if (nChildren != 2 && nChildren != 3) { return errorCompletion; }
        BaseNode cond = node.getChild(0);
        Completion<VALUE> condResult = interpret(cond, locals, null);
        if (!completedNormallyWithoutError(condResult)) {
          error(cond, "Bad condition result " + condResult.value);
          return condResult;
        }
        TriState b = context.toBoolean(condResult.value);
        switch (b) {
          case FALSE:
            if (nChildren == 2) { return nullCompletion; }  // if w/o else
            return interpret(node.getChild(2), locals, null);
          case OTHER:
            error(cond, "Bad condition result " + condResult.value);
            return errorCompletion;
          case TRUE:
            return interpret(node.getChild(1), locals, null);
        }
        throw new AssertionError(condResult.value);
      }
      case ConfirmCast:
        break;
      case ConstantDeclaration:
        break;
      case ConstantExpression:
        break;
      case ConstantModifier:
        break;
      case ConstructorBody:
        break;
      case ConstructorDeclaration:
        break;
      case ConstructorDeclarator:
        break;
      case ConstructorModifier:
        break;
      case ContextFreeName:
        break;
      case ContextFreeNames: {
        Completion<VALUE> result = null;
        for (BaseNode child : node.getChildren()) {
          if (child instanceof ContextFreeNameNode) {
            IdentifierNode identNode = child.firstChildWithType(
                IdentifierNode.class);
            if (identNode == null) {
              error(node, "missing identifier in context free name");
              return errorCompletion;
            }
            String ident = identNode.getValue();
            VALUE v;
            if (result == null) {
              Name name = Name.root(ident, Name.Type.AMBIGUOUS);
              v = locals.get(name, context.errorValue());
              // TODO: or dynamic field on the this value.
            } else {
              v = context.getFieldDynamic(ident, result.value);
            }
            if (context.isErrorValue(v)) {
              return errorCompletion;
            }
            result = normal(v);
          }
        }
        if (result == null) {
          error(node, "zero context free names");
          return errorCompletion;
        }
        return result;
      }
      case ConvertCast:
        break;
      case DefaultValue:
        break;
      case Diamond:
        break;
      case Dim:
        break;
      case DimExpr: {
        ExpressionNode e = node.firstChildWithType(ExpressionNode.class);
        return interpret(e, locals, null);
      }
      case DimExprs:
        break;
      case Dims:
        break;
      case DoStatement: {
        if (node.getNChildren() != 2) {
          error(node, "Malformed do loop " + node);
          return errorCompletion;
        }
        BaseNode body = node.getChild(0);
        BaseNode expression = node.getChild(1);
        Completion<VALUE> firstTimeThroughBody = interpret(body, locals, null);
        switch (firstTimeThroughBody.kind) {
          case BREAK:
            if (firstTimeThroughBody.label == null
                || firstTimeThroughBody.label.equals(parentLabel)) {
              return nullCompletion;
            }
            return firstTimeThroughBody;
          case CONTINUE:
            if (firstTimeThroughBody.label == null
                || firstTimeThroughBody.label.equals(parentLabel)) {
              break;
            }
            return firstTimeThroughBody;
          case NORMAL:
            break;
          case RETURN:
          case THROW:
            return firstTimeThroughBody;
        }
        return interpretLoop(
            null, expression, null, body, locals, parentLabel);
      }
      case DotKeywordDimsOrCtorRef:
        break;
      case ElementValue:
        break;
      case ElementValueArrayInitializer:
        break;
      case ElementValueList:
        break;
      case ElementValuePair:
        break;
      case ElementValuePairList:
        break;
      case EnhancedForStatement: {
        EnhancedForStatementNode s = (EnhancedForStatementNode) node;
        WholeType elementTypeNode = s.firstChildWithType(WholeType.class);
        VariableDeclaratorIdNode decl = s.firstChildWithType(
            VariableDeclaratorIdNode.class);
        ExpressionNode sequenceNode = s.firstChildWithType(ExpressionNode.class);
        StatementNode body = s.firstChildWithType(StatementNode.class);
        if (elementTypeNode == null || decl == null || sequenceNode == null
            || body == null) {
          error(s, "Malformed loop");
          return errorCompletion;
        }

        StaticType elementType = elementTypeNode.getStaticType();
        if (elementType == null) {
          elementType = context.getTypePool().type(
              JavaLang.JAVA_LANG_OBJECT, null, context.getLogger());
        }

        Name elementName = decl.getDeclaredExpressionName();
        if (elementName == null) {
          elementName = Name.root(
              decl.getDeclaredExpressionIdentifier(), Name.Type.AMBIGUOUS);
        }

        Locals<VALUE> loopLocals = new Locals<>(locals);
        loopLocals.declare(elementName, context.coercion(elementType));

        Completion<VALUE> sequence = interpret(sequenceNode, loopLocals, null);
        if (!completedNormallyWithoutError(sequence)) {
          return sequence;
        }

        StaticType sequenceType = sequenceNode.getStaticType();
        if (sequenceType == null) {
          sequenceType = context.runtimeType(sequence.value);
        }

        if (sequenceType instanceof ClassOrInterfaceType) {
          TypeInfo sti = ((ClassOrInterfaceType) sequenceType).info;
          Optional<CallableInfo> iteratorMethod = findMethod(
              sti, "iterator", "()Ljava/util/Iterator;");
          if (!iteratorMethod.isPresent()) {
            error(sequenceNode, "Not iterable");
            return errorCompletion;
          }
          VALUE iterator = context.invokeVirtual(
              iteratorMethod.get(), sequence.value, ImmutableList.of());

          StaticType iteratorType = context.runtimeType(iterator);
          if (!(iteratorType instanceof ClassOrInterfaceType)) {
            error(sequenceNode,
                ".iterator() returned value of type " + iteratorType);
            return errorCompletion;
          }
          TypeInfo iti = ((ClassOrInterfaceType) iteratorType).info;
          Optional<CallableInfo> hasNext = findMethod(
              iti, "hasNext", "()Z");
          Optional<CallableInfo> next = findMethod(
              iti, "next", "()Ljava/lang/Object;");
          if (hasNext.isPresent() && next.isPresent()) {
            while (true) {
              VALUE hasNextValue = context.invokeVirtual(
                  hasNext.get(), iterator, ImmutableList.of());
              switch (context.toBoolean(hasNextValue)) {
                case TRUE:
                  break;
                case FALSE:
                  return nullCompletion;
                case OTHER:
                  error(sequenceNode, "Expected boolean from hasNext(), not "
                        + hasNextValue);
                  return errorCompletion;
              }
              VALUE element = context.invokeVirtual(
                  next.get(), iterator, ImmutableList.of());
              if (context.isErrorValue(element)) { return normal(element); }
              loopLocals.set(elementName, element);

              Completion<VALUE> result = interpret(
                  body, loopLocals, parentLabel);
              switch (result.kind) {
                case BREAK:
                  if (result.label == null
                      || result.label.equals(parentLabel)) {
                    return nullCompletion;
                  }
                  return result;
                case CONTINUE:
                  if (result.label == null
                      || result.label.equals(parentLabel)) {
                    break;
                  }
                  return result;
                case NORMAL:
                  if (context.isErrorValue(result.value)) {
                    return result;
                  }
                  break;
                case RETURN:
                case THROW:
                  return result;
              }
            }
          }
        } else if (sequenceType instanceof ArrayType) {
          VALUE arr = sequence.value;
          int len = context.arrayLength(arr);
          for (int i = 0; i < len; ++i) {
            VALUE element = context.arrayGet(arr, i);
            if (context.isErrorValue(element)) { return normal(element); }
            loopLocals.set(elementName, element);

            Completion<VALUE> result = interpret(
                body, loopLocals, parentLabel);
            switch (result.kind) {
              case BREAK:
                if (result.label == null
                    || result.label.equals(parentLabel)) {
                  return nullCompletion;
                }
                return result;
              case CONTINUE:
                if (result.label == null
                    || result.label.equals(parentLabel)) {
                  continue;
                }
                return result;
              case NORMAL:
                if (context.isErrorValue(result.value)) {
                  return result;
                }
                continue;
              case RETURN:
              case THROW:
                return result;
            }
          }
          return nullCompletion;
        }
        error(sequenceNode, "Don't know how to iterate over " + sequenceType);
        return errorCompletion;
      }
      case EnumBody:
        break;
      case EnumBodyDeclarations:
        break;
      case EnumConstant:
        break;
      case EnumConstantList:
        break;
      case EnumConstantModifier:
        break;
      case EnumConstantName:
        break;
      case EnumDeclaration:
        break;
      case EqualityExpression: {
        EqualityExpressionNode e = (EqualityExpressionNode) node;

        return new BinaryOp<EqualityOperatorNode>() {
          boolean isPrimitiveComparison;

          @Override
          void previewOperands(BaseNode left, BaseNode right) {
            isPrimitiveComparison = true;
            // Do primitive comparison if either is primitive.
            StaticType ltype = ((Typed) left).getStaticType();
            if (ltype instanceof PrimitiveType) {
              return;
            }
            StaticType rtype = ((Typed) right).getStaticType();
            if (rtype instanceof PrimitiveType) {
              return;
            }
            isPrimitiveComparison = !(
                ltype instanceof ReferenceType
                && rtype instanceof ReferenceType);
          }

          @Override
          VALUE apply(VALUE left, EqualityOperatorNode op, VALUE right) {
            VALUE equal = isPrimitiveComparison
                ? context.primitiveEquals(left, right)
                : context.sameReference(left, right);
            switch (op.getVariant()) {
              case BngEq: return context.primitiveLogicalNot(equal);
              case EqEq:  return equal;
            }
            throw new AssertionError(op);
          }

        }.apply(e, EqualityOperatorNode.class, locals);
      }
      case ExceptionType:
        break;
      case ExceptionTypeList:
        break;
      case ExclusiveOrExpression:
        return new BinaryOp<BaseNode>() {
          @Override
          VALUE apply(VALUE left, BaseNode op, VALUE right) {
            return context.primitiveXor(left, right);
          }
        }.apply(node, null, locals);
      case ExplicitConstructorInvocation:
        break;
      case Expression:
        break;
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
          case Local:
            return withLeftHandSide(e, locals, new ReadLeftHandSideHandler());
          case Literal:
            break;
          case MethodInvocation:
            break;
          case Parenthesized:
            break;
          case QuotedName:
            break;
          case StaticMember:
            return nullCompletion;
          case StaticReference:
            break;
          case Super:
            break;
          case This:
            break;
          case UnqualifiedClassInstanceCreationExpression:
            break;
        }
        break;
      }
      case ExpressionStatement:
        break;
      case ExtendsInterfaces:
        break;
      case FieldDeclaration:
        break;
      case FieldModifier:
        break;
      case FieldName:
        break;
      case Finally:
        return interpret(
            node.firstChildWithType(BlockNode.class), locals, null);
      case FloatingPointLiteral:
      case IntegerLiteral: {
        String text = node.getValue();
        int textLength = text.length();

        int suffixChar = text.charAt(textLength - 1) | 32;
        if (textLength > 2 && '0' == text.charAt(0)
            && 'x' == (text.charAt(1) | 32)
            && 'a' <= suffixChar && suffixChar <= 'f') {
          // Don't treat 'd' or 'f' at the end of a hex literal as a type
          // suffix.
          // "0xABCD" is not a double literal with text "0xABC".
          suffixChar = -1;
        }
        switch (suffixChar) {
          case 'd':
            return normal(context.from(
                Tokens.decodeDouble(text.substring(0, textLength - 1))));
          case 'f':
            return normal(context.from(
                Tokens.decodeFloat(text.substring(0, textLength - 1))));
          case 'l':
            return normal(context.from(
                Tokens.decodeLong(text.substring(0, textLength - 1))));
          default:
            return nodeType == NodeType.IntegerLiteral
                ? normal(context.from(Tokens.decodeInt(text)))
                : normal(context.from(Tokens.decodeDouble(text)));
        }
      }
      case FloatingPointType:
        break;
      case ForInit:
        break;
      case ForStatement:
        break;
      case ForUpdate:
        break;
      case FormalParameter:
        break;
      case FormalParameterList:
        break;
      case FormalParameters:
        break;
      case Identifier:
        break;
      case ImportDeclaration:
        break;
      case InclusiveOrExpression:
        return new BinaryOp<BaseNode>() {
          @Override
          VALUE apply(VALUE left, BaseNode op, VALUE right) {
            return context.primitiveOr(left, right);
          }
        }.apply(node, null, locals);
      case InferredFormalParameterList:
        break;
      case InstanceInitializer:
        break;
      case IntegralType:
        break;
      case InterfaceBody:
        break;
      case InterfaceDeclaration:
        break;
      case InterfaceMemberDeclaration:
        break;
      case InterfaceMethodDeclaration:
        break;
      case InterfaceMethodModifier:
        break;
      case InterfaceModifier:
        break;
      case InterfaceType:
        break;
      case InterfaceTypeList:
        break;
      case JavaDocComment:
        break;
      case Label:
        break;
      case LabeledStatement: {
        LabelNode labelNode = node.firstChildWithType(LabelNode.class);
        BaseNode stmt = node.firstChildWithType(StatementNode.class);
        if (stmt != null
            && ((StatementNode) stmt).getVariant()
                == StatementNode.Variant.Block) {
          // A block is a barrier for parentLabel, so feed this through
          // explicitly.
          stmt = stmt.firstChildWithType(BlockNode.class);
          if (stmt != null) {
            stmt = stmt.firstChildWithType(BlockStatementsNode.class);
          }
        }
        IdentifierNode identNode = labelNode != null
            ? labelNode.firstChildWithType(IdentifierNode.class)
            : null;
        if (identNode == null || stmt == null) {
          error(node, "Malformed labeled statement");
          return errorCompletion;
        }
        String label = identNode.getValue();
        Completion<VALUE> result = interpret(stmt, locals, label);
        if (label.equals(result.label)) {
          return nullCompletion;
        }
        return result;
      }
      case LambdaBody:
        break;
      case LambdaExpression:
        break;
      case LambdaParameters:
        break;
      case LastFormalParameter:
        break;
      case LeftHandSide:
        break;
      case LeftHandSideExpression:
        break;
      case Literal:
        break;
      case LocalName:
        break;
      case LocalVariableDeclaration: {
        UnannTypeNode typeNode = node.firstChildWithType(UnannTypeNode.class);
        if (typeNode == null) { return errorCompletion; }
        StaticType type = typeNode.getStaticType();
        Function<VALUE, VALUE> coercion = context.coercion(type);
        for (VariableDeclaratorNode decl
            : node.finder(VariableDeclaratorNode.class)
                  .exclude(NodeType.VariableInitializer)
                  .find()) {
          VariableDeclaratorIdNode varid = decl.firstChildWithType(
              VariableDeclaratorIdNode.class);
          Name localName = varid.getDeclaredExpressionName();
          if (localName == null) {
            IdentifierNode id = varid.firstChildWithType(IdentifierNode.class);
            localName = Name.root(id.getValue(), Name.Type.LOCAL);
          }
          locals.declare(localName, coercion);
          VariableInitializerNode varinit =
              decl.firstChildWithType(VariableInitializerNode.class);
          if (varinit != null) {
            Completion<VALUE> initResult = interpret(varinit, locals, null);
            if (!completedNormallyWithoutError(initResult)) {
              return initResult;
            }
            locals.set(localName, initResult.value);
          }
        }
        return nullCompletion;
      }
      case LocalVariableDeclarationStatement:
        return interpret(
            node.firstChildWithType(LocalVariableDeclarationNode.class),
            locals, null);
      case MarkerAnnotation:
        break;
      case MethodBody:
        break;
      case MethodDeclaration:
        break;
      case MethodDeclarator:
        break;
      case MethodHeader:
        break;
      case MethodInvocation:
        break;
      case MethodModifier:
        break;
      case MethodName:
        break;
      case Modifier:
        break;
      case MultiplicativeExpression: {
        MultiplicativeExpressionNode e = (MultiplicativeExpressionNode) node;
        return new BinaryOp<MultiplicativeOperatorNode>() {

          @Override
          VALUE apply(VALUE left, MultiplicativeOperatorNode op, VALUE right) {
            switch (op.getVariant()) {
              case Str:
                return context.primitiveMultiplication(left, right);
              case Fwd:
                return context.primitiveDivision(left, right);
              case Pct:
                return context.primitiveModulus(left, right);
            }
            throw new AssertionError(op);
          }

        }.apply(e, MultiplicativeOperatorNode.class, locals);
      }
      case NormalAnnotation:
        break;
      case NormalClassDeclaration:
        break;
      case NormalInterfaceDeclaration:
        break;
      case NullLiteral:
        return nullCompletion;
      case NumericType:
        break;
      case PackageDeclaration:
        break;
      case PackageModifier:
        break;
      case PackageName:
        break;
      case PackageOrTypeName:
        break;
      case PostExpression:
      case PreExpression: {
        boolean isPost = node.getNodeType() == NodeType.PostExpression;
        LeftHandSideNode lhs = node.firstChildWithType(LeftHandSideNode.class);
        IncrDecrOperatorNode opNode = node.firstChildWithType(
            IncrDecrOperatorNode.class);
        if (lhs == null || opNode == null) {
          error(node, "malformed incr/decr op");
          return errorCompletion;
        }
        VALUE one = context.from(1);
        class ComputeNewValue extends LazyBinaryOp {
          VALUE leftBefore = context.errorValue();

          @Override
          VALUE perform(VALUE left, VALUE right) {
            leftBefore = left;
            switch (opNode.getVariant()) {
              case DshDsh:
                return context.primitiveSubtraction(left, one);
              case PlsPls:
                return context.primitiveAddition(left, one);
            }
            throw new AssertionError(opNode);
          }
        }
        ComputeNewValue cnv = new ComputeNewValue();
        Completion<VALUE> result = withLeftHandSide(
            lhs, locals,
            new AssignLeftHandSideHandler(
                cnv, Suppliers.ofInstance(nullCompletion)));
        return isPost && completedNormallyWithoutError(result)
            ? normal(cnv.leftBefore)
            : result;
      }
      case Primary: {
        PrimaryNode e = (PrimaryNode) node;
        switch (e.getVariant()) {
          case Ambiguous:
          case ArrayAccess:
          case FieldAccess:
            return withLeftHandSide(e, locals, new ReadLeftHandSideHandler());
          case ExpressionAtom:
            break;
          case InnerClassCreation:
            // TODO
            break;
          case MethodInvocation: {
            BaseNode objNode = e.getChild(0);
            MethodNameNode name = e.firstChildWithType(MethodNameNode.class);
            if (name == null) {
              error(e, "Call missing method name");
              return errorCompletion;
            }
            IdentifierNode identNode = name.firstChildWithType(
                IdentifierNode.class);
            if (identNode == null) {
              error(e, "Call missing method name");
              return errorCompletion;
            }
            String ident = identNode.getValue();
            @Nullable ArgumentListNode actuals = e.firstChildWithType(
                ArgumentListNode.class);
            TypeSpecification declType = name.getMethodDeclaringType();
            String descriptor = name.getMethodDescriptor();
            if (declType == null || descriptor == null) {
              error(e, "Call missing method name");
              return errorCompletion;
            }

            Optional<CallableInfo> infoOpt = callableForType(
                declType, ident, descriptor);
            if (!infoOpt.isPresent()) {
              error(name,
                  "Failed to resolve " + declType + "." + ident + descriptor);
              return errorCompletion;
            }

            CallableInfo info = infoOpt.get();
            boolean isStatic = Modifier.isStatic(info.modifiers);

            Completion<VALUE> objResult = isStatic
                ? null
                : interpret(objNode, locals, null);

            List<VALUE> actualValues = Lists.newArrayList();
            if (actuals != null) {
              for (BaseNode child : actuals.getChildren()) {
                Completion<VALUE> actualResult = interpret(child, locals, null);
                if (completedNormallyWithoutError(actualResult)) {
                  actualValues.add(actualResult.value);
                } else {
                  return errorCompletion;
                }
              }
            }

            if (isStatic) {
              return normal(context.invokeStatic(info, actualValues));
            } else {
              if (!completedNormallyWithoutError(objResult)) {
                return objResult;
              }
              return normal(context.invokeVirtual(
                  info, Preconditions.checkNotNull(objResult).value,
                  actualValues));
            }
          }
          case MethodReference:
            // TODO
            break;
        }
        break;
      }
      case PrimitiveType:
        break;
      case QuotedName:
        break;
      case ReceiverParameter:
        break;
      case ReferenceType:
        break;
      case RelationalExpression: {
        RelationalExpressionNode e = (RelationalExpressionNode) node;
        return new BinaryOp<RelationalOperatorNode>() {

          @Override
          VALUE apply(VALUE left, RelationalOperatorNode op, VALUE right) {
            switch (op.getVariant()) {
              case Gt:
                return context.primitiveLessThan(right, left);
              case GtEq:
                return context.primitiveLogicalNot(
                    context.primitiveLessThan(left, right));
              case Lt:
                return context.primitiveLessThan(left, right);
              case LtEq:
                return context.primitiveLogicalNot(
                    context.primitiveLessThan(right, left));
            }
            throw new AssertionError(op);
          }

        }.apply(e, RelationalOperatorNode.class, locals);
      }
      case Resource:
        break;
      case ResourceList:
        break;
      case ResourceSpecification:
        break;
      case Result:
        break;
      case ReturnStatement: {
        ExpressionNode expr = node.firstChildWithType(ExpressionNode.class);
        if (expr == null) {
          return Completion.returnValue(null);
        } else {
          Completion<VALUE> result = interpret(expr, locals, null);
          switch (result.kind) {
            case BREAK:
            case CONTINUE:
              return result;
            case NORMAL:
              if (context.isErrorValue(result.value)) {
                return result;
              }
              return Completion.returnValue(result.value);
            case RETURN:
            case THROW:
              return result;
          }
          throw new AssertionError(result);
        }
      }
      case ShiftExpression:
        break;
      case SimpleCallPrefix:
        break;
      case SimpleTypeName:
        break;
      case SingleElementAnnotation:
        break;
      case SingleStaticImportDeclaration:
        break;
      case SingleTypeImportDeclaration:
        break;
      case Statement:
        break;
      case StatementExpression:
        break;
      case StatementExpressionList: {
        int n = node.getNChildren();
        for (int i = 0; i < n; ++i) {
          BaseNode stmt = node.getChild(i);
          Completion<VALUE> result = interpret(stmt, locals, null);
          if (!completedNormallyWithoutError(result)) {
            return result;
          }
        }
        return nullCompletion;
      }
      case StaticImportOnDemandDeclaration:
        break;
      case StaticInitializer:
        break;
      case StringLiteral: {
        String encoded = node.getValue();
        StringBuilder sb = new StringBuilder(encoded.length() - 2);
        for (int i = 1, n = encoded.length() - 1; i < n;) {
          long posAndChar = Tokens.decodeChar(encoded, i);
          if (posAndChar < 0) {
            sb.append('\ufffd');
            ++i;
          } else {
            i = (int) (posAndChar >> 32);
            sb.append((char) posAndChar);
          }
        }
        return normal(context.from(sb.toString()));
      }
      case Superclass:
        break;
      case Superinterfaces:
        break;
      case SwitchBlock:
        break;
      case SwitchBlockStatementGroup:
        break;
      case SwitchLabel:
        return interpret(
            node.firstChildWithType(NodeType.CaseValue), locals, null);
      case SwitchLabels:
        break;
      case SwitchStatement: {
        ExpressionNode expr = node.firstChildWithType(ExpressionNode.class);
        SwitchBlockNode body = node.firstChildWithType(SwitchBlockNode.class);

        if (expr == null || body == null) {
          error(node, "Malformed switch");
          return errorCompletion;
        }

        VALUE exprValue;
        {
          Completion<VALUE> exprResult = interpret(expr, locals, null);
          if (!completedNormallyWithoutError(exprResult)) {
            return exprResult;
          }
          exprValue = exprResult.value;
        }
        if (context.isNullValue(exprValue)) {
          error(expr, "Cannot switch on null");
          return errorCompletion;
        }

        // If the value is primitive we will compare as per the primitive
        // variant of (==), otherwise we will compare as by Object.equals.
        CallableInfo equalsInfo = null;
        {
          StaticType exprType = expr.getStaticType();
          if (exprType == null) {
            exprType = context.runtimeType(exprValue);
          }
          if (exprType instanceof ClassOrInterfaceType) {
            TypeInfo ti = ((ClassOrInterfaceType) exprType).info;
            Optional<CallableInfo> mi = findMethod(
                ti, "equals", "(Ljava/lang/Object;)Z");
            if (mi.isPresent()) {
              equalsInfo = mi.get();
            }
          }
        }

        int matchIndex = -1;
        search:
        for (int i = 0, n = body.getNChildren(); i < n; ++i) {
          BaseNode child = body.getChild(i);
          if (child instanceof SwitchBlockStatementGroupNode) {
            SwitchBlockStatementGroupNode group =
                (SwitchBlockStatementGroupNode) child;
            SwitchLabelsNode labels = group.firstChildWithType(
                SwitchLabelsNode.class);
            if (labels == null) {
              error(node, "Malformed case group");
              return errorCompletion;
            }
            for (BaseNode labelsChild : labels.getChildren()) {
              boolean matchFound = false, matchMore = true;
              if (labelsChild instanceof SwitchLabelNode) {
                SwitchLabelNode label = (SwitchLabelNode) labelsChild;
                switch (label.getVariant()) {
                  case CaseCaseValueCln:
                    Completion<VALUE> caseResult = interpret(
                        label, locals, null);
                    if (!completedNormallyWithoutError(caseResult)) {
                      return caseResult;
                    }
                    VALUE eq = equalsInfo == null
                        ? context.primitiveEquals(
                            exprValue, caseResult.value)
                        : context.invokeVirtual(
                            equalsInfo,
                            exprValue,
                            ImmutableList.of(caseResult.value));
                    switch (context.toBoolean(eq)) {
                      case TRUE:
                        matchFound = true;
                        matchMore = false;
                        break;
                      case OTHER:
                        return errorCompletion;
                      case FALSE:
                        break;
                    }
                    break;
                  case DefaultCln:
                    matchFound = true;
                    matchMore = true;
                }
                if (matchFound) {
                  matchIndex = i;
                  if (!matchMore) {
                    break search;
                  }
                }
              }
            }
          }
        }

        if (matchIndex >= 0) {
          block_execution_loop:
          for (int i = matchIndex, n = body.getNChildren(); i < n; ++i) {
            BaseNode child = body.getChild(i);
            if (child instanceof SwitchBlockStatementGroupNode) {
              BlockStatementsNode stmts = child.firstChildWithType(
                  BlockStatementsNode.class);
              if (stmts == null) { continue; }
              Locals<VALUE> caseLocals = new Locals<>(locals);
              Completion<VALUE> blockResult = interpret(
                  stmts, caseLocals, null);
              switch (blockResult.kind) {
                case BREAK:
                  if (blockResult.label == null
                      || blockResult.label.equals(parentLabel)) {
                    return nullCompletion;
                  }
                  return blockResult;
                case CONTINUE:
                  if (blockResult.label != null
                      && blockResult.label.equals(parentLabel)) {
                    error(stmts, "continue from labeled switch");
                    return errorCompletion;
                  }
                  return blockResult;
                case NORMAL:
                  if (context.isErrorValue(blockResult.value)) {
                    return blockResult;
                  }
                  // Fall through to next case.
                  continue block_execution_loop;
                case RETURN:
                case THROW:
                  return blockResult;
              }
              throw new AssertionError(blockResult);
            }
          }
        }
        return nullCompletion;
      }
      case SynchronizedStatement:
        break;
      case TemplateComprehension:
        break;
      case TemplateCondition:
        break;
      case TemplateDirective:
        break;
      case TemplateDirectives:
        break;
      case TemplateFormals:
        break;
      case TemplateInterpolation:
        break;
      case TemplateLocal:
        break;
      case TemplateLoop:
        break;
      case TemplatePseudoRoot:
        break;
      case ThrowStatement:
        break;
      case Throws:
        break;
      case TryStatement:
      case TryWithResourcesStatement: {
        ResourceSpecificationNode resources = node.firstChildWithType(
            ResourceSpecificationNode.class);
        BlockNode body = node.firstChildWithType(BlockNode.class);
        FinallyNode finalStmt = node.firstChildWithType(FinallyNode.class);

        Completion<VALUE> result = nullCompletion;

        List<Object> toClose = null;
        Locals<VALUE> tryLocals = locals;
        if (resources != null) {
          toClose = new ArrayList<>();
          tryLocals = new Locals<>(locals);

          ImmutableList<ResourceNode> rs = resources.finder(ResourceNode.class)
              .exclude(ResourceNode.class)
              .find();
          for (ResourceNode r : rs) {
            WholeType type = (WholeType) r.getDeclaredTypeNode();
            VariableDeclaratorIdNode decl = r.firstChildWithType(
                VariableDeclaratorIdNode.class);
            ExpressionNode expr = r.firstChildWithType(ExpressionNode.class);
            if (type == null || decl == null || expr == null) {
              error(r, "Malformed resource specification");
              return errorCompletion;
            }
            Name declName = decl.getDeclaredExpressionName();
            if (declName == null) {
              declName = Name.root(
                  decl.getDeclaredExpressionIdentifier(), Name.Type.AMBIGUOUS);
            }
            StaticType staticType = type.getStaticType();
            if (staticType == null) {
              staticType = context.getTypePool().type(
                  JavaLang.JAVA_LANG_AUTOCLOSEABLE, null, context.getLogger());
            }
            tryLocals.declare(declName, context.coercion(staticType));

            // Continue initializing resources until one fails.
            // If one does fail we still need to close the ones that succeeded
            // and run any finally block.
            if (completedNormallyWithoutError(result)) {
              result = interpret(expr, tryLocals, null);
              if (completedNormallyWithoutError(result)) {
                VALUE resourceValue = tryLocals.set(declName, result.value);
                if (!context.isNullValue(resourceValue)) {
                  toClose.add(staticType);
                  toClose.add(declName);
                  toClose.add(resourceValue);
                }
              }
            }
          }
        }

        if (body != null && completedNormallyWithoutError(result)) {
          result = interpret(body, tryLocals, null);
        }
        if (toClose != null) {
          for (int i = toClose.size() - 3; i >= 0; i -= 3) {
            StaticType resourceType = (StaticType) toClose.get(i);
            Name declName = (Name) toClose.get(i + 1);
            @SuppressWarnings("unchecked")
            VALUE resource = (VALUE) toClose.get(i + 2);

            if (resourceType instanceof ClassOrInterfaceType) {
              TypeInfo ti = ((ClassOrInterfaceType) resourceType).info;
              Optional<CallableInfo> close = findMethod(ti, "close", "()V");
              if (close.isPresent()) {
                VALUE v = context.invokeVirtual(
                    close.get(), resource, ImmutableList.of());
                if (!context.isErrorValue(v)) {
                  continue;
                }
              }
            }
            error(resources, "Failed to close " + declName);
          }
        }
        if (finalStmt != null) {
          Completion<VALUE> finallyResult = interpret(finalStmt, locals, null);
          if (!completedNormallyWithoutError(finallyResult)) {
            result = finallyResult;
          }
        }
        return completedNormallyWithoutError(result) ? nullCompletion : result;
      }
      case Type:
        break;
      case TypeArgument:
        break;
      case TypeArgumentList:
        break;
      case TypeArguments:
        break;
      case TypeArgumentsOrDiamond:
        break;
      case TypeBound:
        break;
      case TypeDeclaration:
        break;
      case TypeImportOnDemandDeclaration:
        break;
      case TypeParameter:
        break;
      case TypeParameterList:
        break;
      case TypeParameterModifier:
        break;
      case TypeParameters:
        break;
      case TypeVariable:
        break;
      case UnannType:
        break;
      case UnaryExpression:
        switch (((UnaryExpressionNode) node).getVariant()) {
          case CastExpression:
          case PostExpression:
          case PreExpression:
            // delegates
            break;
          case PrefixOperatorUnaryExpression: {
            PrefixOperatorNode operator = node.firstChildWithType(
                PrefixOperatorNode.class);
            if (operator == null) {
              error(node, "missing prefix operator");
              return errorCompletion;
            }
            BaseNode operand = rightOperand(node);
            Completion<VALUE> operandResult = interpret(operand, locals, null);
            if (!completedNormallyWithoutError(operandResult)) {
              return operandResult;
            }
            switch (operator.getVariant()) {
              case Bng:
                return normal(context.primitiveLogicalNot(operandResult.value));
              case Dsh:
                return normal(context.primitiveNegation(operandResult.value));
              case Pls:
                return normal(context.primitiveNegation(
                    context.primitiveNegation(operandResult.value)));
              case Tld:
                return normal(context.primitiveBitwiseInvert(
                    operandResult.value));
            }
            break;
          }
          case Primary:
            break;
        }
        break;
      case UnqualifiedClassInstanceCreationExpression: {
        UnqualifiedClassInstanceCreationExpressionNode e =
            (UnqualifiedClassInstanceCreationExpressionNode) node;

        String ctorDescriptor = e.getMethodDescriptor();
        TypeSpecification typeSpecToInstantiate = e.getMethodDeclaringType();
        if (ctorDescriptor == null || typeSpecToInstantiate == null) {
          error(e, "Constructor not resolved");
          return errorCompletion;
        }
        StaticType typeToInstantiate = context.getTypePool().type(
            typeSpecToInstantiate, e.getSourcePosition(), context.getLogger());
        if (!(typeToInstantiate instanceof ClassOrInterfaceType)) {
          error(e, "Cannot resolve type " + typeSpecToInstantiate);
          return errorCompletion;
        }
        TypeInfo typeInfo = ((ClassOrInterfaceType) typeToInstantiate).info;
        Optional<CallableInfo> ctor = callableForType(
            typeInfo, "<init>", ctorDescriptor);
        if (!ctor.isPresent()) {
          error(e, "Cannot resolve constructor " + typeSpecToInstantiate + " "
              + ctorDescriptor);
          return errorCompletion;
        }

        @Nullable ArgumentListNode actuals = node.firstChildWithType(
            ArgumentListNode.class);
        List<VALUE> actualValues = Lists.newArrayList();
        if (actuals != null) {
          for (BaseNode child : actuals.getChildren()) {
            Completion<VALUE> actualResult = interpret(child, locals, null);
            if (completedNormallyWithoutError(actualResult)) {
              actualValues.add(actualResult.value);
            } else {
              return errorCompletion;
            }
          }
        }

        return normal(context.newInstance(ctor.get(), actualValues));
      }
      case UsePrefix:
        break;
      case VariableDeclarator:
        break;
      case VariableDeclaratorId:
        break;
      case VariableDeclaratorList:
        break;
      case VariableInitializer:
        break;
      case VariableInitializerList:
        break;
      case VariableModifier:
        break;
      case WhileStatement: {
        if (node.getNChildren() != 2) {
          error(node, "Malformed do loop " + node);
          return errorCompletion;
        }
        BaseNode expression = node.getChild(0);
        BaseNode body = node.getChild(1);
        return interpretLoop(
            null, expression, null, body, locals, parentLabel);
      }
      case Wildcard:
        break;
      case WildcardBounds:
        break;
    }
    throw new AssertionError(node.getVariant());
  }

private Optional<CallableInfo> findMethod(TypeInfo ti, String name, String desc) {
  TypeInfoResolver r = context.getTypePool().r;
  Optional<MemberInfo> mi = ti.memberMatching(
      r,
      new Predicate<MemberInfo>() {
        @Override
        public boolean apply(MemberInfo m) {
          if (m instanceof CallableInfo
              && Modifier.isPublic(m.modifiers)
              && name.equals(m.canonName.identifier)
              && desc.equals(((CallableInfo) m).getDescriptor())) {
            Optional<TypeInfo> declaringClass = r.resolve(
                m.canonName.getContainingClass());
            if (declaringClass.isPresent()
                && !Modifier.isPublic(declaringClass.get().modifiers)) {
              return false;
            }
            return true;
          }
          return false;
        }
      });
  return mi.isPresent()
      ? Optional.of((CallableInfo) mi.get())
      : Optional.absent();
}

private int debugSteps = 1000;

  private Completion<VALUE> interpretLoop(
      BaseNode forInit, BaseNode expression, BaseNode forUpdate,
      BaseNode statement,
      Locals<VALUE> locals, String parentLabel) {
    if (forInit != null) {
      Completion<VALUE> result = interpret(forInit, locals, null);
      if (!completedNormallyWithoutError(result)) { return result; }
    }
    while (true) {
      if (debugSteps <= 0) { throw new Error(); }
      --debugSteps;
      if (expression != null) {
        Completion<VALUE> result = interpret(expression, locals, null);
        if (!completedNormallyWithoutError(result)) { return result; }
        switch (context.toBoolean(result.value)) {
          case FALSE:
            return nullCompletion;
          case OTHER:
            error(expression, "Invalid condition result: " + result.value);
            return errorCompletion;
          case TRUE:
            break;
        }
      }
      if (statement != null) {
        Completion<VALUE> result = interpret(statement, locals, null);
        switch (result.kind) {
          case BREAK:
            if (result.label == null || result.label.equals(parentLabel)) {
              return nullCompletion;
            } else {
              return result;
            }
          case CONTINUE:
            if (result.label == null || result.label.equals(parentLabel)) {
              break;  // proceed to increment
            } else {
              return result;
            }
          case NORMAL:
            if (context.isErrorValue(result.value)) {
              return result;
            }
            break;
          case RETURN:
          case THROW:
            return result;
        }
      }
      if (forUpdate != null) {
        Completion<VALUE> result = interpret(forUpdate, locals, null);
        if (!completedNormallyWithoutError(result)) { return result; }
      }
    }
  }

  private VALUE buildMultiDimArray(int[] dims, int d, ArrayType arrType) {
    int len = dims[d];
    VALUE arr = context.newArray(arrType.elementType, len);
    if (d + 1 < dims.length) {
      if (arrType.elementType instanceof ArrayType) {
        ArrayType subArrType = (ArrayType) arrType.elementType;
        for (int i = 0; i < len; ++i) {
          context.arraySet(arr, i, buildMultiDimArray(dims, d + 1, subArrType));
        }
      } else {
        return context.errorValue();
      }
    }
    return arr;
  }

  private static BaseNode leftOperand(BaseNode node) {
    int nChildren = node.getNChildren();
    return nChildren == 0 ? null : node.getChild(0);
  }

  private static BaseNode rightOperand(BaseNode node) {
    int nChildren = node.getNChildren();
    return nChildren == 0 ? null : node.getChild(nChildren - 1);
  }

  boolean completedNormallyWithoutError(Completion<VALUE> c) {
    return c.kind == Completion.Kind.NORMAL && !context.isErrorValue(c.value);
  }

  private StaticType stringType;
  boolean ambiguousPlusShouldConcatenate(
      BaseNode lhs, VALUE a, BaseNode rhs, VALUE b) {
    if (stringType == null) {
      stringType = context.getTypePool().type(
          JavaLang.JAVA_LANG_STRING, null, context.getLogger());
    }
    StaticType leftType = typeOf(lhs);
    if (leftType == null) {
      leftType = context.runtimeType(a);
    }
    switch (stringType.assignableFrom(leftType)) {
      case SAME:
      case CONFIRM_SAFE:
        return true;
      case BOX:
      case CONFIRM_CHECKED:
      case CONFIRM_UNCHECKED:
      case CONVERTING_LOSSLESS:
      case CONVERTING_LOSSY:
      case DISJOINT:
      case UNBOX:
        break;
    }
    StaticType rightType = typeOf(rhs);
    if (rightType == null) {
      rightType = context.runtimeType(b);
    }
    switch (stringType.assignableFrom(rightType)) {
      case SAME:
      case CONFIRM_SAFE:
        return true;
      case BOX:
      case CONFIRM_CHECKED:
      case CONFIRM_UNCHECKED:
      case CONVERTING_LOSSLESS:
      case CONVERTING_LOSSY:
      case DISJOINT:
      case UNBOX:
        break;
    }
    return false;
  }

  private static StaticType typeOf(BaseNode node) {
    BaseNode n = node;
    while (true) {
      if (n instanceof Typed) {
        return ((Typed) n).getStaticType();
      }
      if (node.getNChildren() == 1) {
        n = node.getChild(0);
      } else {
        return null;
      }
    }
  }

  private interface LeftHandSideHandler<VALUE, T> {

    T ambiguous(VALUE container, Name fieldName);
    T arrayAccess(VALUE arr, int index);
    T arrayLength(VALUE arr);
    T fieldAccess(FieldInfo fieldInfo, VALUE container);
    T fieldAccess(String name, VALUE container);
    T local(Name localName, Locals<VALUE> locals);

    T error(BaseNode node);
    T error(Completion<VALUE> c);
  }

  private final class AssignLeftHandSideHandler
  implements LeftHandSideHandler<VALUE, Completion<VALUE>> {

    final LazyBinaryOp computeNewValue;
    final Supplier<Completion<VALUE>> rhsSupplier;

    AssignLeftHandSideHandler(
        LazyBinaryOp computeNewValue,
        Supplier<Completion<VALUE>> rhsSupplier) {
      this.computeNewValue = computeNewValue;
      this.rhsSupplier = rhsSupplier;
    }

    @Override
    public Completion<VALUE> ambiguous(VALUE container, Name fieldName) {
      throw new UnsupportedOperationException("TODO");  // TODO
    }

    @Override
    public Completion<VALUE> arrayAccess(VALUE arr, int index) {
      Supplier<Completion<VALUE>> oldValueSupplier =
          new Supplier<Completion<VALUE>>() {
            @Override
            public Completion<VALUE> get() {
              return normal(context.arrayGet(arr, index));
            }
          };
      Completion<VALUE> newValue = computeNewValue.perform(
          oldValueSupplier, rhsSupplier);
      if (!completedNormallyWithoutError(newValue)) {
        return newValue;
      }
      return normal(context.arraySet(arr, index, newValue.value));
    }

    @Override
    public Completion<VALUE> arrayLength(VALUE arr) {
      Interpreter.this.error(null, "Cannot assign array length");
      return errorCompletion;
    }

    @Override
    public Completion<VALUE> fieldAccess(FieldInfo fieldInfo, VALUE container) {
      boolean isStatic = Modifier.isStatic(fieldInfo.modifiers);
      Supplier<Completion<VALUE>> oldValueSupplier =
          new Supplier<Completion<VALUE>>() {
            @Override
            public Completion<VALUE> get() {
              if (isStatic) {
                return normal(context.getStaticField(fieldInfo));
              } else {
                return normal(context.getField(fieldInfo, container));
              }
            }
          };
      Completion<VALUE> newValue = computeNewValue.perform(
          oldValueSupplier, rhsSupplier);
      if (!completedNormallyWithoutError(newValue)) {
        return newValue;
      }
      if (isStatic) {
        return normal(context.setStaticField(fieldInfo, newValue.value));
      } else {
        return normal(context.setField(fieldInfo, container, newValue.value));
      }
    }

    @Override
    public Completion<VALUE> fieldAccess(String fieldName, VALUE container) {
      Interpreter.this.error(
          null, "Cannot assign to dynamic field " + fieldName);
      return errorCompletion;
    }

    @Override
    public Completion<VALUE> local(Name localName, Locals<VALUE> locals) {
      Supplier<Completion<VALUE>> oldValueSupplier =
          new Supplier<Completion<VALUE>>() {
            @Override
            public Completion<VALUE> get() {
              return normal(locals.get(localName, context.errorValue()));
            }
          };
      Completion<VALUE> newValue = computeNewValue.perform(
          oldValueSupplier, rhsSupplier);
      if (!completedNormallyWithoutError(newValue)) {
        return newValue;
      }
      return normal(locals.set(localName, newValue.value));
    }

    @Override
    public Completion<VALUE> error(BaseNode node) {
      return errorCompletion;
    }

    @Override
    public Completion<VALUE> error(Completion<VALUE> c) {
      return completedNormallyWithoutError(c)
          ? errorCompletion : c;
    }
  }


  private final class ReadLeftHandSideHandler
  implements LeftHandSideHandler<VALUE, Completion<VALUE>> {

    @Override
    public Completion<VALUE> ambiguous(VALUE container, Name fieldName) {
      throw new UnsupportedOperationException("TODO");  // TODO
    }

    @Override
    public Completion<VALUE> arrayAccess(VALUE arr, int index) {
      return normal(context.arrayGet(arr, index));
    }

    @Override
    public Completion<VALUE> arrayLength(VALUE arr) {
      return normal(context.from(context.arrayLength(arr)));
    }

    @Override
    public Completion<VALUE> fieldAccess(FieldInfo fieldInfo, VALUE container) {
      if (Modifier.isStatic(fieldInfo.modifiers)) {
        return normal(context.getStaticField(fieldInfo));
      } else {
        return normal(context.getField(fieldInfo, container));
      }
    }

    @Override
    public Completion<VALUE> fieldAccess(String fieldName, VALUE container) {
      return normal(context.getFieldDynamic(fieldName, container));
    }

    @Override
    public Completion<VALUE> local(Name localName, Locals<VALUE> locals) {
      return normal(locals.get(localName, context.errorValue()));
    }

    @Override
    public Completion<VALUE> error(BaseNode node) {
      return errorCompletion;
    }

    @Override
    public Completion<VALUE> error(Completion<VALUE> c) {
      return completedNormallyWithoutError(c)
          ? errorCompletion : c;
    }
  }

  private <T> T withLeftHandSide(
      LeftHandSideNode lhs, Locals<VALUE> locals,
      LeftHandSideHandler<VALUE, T> handler) {
    if (lhs != null && lhs.getNChildren() == 1) {
      BaseNode child = lhs.getChild(0);
      if (child instanceof PrimaryNode) {
        return withLeftHandSide((PrimaryNode) child, locals, handler);
      } else if (child instanceof ExpressionAtomNode) {
        return withLeftHandSide((ExpressionAtomNode) child, locals, handler);
      }
    }
    error(lhs, "invalid LHS");
    return handler.error(lhs);
  }

  private <T> T withLeftHandSide(
      PrimaryNode e,
      Locals<VALUE> locals,
      LeftHandSideHandler<VALUE, T> handler) {
    switch (e.getVariant()) {
      case Ambiguous:
        throw new UnsupportedOperationException("TODO");  // TODO

      case ArrayAccess: {
        if (e.getNChildren() != 2) { break; }
        Completion<VALUE> arr = interpret(leftOperand(e), locals, null);
        if (!completedNormallyWithoutError(arr)) {
          return handler.error(arr);
        }
        Completion<VALUE> idx = interpret(rightOperand(e), locals, null);
        if (!completedNormallyWithoutError(idx)) {
          return handler.error(idx);
        }
        Optional<Integer> iOpt = context.toInt(
            context.coercion(StaticType.T_INT).apply(idx.value));
        if (!iOpt.isPresent()) {
          break;
        }
        int i = iOpt.get();
        if (i < 0 || i >= context.arrayLength(arr.value)) {
          break;
        }
        return handler.arrayAccess(arr.value, i);
      }
      case FieldAccess: {
        if (e.getNChildren() != 2) { break; }
        BaseNode objNode = leftOperand(e);
        StaticType objType = objNode instanceof Typed
            ? ((Typed) objNode).getStaticType()
            : null;

        FieldNameNode nameNode = e.firstChildWithType(FieldNameNode.class);
        IdentifierNode identNode = nameNode.firstChildWithType(
            IdentifierNode.class);
        Name canonName = nameNode.getReferencedExpressionName();

        Completion<VALUE> obj = interpret(objNode, locals, null);
        if (!completedNormallyWithoutError(obj)) {
          return handler.error(obj);
        }

        if (canonName != null) {
          Name className = canonName.getContainingClass();
          String ident = canonName.identifier;
          Optional<TypeInfo> objInfo = context.getTypePool().r.resolve(
              className);
          if (objInfo.isPresent()) {
            Optional<FieldInfo> fi = fieldForType(objInfo.get(), ident, false);
            if (fi.isPresent()) {
              return handler.fieldAccess(fi.get(), obj.value);
            }
          }
        }
        String ident = identNode != null ? identNode.getValue() : null;
        if (objType instanceof ArrayType && "length".equals(ident)) {
          return handler.arrayLength(obj.value);
        }
        if (ident != null) {
          return handler.fieldAccess(ident, obj.value);
        }
        error(e, "Field access failed");
        return handler.error(e);
      }
      default:
        break;
    }
    error(e, "invalid LHS " + e.getVariant());
    return handler.error(e);
  }

  private <T> T withLeftHandSide(
      ExpressionAtomNode e, Locals<VALUE> locals,
      LeftHandSideHandler<VALUE, T> handler) {
    switch (e.getVariant()) {
      case FreeField: {
        FieldNameNode nameNode = e.firstChildWithType(FieldNameNode.class);
        Optional<IdentifierNode> identNode =
            nameNode.finder(IdentifierNode.class).findOne();
        TypeInfo thisType = context.getThisType();
        if (!identNode.isPresent() || thisType == null) {
          error(e, "Malformed free field");
          return handler.error(nameNode);
        }
        String ident = identNode.get().getValue();
        // TODO: cache this.
        Optional<FieldInfo> fieldInfoOpt = fieldForType(thisType, ident, false);
        if (!fieldInfoOpt.isPresent()) {
          // TODO: fall back to outer class.
          error(e, "Missing field " + ident);
          return handler.error(nameNode);
        }
        VALUE thisValue = context.getThisValue(thisType.canonName);

        return handler.fieldAccess(fieldInfoOpt.get(), thisValue);
      }
      case Local: {
        LocalNameNode nameNode = e.firstChildWithType(LocalNameNode.class);
        Name name = null;
        if (nameNode != null) {
          name = nameNode.getReferencedExpressionName();
          if (name == null) {
            IdentifierNode identNode =
                nameNode.firstChildWithType(IdentifierNode.class);
            name = Name.root(identNode.getValue(), Name.Type.AMBIGUOUS);
          }
        }
        if (name == null) {
          error(e, "Malformed local variable use");
          return handler.error(e);
        }
        return handler.local(name, locals);
      }
      default:
        break;
    }
    error(e, "invalid LHS " + e.getVariant());
    return handler.error(e);
  }

  abstract class BinaryOp<OP extends BaseNode> {
    abstract VALUE apply(VALUE left, OP op, VALUE right);

    /**
     * @param left the left operand.
     * @param right the right operand.
     */
    void previewOperands(BaseNode left, BaseNode right) {
      // By default, does nothing.
    }

    Completion<VALUE> apply(
        BaseNode operation, @Nullable Class<OP> operatorType,
        Locals<VALUE> locals) {
      int nChildrenRequired = operatorType == null ? 2 : 3;
      if (operation.getNChildren() != nChildrenRequired) {
        error(operation, "Malformed " + operation.getVariant());
        return errorCompletion;
      }

      BaseNode left = operation.getChild(0);
      BaseNode right = operation.getChild(nChildrenRequired - 1);
      previewOperands(left, right);

      OP op = null;
      if (operatorType != null) {
        BaseNode opNode = operation.getChild(1);
        if (!(operatorType.isInstance(opNode))) {
          error(opNode, "Wrong operator " + opNode);
          return errorCompletion;
        }
        op = operatorType.cast(opNode);
      }

      @SuppressWarnings("synthetic-access")
      Completion<VALUE> leftResult = interpret(left, locals, null);
      if (!completedNormallyWithoutError(leftResult)) {
        return leftResult;
      }
      @SuppressWarnings("synthetic-access")
      Completion<VALUE> rightResult = interpret(right, locals, null);
      if (!completedNormallyWithoutError(rightResult)) {
        return rightResult;
      }

      return normal(apply(leftResult.value, op, rightResult.value));
    }
  }

  abstract class LazyBinaryOp {
    Completion<VALUE> perform(
        Supplier<Completion<VALUE>> left,
        Supplier<Completion<VALUE>> right) {
      Completion<VALUE> leftResult = left.get();
      if (!completedNormallyWithoutError(leftResult)) {
        return leftResult;
      }
      Completion<VALUE> rightResult = right.get();
      if (!completedNormallyWithoutError(rightResult)) {
        return rightResult;
      }
      return normal(perform(leftResult.value, rightResult.value));
    }

    // Should be overridden
    VALUE perform(@SuppressWarnings("unused") VALUE left,
                  @SuppressWarnings("unused") VALUE right) {
      throw new UnsupportedOperationException();
    }
  }

  LazyBinaryOp binaryOpFor(
      BaseNode lhs, AssignmentOperatorNode.Variant v, BaseNode rhs) {
    // TODO: can this switch become a static immutableEnumMap?
    switch (v) {
      case AmpEq:
        return new LazyBinaryOp() {
          @Override VALUE perform(VALUE oldValue, VALUE rhsValue) {
            return context.primitiveAnd(oldValue, rhsValue);
          }
        };
      case DshEq:
        return new LazyBinaryOp() {
          @Override VALUE perform(VALUE oldValue, VALUE rhsValue) {
            return context.primitiveSubtraction(oldValue, rhsValue);
          }
        };
      case Eq:
        return new LazyBinaryOp() {
          @Override Completion<VALUE> perform(
              Supplier<Completion<VALUE>> left,
              Supplier<Completion<VALUE>> right) {
            // Do not compute the left.
            return right.get();
          }
        };
      case FwdEq:
        return new LazyBinaryOp() {
          @Override VALUE perform(VALUE oldValue, VALUE rhsValue) {
            return context.primitiveDivision(oldValue, rhsValue);
          }
        };
      case Gt2Eq:
        return new LazyBinaryOp() {
          @Override VALUE perform(VALUE oldValue, VALUE rhsValue) {
            return context.primitiveRshift(oldValue, rhsValue);
          }
        };
      case Gt3Eq:
        return new LazyBinaryOp() {
          @Override VALUE perform(VALUE oldValue, VALUE rhsValue) {
            return context.primitiveRushift(oldValue, rhsValue);
          }
        };
      case HatEq:
        return new LazyBinaryOp() {
          @Override VALUE perform(VALUE oldValue, VALUE rhsValue) {
            return context.primitiveXor(oldValue, rhsValue);
          }
        };
      case Lt2Eq:
        return new LazyBinaryOp() {
          @Override VALUE perform(VALUE oldValue, VALUE rhsValue) {
            return context.primitiveLshift(oldValue, rhsValue);
          }
        };
     case PctEq:
       return new LazyBinaryOp() {
         @Override VALUE perform(VALUE oldValue, VALUE rhsValue) {
           return context.primitiveModulus(oldValue, rhsValue);
         }
       };
     case PipEq:
       return new LazyBinaryOp() {
         @Override VALUE perform(VALUE oldValue, VALUE rhsValue) {
           return context.primitiveOr(oldValue, rhsValue);
         }
       };
     case PlsEq: {
       return new LazyBinaryOp() {
         @Override VALUE perform(VALUE oldValue, VALUE rhsValue) {
           return (
               ambiguousPlusShouldConcatenate(
                   lhs, oldValue, rhs, rhsValue))
               ? context.stringConcatenation(oldValue, rhsValue)
               : context.primitiveAddition(oldValue, rhsValue);
         }
       };
     }
     case StrEq:
       return new LazyBinaryOp() {
         @Override VALUE perform(VALUE oldValue, VALUE rhsValue) {
           return context.primitiveMultiplication(oldValue, rhsValue);
         }
       };
    }
    throw new AssertionError(v);
  }

  private Optional<FieldInfo> fieldForType(
      TypeInfo info, String ident, boolean publicOnly) {
    Optional<MemberInfo> matching = info.memberMatching(
        context.getTypePool().r,
        new Predicate<MemberInfo>() {
          @Override
          public boolean apply(MemberInfo mi) {
            return mi instanceof FieldInfo
                && ((FieldInfo) mi).canonName.identifier.equals(
                    ident)
                // TODO: does declaring class need to be public?
                && (!publicOnly || Modifier.isPublic(mi.modifiers));
          }
        });
    if (matching.isPresent()) {
      return Optional.of((FieldInfo) matching.get());
    }
    return Optional.absent();
  }

  private Optional<CallableInfo> callableForType(
      TypeSpecification ts, String ident, String descriptor) {
    StaticType t = context.getTypePool().type(ts, null, context.getLogger());
    if (t instanceof ClassOrInterfaceType) {
      ClassOrInterfaceType cit = (ClassOrInterfaceType) t;
      return callableForType(cit.info, ident, descriptor);
    }
    return Optional.absent();
  }

  private Optional<CallableInfo> callableForType(
      TypeInfo info, String ident, String descriptor) {
    Optional<MemberInfo> matching = info.memberMatching(
        context.getTypePool().r,
        new Predicate<MemberInfo>() {
          @Override
          public boolean apply(MemberInfo mi) {
            if (mi instanceof CallableInfo) {
              CallableInfo ci = (CallableInfo) mi;
              return ci.canonName.identifier.equals(ident)
                  && descriptor.equals(ci.getDescriptor());
            }
            return false;
          }
        });
    if (matching.isPresent()) {
      return Optional.of((CallableInfo) matching.get());
    }
    return Optional.absent();
  }
}
