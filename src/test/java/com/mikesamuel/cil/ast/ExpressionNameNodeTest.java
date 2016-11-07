package com.mikesamuel.cil.ast;

import org.junit.Test;

import com.mikesamuel.cil.ptree.PTree;
import static com.mikesamuel.cil.ast.MatchEvent.content;
import static com.mikesamuel.cil.ast.MatchEvent.pop;
import static com.mikesamuel.cil.ast.MatchEvent.push;
import static com.mikesamuel.cil.ast.MatchEvent.token;

@SuppressWarnings("javadoc")
public final class ExpressionNameNodeTest extends AbstractParSerTestCase {

  @Test
  public void testSimpleExpressionName() {
    assertParsePasses(
        PTree.complete(NodeType.ExpressionName),
        "name",
        push(ExpressionNameNode.Variant.NotAtContextFreeNames),
        push(ContextFreeNamesNode.Variant.ContextFreeNameDotContextFreeName),
        push(ContextFreeNameNode.Variant.AnnotationIdentifierTypeArgumentsOrDiamond),
        push(IdentifierNode.Variant.Builtin),
        content("name", -1),
        pop(),
        pop(),
        pop(),
        pop());
  }

  @Test
  public void testComplexExpressionName() {
    assertParsePasses(
        PTree.complete(NodeType.ExpressionName),
        "one.two",
        push(ExpressionNameNode.Variant.NotAtContextFreeNames),
        push(ContextFreeNamesNode.Variant.ContextFreeNameDotContextFreeName),
        push(ContextFreeNameNode.Variant.AnnotationIdentifierTypeArgumentsOrDiamond),
        push(IdentifierNode.Variant.Builtin),
        content("one", -1),
        pop(),
        pop(),
        token(".", -1),
        push(ContextFreeNameNode.Variant.AnnotationIdentifierTypeArgumentsOrDiamond),
        push(IdentifierNode.Variant.Builtin),
        content("two", -1),
        pop(),
        pop(),
        pop(),
        pop());
  }

  @Test
  public void testExpressionNameThatHasKeywordAsPrefix() {
    assertParsePasses(
        PTree.complete(NodeType.ExpressionName),
        "donut",
        push(ExpressionNameNode.Variant.NotAtContextFreeNames),
        push(ContextFreeNamesNode.Variant.ContextFreeNameDotContextFreeName),
        push(ContextFreeNameNode.Variant.AnnotationIdentifierTypeArgumentsOrDiamond),
        push(IdentifierNode.Variant.Builtin),
        content("donut", -1),
        pop(),
        pop(),
        pop(),
        pop());
  }
}
