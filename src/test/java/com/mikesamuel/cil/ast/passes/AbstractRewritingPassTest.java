package com.mikesamuel.cil.ast.passes;

import java.util.List;
import java.util.logging.Logger;

import javax.annotation.Nullable;

import org.junit.Test;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.mikesamuel.cil.ast.BaseInnerNode;
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

  Logger logger = Logger.getAnonymousLogger();
  {
    logger.setUseParentHandlers(false);
  }

  @Test
  public void testInsertions() throws Exception {
    assertRewrite(
        "class Foo implements A, B, C, D, E {}",
        "class Foo implements A, B,    D, E {}",
        new AbstractRewritingPass(logger) {
          final List<BaseNode> stack = Lists.newArrayList();

          @Override
          protected ProcessingStatus previsit(
              BaseNode node, @Nullable SList<Parent> pathFromRoot) {
            stack.add(node.deepClone());
            return ProcessingStatus.CONTINUE;
          }

          @Override
          protected ProcessingStatus postvisit(
              BaseNode node, @Nullable SList<Parent> pathFromRoot) {
            BaseNode original = stack.remove(stack.size() - 1);
            boolean changed = !original.equals(node);

            switch (node.getNodeType()) {
              case ContextFreeName:
                // If we see the name D, turn it into a C via the builder.
                String name = node.getTextContent(".");
                if (name.equals("D")) {
                  ((BaseInnerNode) node).replace(
                      0,
                      IdentifierNode.Variant.Builtin.buildNode("C"));
                }
                break;
              case InterfaceType:
                if (changed) {
                  // If some descendant changed, then insert the built node
                  // before the original.
                  return ProcessingStatus.replace(node, original);
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
        new AbstractRewritingPass(logger) {
          @Override
          protected ProcessingStatus previsit(
              BaseNode node, @Nullable SList<Parent> pathFromRoot) {
            switch (node.getNodeType()) {
              case Identifier:
                String ident = node.getValue();
                if (ident.length() == 1 && !"C".equals(ident)) {
                  IdentifierNode copy = new IdentifierNode(
                      (IdentifierNode) node);
                  copy.setValue("G");
                  return ProcessingStatus.replace(
                      copy);
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
        new AbstractRewritingPass(logger) {
          @Override
          protected ProcessingStatus
 previsit(         BaseNode node, @Nullable SList<Parent> pathFromRoot) {
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
        new AbstractRewritingPass(logger) {
          @Override
          protected ProcessingStatus postvisit(
              BaseNode node, @Nullable SList<Parent> pathFromRoot) {
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
    assertRewrite(
        "class Foo implements A, C, D, E {}",
        "class Foo implements A, B, C, D, E {}",
        new AbstractRewritingPass(logger) {
          @Override
          protected ProcessingStatus previsit(
              BaseNode node, @Nullable SList<Parent> pathFromRoot) {
            switch (node.getNodeType()) {
              case InterfaceTypeList:
                ((InterfaceTypeListNode) node).remove(1);
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
  public void testOutOfBandChangesAndBreak() throws Exception {
    assertRewrite(
        "class Foo implements A, C, D, E {}",
        "class Foo implements A, B, C, D, E {}",
        new AbstractRewritingPass(logger) {
          @Override
          protected ProcessingStatus previsit(
              BaseNode node, @Nullable SList<Parent> pathFromRoot) {
            switch (node.getNodeType()) {
              case InterfaceTypeList:
                ((InterfaceTypeListNode) node).remove(1);
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
        new AbstractRewritingPass(logger) {
          @Override
          protected ProcessingStatus postvisit(
              BaseNode node, @Nullable SList<Parent> pathFromRoot) {
            switch (node.getNodeType()) {
              case InterfaceTypeList:
                ((InterfaceTypeListNode) node).remove(1);
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
