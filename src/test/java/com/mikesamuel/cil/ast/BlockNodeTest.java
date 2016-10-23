package com.mikesamuel.cil.ast;

import org.junit.Test;

import com.mikesamuel.cil.ptree.PTree;
import static com.mikesamuel.cil.ast.MatchEvent.pop;
import static com.mikesamuel.cil.ast.MatchEvent.push;
import static com.mikesamuel.cil.ast.MatchEvent.token;

@SuppressWarnings("javadoc")
public final class BlockNodeTest extends AbstractParSerTestCase {

  @Test
  public void testParseEmptyBlock() {
    assertParsePasses(
        PTree.complete(NodeType.Block),
        "{}",

        push(BlockNode.Variant.LcBlockStatementsRc),
        token("{"),
        token("}"),
        pop()
        );
  }

  @Test
  public void testParseBlockNop() {
    assertParsePasses(
        PTree.complete(NodeType.Block),
        "{;}",

        push(BlockNode.Variant.LcBlockStatementsRc),
        token("{"),
        push(
            BlockStatementsNode.Variant.BlockStatementBlockStatement),
        push(BlockStatementNode.Variant.Statement),
        push(
            StatementNode.Variant.StatementWithoutTrailingSubstatement),
        push(
            StatementWithoutTrailingSubstatementNode.Variant.EmptyStatement),
        push(EmptyStatementNode.Variant.Sem),
        token(";"),
        pop(),
        pop(),
        pop(),
        pop(),
        pop(),
        token("}"),
        pop()
        );
  }

  @Test
  public void testParseEmptyBlockBlock() {
    assertParsePasses(
        PTree.complete(NodeType.Block),
        "{ { } } // block block",

        push(BlockNode.Variant.LcBlockStatementsRc),
        token("{"),
        push(BlockStatementsNode.Variant.BlockStatementBlockStatement),
        push(BlockStatementNode.Variant.Statement),
        push(StatementNode.Variant.StatementWithoutTrailingSubstatement),
        push(StatementWithoutTrailingSubstatementNode.Variant.Block),
        push(BlockNode.Variant.LcBlockStatementsRc),
        token("{"),
        token("}"),
        pop(),
        pop(),
        pop(),
        pop(),
        pop(),
        token("}"),
        pop()
        );
  }
}
