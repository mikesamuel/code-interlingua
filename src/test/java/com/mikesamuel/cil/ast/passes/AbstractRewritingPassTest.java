package com.mikesamuel.cil.ast.passes;

import javax.annotation.Nullable;

import org.junit.Test;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.mikesamuel.cil.ast.BaseNode;
import com.mikesamuel.cil.ast.CompilationUnitNode;
import com.mikesamuel.cil.ast.IdentifierNode;
import com.mikesamuel.cil.ast.InterfaceTypeListNode;
import com.mikesamuel.cil.ast.NodeType;
import com.mikesamuel.cil.ast.Trees;
import com.mikesamuel.cil.format.FormattedSource;
import com.mikesamuel.cil.parser.Input;
import com.mikesamuel.cil.parser.LeftRecursion;
import com.mikesamuel.cil.parser.ParSer;
import com.mikesamuel.cil.parser.ParseErrorReceiver;
import com.mikesamuel.cil.parser.ParseResult;
import com.mikesamuel.cil.parser.ParseState;
import com.mikesamuel.cil.parser.SList;
import com.mikesamuel.cil.parser.SerialErrorReceiver;
import com.mikesamuel.cil.parser.SerialState;
import com.mikesamuel.cil.parser.Unparse;
import com.mikesamuel.cil.ptree.PTree;

import junit.framework.TestCase;

@SuppressWarnings("javadoc")
public final class AbstractRewritingPassTest extends TestCase {

  @Test
  public void testInsertions() throws Exception {
    assertRewrite(
        "class Foo implements A, B, C, D, E {}",
        "class Foo implements A, B,    D, E {}",
        new AbstractRewritingPass() {
          @Override
          protected <N extends BaseNode> ProcessingStatus
          postvisit(N node, @Nullable SList<Parent> pathFromRoot,
              BaseNode.Builder<N, ?> builder) {
            switch (node.getNodeType()) {
              case ContextFreeName:
                // If we see the name D, turn it into a C via the builder.
                String name = node.getTextContent(".");
                if (name.equals("D")) {
                  ((BaseNode.InnerBuilder<N, ?>) builder).replace(
                      0,
                      IdentifierNode.Variant.Builtin.nodeBuilder()
                          .leaf("C")
                          .build());
                }
                break;
              case InterfaceType:
                if (builder.changed()) {
                  // If some descendant changed, then insert the built node
                  // before the original.
                  return ProcessingStatus.replace(builder.build(), node);
                }
                break;
              default:
                break;
            }
            return ProcessingStatus.CONTINUE;
          }
        },
        null);
  }

  @Test
  public void testReplace() throws Exception {
    assertRewrite(
        "class Foo implements G, G, C, G, G {}",
        "class Foo implements A, B, C, D, E {}",
        new AbstractRewritingPass() {
          @Override
          protected <N extends BaseNode> ProcessingStatus
          previsit(N node, @Nullable SList<Parent> pathFromRoot,
              BaseNode.Builder<N, ?> builder) {
            switch (node.getNodeType()) {
              case Identifier:
                String ident = node.getValue();
                if (ident.length() == 1 && !"C".equals(ident)) {
                  return ProcessingStatus.replace(
                      ((IdentifierNode) node).builder().leaf("G").build());
                }
                break;
              default:
                break;
            }
            return ProcessingStatus.CONTINUE;
          }
        },
        null);
  }

  @Test
  public void testRemove() throws Exception {
    assertRewrite(
        "class Foo implements A, B, D, E {}",
        "class Foo implements A, B, C, D, E {}",
        new AbstractRewritingPass() {
          @Override
          protected <N extends BaseNode> ProcessingStatus
          previsit(N node, @Nullable SList<Parent> pathFromRoot,
              BaseNode.Builder<N, ?> builder) {
            switch (node.getNodeType()) {
              case InterfaceType:
                if ("C".equals(node.getTextContent("^"))) {
                  return ProcessingStatus.REMOVE;
                }
                break;
              default:
                break;
            }
            return ProcessingStatus.CONTINUE;
          }
        },
        null);
  }

  @Test
  public void testRemoveInPost() throws Exception {
    assertRewrite(
        "class Foo implements A, B, D, E {}",
        "class Foo implements A, B, C, D, E {}",
        new AbstractRewritingPass() {
          @Override
          protected <N extends BaseNode> ProcessingStatus
          postvisit(N node, @Nullable SList<Parent> pathFromRoot,
              BaseNode.Builder<N, ?> builder) {
            switch (node.getNodeType()) {
              case InterfaceType:
                if ("C".equals(node.getTextContent("^"))) {
                  return ProcessingStatus.REMOVE;
                }
                break;
              default:
                break;
            }
            return ProcessingStatus.CONTINUE;
          }
        },
        null);
  }

  @Test
  public void testOutOfBandChangesAndContinue() throws Exception {
    boolean threw;
    try {
      assertRewrite(
          "NEVER COMPARED TO EXPECTED VALUE",
          "class Foo implements A, B, C, D, E {}",
          new AbstractRewritingPass() {
            @Override
            protected <N extends BaseNode> ProcessingStatus
            previsit(N node, @Nullable SList<Parent> pathFromRoot,
                BaseNode.Builder<N, ?> builder) {
              switch (node.getNodeType()) {
                case InterfaceTypeList:
                  ((InterfaceTypeListNode.Builder) builder).remove(1);
                  break;
                default:
                  break;
              }
              return ProcessingStatus.CONTINUE;
            }
          },
          null);
      threw = false;
    } catch (@SuppressWarnings("unused") IllegalStateException ex) {
      threw = true;
    }
    assertTrue(threw);
  }

  @Test
  public void testOutOfBandChangesAndBreak() throws Exception {
    assertRewrite(
        "class Foo implements A, C, D, E {}",
        "class Foo implements A, B, C, D, E {}",
        new AbstractRewritingPass() {
          @Override
          protected <N extends BaseNode> ProcessingStatus
          previsit(N node, @Nullable SList<Parent> pathFromRoot,
              BaseNode.Builder<N, ?> builder) {
            switch (node.getNodeType()) {
              case InterfaceTypeList:
                ((InterfaceTypeListNode.Builder) builder).remove(1);
                return ProcessingStatus.BREAK;
              default:
                break;
            }
            return ProcessingStatus.CONTINUE;
          }
        },
        null);
  }

  @Test
  public void testOutOfBandChangesAndContinueFromPost() throws Exception {
    assertRewrite(
        "class Foo implements A, C, D, E {}",
        "class Foo implements A, B, C, D, E {}",
        new AbstractRewritingPass() {
          @Override
          protected <N extends BaseNode> ProcessingStatus
          postvisit(N node, @Nullable SList<Parent> pathFromRoot,
              BaseNode.Builder<N, ?> builder) {
            switch (node.getNodeType()) {
              case InterfaceTypeList:
                ((InterfaceTypeListNode.Builder) builder).remove(1);
                break;
              default:
                break;
            }
            return ProcessingStatus.CONTINUE;
          }
        },
        null);
  }

  void assertRewrite(
      String want,
      String input,
      AbstractRewritingPass pass,
      @Nullable Trees.Decorator decorator)
  throws Exception {
    ParSer ps = PTree.complete(NodeType.CompilationUnit).getParSer();
    ParseState start = new ParseState(
        Input.builder().source(getName()).code(input).build());
    ParseResult result =
        ps.parse(start, new LeftRecursion(), ParseErrorReceiver.DEV_NULL);
    assertEquals(ParseResult.Synopsis.SUCCESS, result.synopsis);
    CompilationUnitNode cu = (CompilationUnitNode) Trees.of(result.next());
    StringBuilder sb = new StringBuilder();
    for (CompilationUnitNode out : pass.run(ImmutableList.of(cu))) {
      Optional<SerialState> ser = ps.unparse(
          new SerialState(SList.forwardIterable(
              Trees.startUnparse(null, out, decorator))),
          SerialErrorReceiver.DEV_NULL);
      if (!ser.isPresent()) {
        fail(out.toAsciiArt("", null));
      }
      Unparse.Verified verified = Unparse.verify(
          SList.forwardIterable(ser.get().output));
      if (sb.length() != 0) {
        sb.append("\n\n");
      }
      FormattedSource formatted = Unparse.format(verified);
      sb.append(formatted.code);
    }
    String got = sb.toString();
    assertEquals(want, got);
  }

}
