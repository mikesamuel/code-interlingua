package com.mikesamuel.cil.expr;

import javax.annotation.Nullable;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
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
import com.mikesamuel.cil.ast.BooleanLiteralNode;
import com.mikesamuel.cil.ast.ContextFreeNameNode;
import com.mikesamuel.cil.ast.DimExprsNode;
import com.mikesamuel.cil.ast.EqualityExpressionNode;
import com.mikesamuel.cil.ast.EqualityOperatorNode;
import com.mikesamuel.cil.ast.ExpressionAtomNode;
import com.mikesamuel.cil.ast.FieldNameNode;
import com.mikesamuel.cil.ast.IdentifierNode;
import com.mikesamuel.cil.ast.IncrDecrOperatorNode;
import com.mikesamuel.cil.ast.LeftHandSideNode;
import com.mikesamuel.cil.ast.MethodNameNode;
import com.mikesamuel.cil.ast.MultiplicativeExpressionNode;
import com.mikesamuel.cil.ast.MultiplicativeOperatorNode;
import com.mikesamuel.cil.ast.NodeType;
import com.mikesamuel.cil.ast.PrefixOperatorNode;
import com.mikesamuel.cil.ast.PrimaryNode;
import com.mikesamuel.cil.ast.RelationalExpressionNode;
import com.mikesamuel.cil.ast.RelationalOperatorNode;
import com.mikesamuel.cil.ast.UnaryExpressionNode;
import com.mikesamuel.cil.ast.UnqualifiedClassInstanceCreationExpressionNode;
import com.mikesamuel.cil.ast.VariableInitializerListNode;
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
import com.mikesamuel.cil.ast.meta.TypeSpecification;
import com.mikesamuel.cil.ast.traits.Typed;
import com.mikesamuel.cil.ast.traits.WholeType;
import com.mikesamuel.cil.parser.SourcePosition;
import com.mikesamuel.cil.ptree.Tokens;

import static com.mikesamuel.cil.expr.Completion.normal;

import java.lang.reflect.Modifier;
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

  Interpreter(InterpretationContext<VALUE> context) {
    this.context = context;
    nullCompletion = Completion.normal(context.nullValue());
    errorCompletion = Completion.normal(context.errorValue());
  }

  void error(BaseNode node, String message) {
    SourcePosition pos = node != null ? node.getSourcePosition() : null;
    String fullMessage = pos != null ? pos + ": " + message : message;
    context.getLogger().log(Level.SEVERE, fullMessage);
  }

  /** Interprets the given AST fragment. */
  public Completion<VALUE> interpret(BaseNode node) {
    return interpret(node, new Locals<>(), null);
  }

  /**
   * Interprets the given AST fragment.
   *
   * @param locals used to resolve free variables.
   */
  public Completion<VALUE> interpret(BaseNode node, Locals<VALUE> locals) {
    return interpret(node, locals, null);
  }

  private static final ImmutableSet<NodeType> NONSPECIFIC_DELEGATES =
      Sets.immutableEnumSet(NodeType.FieldName, NodeType.TypeName);

  /**
   * @param parentLabel the label of the containing block so that interpretation
   *     can properly handle continues.
   */
  @SuppressWarnings("synthetic-access")
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
    NodeType nodeType = node.getNodeType();
    switch (nodeType) {
      case AdditionalBound:
      case AdditiveOperator:
      case AmbiguousBinaryUnaryOperator:
      case AssignmentOperator:
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
              System.err.println("arrType=" + arrType + ", dimExprsNode=" + dimExprsNode);
              // TODO
              return errorCompletion;
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
      case BasicForStatement:
        break;
      case Block:
        break;
      case BlockStatement:
        break;
      case BlockStatements:
        break;
      case BlockTypeScope:
        break;
      case BooleanLiteral:
        switch (((BooleanLiteralNode) node).getVariant()) {
          case False: return normal(context.from(false));
          case True: return normal(context.from(true));
        }
        break;
      case BreakStatement:
        break;
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
      case ClassDeclaration:
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
        break;
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
            } else {
              StaticType rt = context.runtimeType(result.value);
              if (StaticType.ERROR_TYPE.equals(rt)) {
                return errorCompletion;
              }
              if (rt instanceof PrimitiveType) {
                rt = context.getTypePool().type(
                    new TypeSpecification(((PrimitiveType) rt).wrapperType),
                    child.getSourcePosition(), context.getLogger());
              }
              Optional<FieldInfo> field = fieldForType(rt, ident, true);
              if (field.isPresent()) {
                v = context.getField(field.get(), result.value);
              } else if ("length".equals(ident) && rt instanceof ArrayType) {
                // Special case : array.length
                v = context.from(context.arrayLength(result.value));
              } else {
                error(node, "cannot find field " + ident);
                return errorCompletion;
              }
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
      case ContinueStatement:
        break;
      case ConvertCast:
        break;
      case DefaultValue:
        break;
      case Diamond:
        break;
      case Dim:
        break;
      case DimExpr:
        break;
      case DimExprs:
        break;
      case Dims:
        break;
      case DoStatement:
        break;
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
      case EmptyStatement:
        break;
      case EnhancedForStatement:
        break;
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
        break;
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
      case IfStatement:
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
      case LabeledStatement:
        break;
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
      case LocalVariableDeclaration:
        break;
      case LocalVariableDeclarationStatement:
        break;
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
      case ReturnStatement:
        break;
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
      case StatementExpressionList:
        break;
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
        break;
      case SwitchLabels:
        break;
      case SwitchStatement:
        break;
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
      case TemplateInterpolation:
        break;
      case TemplateLoop:
        break;
      case ThrowStatement:
        break;
      case Throws:
        break;
      case TryStatement:
        break;
      case TryWithResourcesStatement:
        break;
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
      case WhileStatement:
        break;
      case Wildcard:
        break;
      case WildcardBounds:
        break;
    }
    throw new AssertionError(node.getVariant());
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
    T local(Name localName);

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
    public Completion<VALUE> local(Name localName) {
      throw new UnsupportedOperationException("TODO");  // TODO
    }

    @Override
    public Completion<VALUE> error(BaseNode node) {
      return errorCompletion;
    }

    @Override
    public Completion<VALUE> error(Completion<VALUE> c) {
      return c;
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
    public Completion<VALUE> local(Name localName) {
      throw new UnsupportedOperationException("TODO");  // TODO
    }

    @Override
    public Completion<VALUE> error(BaseNode node) {
      return errorCompletion;
    }

    @Override
    public Completion<VALUE> error(Completion<VALUE> c) {
      return c;
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
        throw new UnsupportedOperationException("TODO");

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
        } else if (objType instanceof ArrayType && identNode != null
                   && "length".equals(identNode.getValue())) {
          return handler.arrayLength(obj.value);
        }
        error(objNode, "Field access failed");
        return handler.error(obj);
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
      case Local:
        throw new UnsupportedOperationException("TODO");
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
      StaticType rt, String ident, boolean publicOnly) {
    if (rt instanceof ClassOrInterfaceType) {
      ClassOrInterfaceType cit = (ClassOrInterfaceType) rt;
      return fieldForType(cit.info, ident, publicOnly);
    }
    return Optional.absent();
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
