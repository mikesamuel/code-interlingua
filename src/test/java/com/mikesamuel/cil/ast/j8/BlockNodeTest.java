package com.mikesamuel.cil.ast.j8;

import static com.mikesamuel.cil.event.Event.pop;
import static com.mikesamuel.cil.event.Event.push;
import static com.mikesamuel.cil.event.Event.token;

import org.junit.Test;

import com.mikesamuel.cil.ast.AbstractParSerTestCase;

@SuppressWarnings("javadoc")
public final class BlockNodeTest extends AbstractParSerTestCase {

  @Test
  public void testParseEmptyBlock() {
    assertParsePasses(
        J8NodeType.Block,
        "{}",

        push(BlockNode.Variant.LcBlockStatementsRc),
        token("{", -1),
        token("}", -1),
        pop()
        );
  }

  @Test
  public void testParseBlockNop() {
    assertParsePasses(
        J8NodeType.Block,
        "{;}",

        push(BlockNode.Variant.LcBlockStatementsRc),
        token("{", -1),
        push(BlockStatementsNode.Variant
            .BlockStatementBlockStatementBlockTypeScope),
        push(BlockStatementNode.Variant.Statement),
        push(StatementNode.Variant.EmptyStatement),
        push(EmptyStatementNode.Variant.Sem),
        token(";", -1),
        pop(),
        pop(),
        pop(),
        pop(),
        token("}", -1),
        pop()
        );
  }

  @Test
  public void testParseEmptyBlockBlock() {
    assertParsePasses(
        J8NodeType.Block,
        "{ { } } // block block",

        push(BlockNode.Variant.LcBlockStatementsRc),
        token("{", -1),
        push(BlockStatementsNode.Variant
            .BlockStatementBlockStatementBlockTypeScope),
        push(BlockStatementNode.Variant.Statement),
        push(StatementNode.Variant.Block),
        push(BlockNode.Variant.LcBlockStatementsRc),
        token("{", -1),
        token("}", -1),
        pop(),
        pop(),
        pop(),
        pop(),
        token("}", -1),
        pop()
        );
  }
}
