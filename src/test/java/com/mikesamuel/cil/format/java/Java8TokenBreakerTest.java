package com.mikesamuel.cil.format.java;

import org.junit.Test;

import com.google.common.base.Preconditions;
import com.mikesamuel.cil.ast.MatchEvent;
import com.mikesamuel.cil.ast.NodeType;
import com.mikesamuel.cil.ast.NodeVariant;
import com.mikesamuel.cil.format.TokenBreak;
import com.mikesamuel.cil.format.TokenBreaker;
import com.mikesamuel.cil.format.java.Java8TokenBreaker;
import com.mikesamuel.cil.parser.Chain;
import com.mikesamuel.cil.parser.Input;
import com.mikesamuel.cil.parser.LeftRecursion;
import com.mikesamuel.cil.parser.ParseErrorReceiver;
import com.mikesamuel.cil.parser.ParseResult;
import com.mikesamuel.cil.parser.ParseState;

import junit.framework.TestCase;

@SuppressWarnings("javadoc")
public final class Java8TokenBreakerTest extends TestCase {

  private static void assertSpacedTokens(
      boolean includeShould,
      String withSpaces, String... tokens) {
    TokenBreaker<?> tokenBreaker = new Java8TokenBreaker();
    StringBuilder sb = new StringBuilder();
    String last = null;
    for (String tok : tokens) {
      if (last != null) {
        switch (tokenBreaker.breakBetween(last, null, tok, null)) {
          case SHOULD:
            if (includeShould) {
              sb.append(" ");
            }
            break;
          case MAY:
          case SHOULD_NOT:
            break;
          case MUST:
            sb.append(" ");
            break;
        }
      }
      sb.append(tok);
      last = tok;
    }
    assertEquals(withSpaces, sb.toString());
  }

  @Test
  public static void testSpacesAroundNumbers() {
    assertSpacedTokens(false, "1 1", "1", "1");
    assertSpacedTokens(false, "1. 1", "1.", "1");
    assertSpacedTokens(false, "1 .1", "1", ".1");
    assertSpacedTokens(false, ". 1", ".", "1");
    assertSpacedTokens(false, "1 .", "1", ".");
    assertSpacedTokens(false, "... 1", "...", "1");
    assertSpacedTokens(false, "1e 1", "1e", "1");
  }

  @Test
  public static void testSpacesAroundWords() {
    assertSpacedTokens(false, "a b", "a", "b");
    assertSpacedTokens(false, "public boolean", "public", "boolean");
    assertSpacedTokens(false, "a $ _", "a", "$", "_");
    assertSpacedTokens(false, "a.b", "a", ".", "b");
  }

  @Test
  public static void testMergeablePunc() {
    assertSpacedTokens(false, "< =", "<", "=");
    assertSpacedTokens(false, "- -", "-", "-");
    assertSpacedTokens(false, "- --", "-", "--");
    assertSpacedTokens(false, "+ ++", "+", "++");
    assertSpacedTokens(false, "1+ +2", "1", "+", "+", "2");
    assertSpacedTokens(false, ". . . ...", ".", ".", ".", "...");
    assertSpacedTokens(false, "- >", "-", ">");
    assertSpacedTokens(false, "(& & =)", "(", "&", "&", "=", ")");
  }

  @Test
  public static void testCommentDelimiterAmbiguity() {
    assertSpacedTokens(false, "/ *", "/", "*");
    assertSpacedTokens(false, "/ *", "/", "*");
    assertSpacedTokens(false, "/ **", "/", "*", "*");
    assertSpacedTokens(false, "/ /**foo*/", "/", "/**foo*/");
    assertSpacedTokens(false, "/ //foo", "/", "//foo");
    assertEquals(
        TokenBreak.MUST,
        new Java8TokenBreaker().lineBetween("//foo", null, "x", null));
  }

  @Test
  public static void testConventions() {
    assertSpacedTokens(
        true, "if (x == y) { return; }",
        "if", "(", "x", "==", "y", ")", "{", "return", ";", "}");
    assertSpacedTokens(
        true, "new String[] { 1, 2 };",
        "new", "String", "[", "]", "{", "1", ",", "2", "}", ";");
    assertSpacedTokens(
        true, "((T) arr[i])",
        "(", "(", "T", ")", "arr", "[", "i", "]", ")");
    assertSpacedTokens(
        true, "import static foo.bar.*;",
        "import", "static", "foo", ".", "bar", ".", "*", ";");
    assertSpacedTokens(
        true, "@Annot(foo = Bar)",
        "@", "Annot", "(", "foo", "=", "Bar", ")");
  }

  @Test
  public void testWithContext() {
    assertParsedSpacedTokens("x + ++x", NodeType.Expression);
    assertParsedSpacedTokens("x + x++", NodeType.Expression);
    assertParsedSpacedTokens("x + --x", NodeType.Expression);
    assertParsedSpacedTokens("x + x--", NodeType.Expression);
    assertParsedSpacedTokens("+x", NodeType.Expression);
    assertParsedSpacedTokens("-x", NodeType.Expression);
    assertParsedSpacedTokens("x + x", NodeType.Expression);
    assertParsedSpacedTokens("x - x", NodeType.Expression);
    assertParsedSpacedTokens("for (;;) {}", NodeType.Statement);
    assertParsedSpacedTokens(
        "try (InputStream in = open();) { use(in); }",
        NodeType.Statement);
  }

  private void assertParsedSpacedTokens(
      String content, NodeType startProduction) {
    assertParsedSpacedTokens(true, content, content, startProduction);
  }
  private void assertParsedSpacedTokens(
      boolean includeShould, String expected, String content,
      NodeType startProduction) {
    Input inp = Input.fromCharSequence(getName(), content);
    ParseState start = new ParseState(inp);
    ParseResult result = startProduction.getParSer().parse(
        start, new LeftRecursion(), ParseErrorReceiver.DEV_NULL);

    Java8TokenBreaker tokenBreaker = new Java8TokenBreaker();

    StringBuilder sb = new StringBuilder();
    String lastTok = null;
    Chain<NodeVariant> lastStack = null;
    Chain<NodeVariant> stack = null;
    for (MatchEvent e : Chain.forwardIterable(result.next().output)) {
      String tok = null;
      if (e instanceof MatchEvent.Content) {
        tok = ((MatchEvent.Content) e).content;
      } else if (e instanceof MatchEvent.Token) {
        tok = ((MatchEvent.Token) e).content;
      } else if (e instanceof MatchEvent.Pop) {
        Preconditions.checkNotNull(stack);
        stack = stack.prev;
      } else if (e instanceof MatchEvent.Push) {
        stack = Chain.append(stack, ((MatchEvent.Push) e).variant);
      }
      if (tok != null) {
        if (lastTok != null) {
          TokenBreak b = tokenBreaker.breakBetween(
              lastTok, lastStack, tok, stack);
          switch (b) {
            case SHOULD:
              if (includeShould) {
                sb.append(" ");
              }
              break;
            case MAY:
            case SHOULD_NOT:
              break;
            case MUST:
              sb.append(" ");
              break;
          }
        }
        sb.append(tok);
        lastTok = tok;
        lastStack = stack;
      }
    }
    assertEquals(expected, sb.toString());
    assertNull(stack);
  }
}
