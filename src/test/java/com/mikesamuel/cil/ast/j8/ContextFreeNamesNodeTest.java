package com.mikesamuel.cil.ast.j8;

import static com.mikesamuel.cil.event.Event.content;
import static com.mikesamuel.cil.event.Event.pop;
import static com.mikesamuel.cil.event.Event.push;
import static com.mikesamuel.cil.event.Event.token;

import org.junit.Test;

import com.mikesamuel.cil.ast.AbstractParSerTestCase;

@SuppressWarnings("javadoc")
public final class ContextFreeNamesNodeTest extends AbstractParSerTestCase {

  @Test
  public void testSimpleExpressionName() {
    assertParsePasses(
        J8NodeType.ContextFreeNames,
        "name",
        push(ContextFreeNamesNode.Variant.ContextFreeNameDotContextFreeName),
        push(ContextFreeNameNode.Variant.Name),
        push(IdentifierNode.Variant.Builtin),
        content("name", -1),
        pop(),
        pop(),
        pop());
  }

  @Test
  public void testComplexExpressionName() {
    assertParsePasses(
        J8NodeType.ContextFreeNames,
        "one.two",
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
        pop());
  }

  @Test
  public void testExpressionNameThatHasKeywordAsPrefix() {
    assertParsePasses(
        J8NodeType.ContextFreeNames,
        "donut",
        push(ContextFreeNamesNode.Variant.ContextFreeNameDotContextFreeName),
        push(ContextFreeNameNode.Variant.Name),
        push(IdentifierNode.Variant.Builtin),
        content("donut", -1),
        pop(),
        pop(),
        pop());
  }
}
