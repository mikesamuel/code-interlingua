package com.mikesamuel.cil.ast;

import org.junit.Test;

import com.mikesamuel.cil.ptree.PTree;

@SuppressWarnings("javadoc")
public final class BlockNodeTest extends AbstractParSerTestCase {

  @Test
  public void testParseEmptyBlock() {
    assertParsePasses(
        PTree.complete(NodeType.Block),
        "{}",

        MatchEvent.push(BlockNode.Variant.LcBlockStatementsRc),
        MatchEvent.token("{"),
        MatchEvent.token("}"),
        MatchEvent.pop()
        );
  }

  @Test
  public void testParseBlockNop() {
    assertParsePasses(
        PTree.complete(NodeType.Block),
        "{;}",

        MatchEvent.push(BlockNode.Variant.LcBlockStatementsRc),
        MatchEvent.token("{"),
        MatchEvent.push(
            BlockStatementsNode.Variant.BlockStatementBlockStatement),
        MatchEvent.push(BlockStatementNode.Variant.Statement),
        MatchEvent.push(
            StatementNode.Variant.StatementWithoutTrailingSubstatement),
        MatchEvent.push(
            StatementWithoutTrailingSubstatementNode.Variant.EmptyStatement),
        MatchEvent.push(EmptyStatementNode.Variant.Sem),
        MatchEvent.token(";"),
        MatchEvent.pop(),
        MatchEvent.pop(),
        MatchEvent.pop(),
        MatchEvent.pop(),
        MatchEvent.pop(),
        MatchEvent.token("}"),
        MatchEvent.pop()
        );
  }

  @Test
  public void testParseEmptyBlockBlock() {
    assertParsePasses(
        PTree.complete(NodeType.Block),
        "{ { } } // block block",

        MatchEvent.push(BlockNode.Variant.LcBlockStatementsRc),
        MatchEvent.token("{"),
        MatchEvent.token("}"),
        MatchEvent.pop()
        );
  }

}
