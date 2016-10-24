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
        push(ExpressionNameNode.Variant.Identifier),
        push(IdentifierNode.Variant.Builtin),
        content("name"),
        pop(),
        pop());
  }

  @Test
  public void testComplexExpressionName() {
    assertParsePasses(
        PTree.complete(NodeType.ExpressionName),
        "one.two",
        push(ExpressionNameNode.Variant.ExpressionNameDotIdentifierNotLp),
        push(ExpressionNameNode.Variant.Identifier),
        push(IdentifierNode.Variant.Builtin),
        content("one"),
        pop(),
        pop(),
        token("."),
        push(IdentifierNode.Variant.Builtin),
        content("two"),
        pop(),
        pop());
  }

}
