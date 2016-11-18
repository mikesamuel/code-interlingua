package com.mikesamuel.cil.ast;

import static com.mikesamuel.cil.event.MatchEvent.content;
import static com.mikesamuel.cil.event.MatchEvent.pop;
import static com.mikesamuel.cil.event.MatchEvent.push;
import static com.mikesamuel.cil.event.MatchEvent.token;

import org.junit.Test;

@SuppressWarnings("javadoc")
public final class StatementExpressionNodeTest extends AbstractParSerTestCase {

  @Test
  public final void testAssignment() {
    assertParsePasses(
        NodeType.StatementExpression,
        "a = b",
        push(StatementExpressionNode.Variant.Assignment),
        push(AssignmentNode.Variant.LeftHandSideAssignmentOperatorExpression),

        push(LeftHandSideNode.Variant.Ambiguous),
        push(PrimaryNode.Variant.Ambiguous),
        push(ContextFreeNamesNode.Variant.ContextFreeNameDotContextFreeName),
        push(ContextFreeNameNode.Variant.Name),
        push(IdentifierNode.Variant.Builtin),
        content("a", 0),
        pop(),
        pop(),
        pop(),
        pop(),
        pop(),

        push(AssignmentOperatorNode.Variant.Eq),
        token("=", 2),
        pop(),

        push(ExpressionNode.Variant.ConditionalExpression),
        push(PrimaryNode.Variant.Ambiguous),
        push(ContextFreeNamesNode.Variant.ContextFreeNameDotContextFreeName),
        push(ContextFreeNameNode.Variant.Name),
        push(IdentifierNode.Variant.Builtin),
        content("b", 4),
        pop(),
        pop(),
        pop(),
        pop(),
        pop(),

        pop(),
        pop()
        );
  }

  @Test
  public final void testAssignmentToFieldOfThis() {
    parseSanityCheck(
        ExpressionNode.Variant.Assignment,
        "this.x = x");
  }

  @Test
  public final void testClassInstanceCreationExpression() {
    assertParsePasses(
        NodeType.StatementExpression,
        "new Foo()",

        push(StatementExpressionNode.Variant.ClassInstanceCreationExpression),
        push(ClassInstanceCreationExpressionNode.Variant.UnqualifiedClassInstanceCreationExpression),
        push(ExpressionAtomNode.Variant.UnqualifiedClassInstanceCreationExpression),
        push(UnqualifiedClassInstanceCreationExpressionNode.Variant.NewTypeArgumentsClassOrInterfaceTypeToInstantiateLpArgumentListRpClassBody),
        token("new", -1),
        push(ClassOrInterfaceTypeToInstantiateNode.Variant.AnnotationIdentifierDotAnnotationIdentifierTypeArgumentsOrDiamond),
        push(IdentifierNode.Variant.Builtin),
        content("Foo", -1),
        pop(),
        pop(),
        token("(", -1),
        token(")", -1),
        pop(),
        pop(),
        pop(),
        pop()
        );
  }

  @Test
  public final void testMethodInvocation() {
    assertParsePasses(
        NodeType.StatementExpression,
        "foo()",

        push(StatementExpressionNode.Variant.MethodInvocation),
        push(MethodInvocationNode.Variant.ImplicitCallee),
        push(ExpressionAtomNode.Variant.MethodInvocation),
        push(MethodNameNode.Variant.Identifier),
        push(IdentifierNode.Variant.Builtin),
        content("foo", -1),
        pop(),
        pop(),
        token("(", -1),
        token(")", -1),
        pop(),
        pop(),
        pop()
        );
  }

  @Test
  public final void testPostExpression() {
    assertParsePasses(
        NodeType.StatementExpression,
        "a++",

        push(StatementExpressionNode.Variant.PostExpression),
        push(PostExpressionNode.Variant.LeftHandSideExpressionIncrDecrOperator),
        push(LeftHandSideNode.Variant.Ambiguous),
        push(PrimaryNode.Variant.Ambiguous),
        push(ContextFreeNamesNode.Variant.ContextFreeNameDotContextFreeName),
        push(ContextFreeNameNode.Variant.Name),
        push(IdentifierNode.Variant.Builtin),
        content("a", -1),
        pop(),
        pop(),
        pop(),
        pop(),
        pop(),
        push(IncrDecrOperatorNode.Variant.PlsPls),
        token("++", -1),
        pop(),
        pop(),
        pop()
        );
  }

  @Test
  public final void testPreExpression() {
    assertParsePasses(
        NodeType.StatementExpression,
        "--a",

        push(StatementExpressionNode.Variant.PreExpression),
        push(PreExpressionNode.Variant.IncrDecrOperatorLeftHandSideExpression),
        push(IncrDecrOperatorNode.Variant.DshDsh),
        token("--", -1),
        pop(),
        push(LeftHandSideNode.Variant.Ambiguous),
        push(PrimaryNode.Variant.Ambiguous),
        push(ContextFreeNamesNode.Variant.ContextFreeNameDotContextFreeName),
        push(ContextFreeNameNode.Variant.Name),
        push(IdentifierNode.Variant.Builtin),
        content("a", -1),
        pop(),
        pop(),
        pop(),
        pop(),
        pop(),
        pop(),
        pop()
        );
  }

  @Test
  public final void testCallIndirectlyViaThis() {
    assertParsePasses(
        NodeType.StatementExpression,
        "this.field.method(arg)",
        /**/push(StatementExpressionNode.Variant.MethodInvocation),
        /*..*/push(MethodInvocationNode.Variant.ExplicitCallee),
        /*....*/push(PrimaryNode.Variant.MethodInvocation),
        /*......*/push(PrimaryNode.Variant.FieldAccess),
        /*........*/push(ExpressionAtomNode.Variant.This),
        /*..........*/token("this", -1),
        /*........*/pop(),
        /*........*/token(".", -1),
        /*........*/push(FieldNameNode.Variant.Identifier),
        /*..........*/push(IdentifierNode.Variant.Builtin),
        /*............*/content("field", -1),
        /*..........*/pop(),
        /*........*/pop(),
        /*......*/pop(),
        /*......*/token(".", -1),
        /*......*/push(MethodNameNode.Variant.Identifier),
        /*........*/push(IdentifierNode.Variant.Builtin),
        /*..........*/content("method", -1),
        /*........*/pop(),
        /*......*/pop(),
        /*......*/token("(", -1),
        /*......*/push(ArgumentListNode.Variant.ExpressionComExpression),
        /*........*/push(ExpressionNode.Variant.ConditionalExpression),
        /*..........*/push(PrimaryNode.Variant.Ambiguous),
        /*............*/push(ContextFreeNamesNode.Variant.ContextFreeNameDotContextFreeName),
        /*..............*/push(ContextFreeNameNode.Variant.Name),
        /*................*/push(IdentifierNode.Variant.Builtin),
        /*..................*/content("arg", -1),
        /*................*/pop(),
        /*..............*/pop(),
        /*............*/pop(),
        /*..........*/pop(),
        /*........*/pop(),
        /*......*/pop(),
        /*......*/token(")", -1),
        /*....*/pop(),
        /*..*/pop(),
        /**/pop()
        );
  }
}

