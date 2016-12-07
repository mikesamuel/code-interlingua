package com.mikesamuel.cil.ast;

import static com.mikesamuel.cil.event.Event.pop;
import static com.mikesamuel.cil.event.Event.push;
import static com.mikesamuel.cil.event.Event.token;

import org.junit.Test;

import com.mikesamuel.cil.ptree.PTree;

@SuppressWarnings("javadoc")
public final class BlockNodeTest extends AbstractParSerTestCase {

  @Test
  public void testParseEmptyBlock() {
    assertParsePasses(
        PTree.complete(NodeType.Block),
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
        PTree.complete(NodeType.Block),
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
        PTree.complete(NodeType.Block),
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
