package com.mikesamuel.cil.ast;

import static com.mikesamuel.cil.event.Event.content;
import static com.mikesamuel.cil.event.Event.pop;
import static com.mikesamuel.cil.event.Event.push;
import static com.mikesamuel.cil.event.Event.token;

import org.junit.Test;

import com.mikesamuel.cil.ptree.PTree;

@SuppressWarnings("javadoc")
public final class ExpressionNameNodeTest extends AbstractParSerTestCase {

  @Test
  public void testSimpleExpressionName() {
    assertParsePasses(
        PTree.complete(NodeType.ExpressionName),
        "name",
        push(ExpressionNameNode.Variant.NotAtContextFreeNames),
        push(ContextFreeNamesNode.Variant.ContextFreeNameDotContextFreeName),
        push(ContextFreeNameNode.Variant.Name),
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
        push(ContextFreeNameNode.Variant.Name),
        push(IdentifierNode.Variant.Builtin),
        content("one", -1),
        pop(),
        pop(),
        token(".", -1),
        push(ContextFreeNameNode.Variant.Name),
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
        push(ContextFreeNameNode.Variant.Name),
        push(IdentifierNode.Variant.Builtin),
        content("donut", -1),
        pop(),
        pop(),
        pop(),
        pop());
  }
}
