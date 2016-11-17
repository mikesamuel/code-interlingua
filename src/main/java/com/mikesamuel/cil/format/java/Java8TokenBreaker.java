package com.mikesamuel.cil.format.java;

import javax.annotation.Nullable;

import com.google.common.collect.ImmutableSet;
import com.mikesamuel.cil.ast.NodeType;
import com.mikesamuel.cil.ast.NodeVariant;
import com.mikesamuel.cil.ast.TokenStrings;
import com.mikesamuel.cil.format.TokenBreak;
import com.mikesamuel.cil.format.TokenBreaker;
import com.mikesamuel.cil.format.java.Java8TokenClassifier.Classification;
import com.mikesamuel.cil.parser.Chain;
import com.mikesamuel.cil.ptree.Tokens;

/**
 * Determines which tokens should have spaces between them based on the need to
 * avoid lexical ambiguity and a desire to format token streams the way a human
 * might.
 */
final class Java8TokenBreaker
implements TokenBreaker<Chain<NodeVariant>> {

  @Override
  public TokenBreak breakBetween(
      String left,  @Nullable Chain<NodeVariant> leftStack,
      String right, @Nullable Chain<NodeVariant> rightStack) {
    Classification lc = Java8TokenClassifier.classify(left);
    Classification rc = Java8TokenClassifier.classify(right);
    if ((lc == Classification.IDENTIFIER_CHARS
        || lc == Classification.NUMBER_LITERAL)
        && (rc == Classification.IDENTIFIER_CHARS
            || rc == Classification.NUMBER_LITERAL)) {
      return TokenBreak.MUST;
    }

    if (lc == Classification.NUMBER_LITERAL
        && rc == Classification.PUNCTUATION && right.startsWith(".")) {
      return TokenBreak.MUST;
    }
    if (lc == Classification.PUNCTUATION
        && rc == Classification.NUMBER_LITERAL
        && left.endsWith(".")) {
      return TokenBreak.MUST;
    }

    if (lc == Classification.PUNCTUATION && rc == Classification.PUNCTUATION) {
      // Test that the two tokens don't merge into a larger one.
      // For example:  (x - -1) should not be rendered (x--1) since (x--) has
      // an independent meaning.
      String extended = left + right.charAt(0);
      if (TokenStrings.PUNCTUATION.contains(extended)
          || !Tokens.punctuationSuffixes(extended).isEmpty()) {
        return TokenBreak.MUST;
      }
    }

    if (lc == Classification.PUNCTUATION) {
      switch (left) {
        case ".": return TokenBreak.SHOULD_NOT;
        case "[":
        case "(": return TokenBreak.SHOULD_NOT;
        case ")":
          if (";".equals(right)) { return TokenBreak.SHOULD_NOT; }
          return TokenBreak.SHOULD;
        case "{":
          if ("}".equals(right)) { return TokenBreak.SHOULD_NOT; }
          return TokenBreak.SHOULD;
        case ",":
        case ";":
          if (")".equals(right) || ";".equals(right)) {
            return TokenBreak.SHOULD_NOT;
          }
          return TokenBreak.SHOULD;
        case "!": case "~":
          return TokenBreak.SHOULD_NOT;
        case "-": case "+":
          if (inPrefixOperatorContext(leftStack)) {
            return TokenBreak.SHOULD_NOT;
          }
          return TokenBreak.SHOULD;
        case "--": case "++":
          if (inPrefixOperatorContext(leftStack)) {
            return TokenBreak.SHOULD_NOT;
          }
          break;
        case "]":
          if (rc == Classification.IDENTIFIER_CHARS || "{".equals(right)) {
            return TokenBreak.SHOULD;
          }
          break;
        case "}":
          if (")".equals(right) || ";".equals(right)) {
            return TokenBreak.SHOULD_NOT;
          }
          return TokenBreak.SHOULD;
        case ":": case "?":
        case "...":
          return TokenBreak.SHOULD;
        case "*":
          if (";".equals(right)) {  // Special use in wildcard imports.
            return TokenBreak.SHOULD_NOT;
          }
          return TokenBreak.SHOULD;
        default:
          if (isBinaryOperator(left)) {
            return TokenBreak.SHOULD;
          }
      }
    }

    if (rc == Classification.PUNCTUATION) {
      switch (right) {
        case ".": return TokenBreak.SHOULD_NOT;
        case "(":
          if (isKeyword(left)) { return TokenBreak.SHOULD; }
          return TokenBreak.SHOULD_NOT;
        case ")": case "]":
          return TokenBreak.SHOULD_NOT;
        case "}":
          if (",".equals(left)) { return TokenBreak.SHOULD_NOT; }
          return TokenBreak.SHOULD;
        case ",":
        case ";":
          return TokenBreak.SHOULD_NOT;
        case ":": case "?":
          return TokenBreak.SHOULD;
        case "--": case "++":
          if (inPostfixOperatorContext(rightStack)) {
            return TokenBreak.SHOULD_NOT;
          }
          break;
        default:
          if (isBinaryOperator(right)) {
            return TokenBreak.SHOULD;
          }
      }
    }

    return TokenBreak.SHOULD_NOT;
  }

  private static boolean inPostfixOperatorContext(Chain<NodeVariant> stack) {
    if (stack == null || stack.prev == null) { return false; }
    return stack.prev.x.getNodeType() == NodeType.PostExpression;
  }

  private static boolean inPrefixOperatorContext(Chain<NodeVariant> stack) {
    if (stack == null) { return false; }
    if (stack.x.getNodeType() == NodeType.PrefixOperator) { return true; }
    return stack.prev != null
        && stack.prev.x.getNodeType() == NodeType.PreExpression;
  }

  @Override
  public TokenBreak lineBetween(
      String left,  @Nullable Chain<NodeVariant> leftStack,
      String right, @Nullable Chain<NodeVariant> rightStack) {
    switch (left) {
      case "{":
        if (!right.equals("}")) {
          return TokenBreak.SHOULD;
        }
        break;
      case "}":
        if ("else".equals(right) || "catch".equals(right)
            || "finally".equals(right)) {
          // Also "while" inside a DoStatement
          return TokenBreak.SHOULD_NOT;
        }
        return TokenBreak.SHOULD;
      case ";":
        if (leftStack != null) {
          switch (leftStack.x.getNodeType()) {
            case TryWithResourcesStatement:
            case BasicForStatement:
              return TokenBreak.SHOULD_NOT;
            default:
              break;
          }
        }
        return TokenBreak.SHOULD;
    }
    // http://www.oracle.com/technetwork/java/javase/documentation/codeconventions-136091.html#248
    // > When an expression will not fit on a single line, break it according to
    // > these general principles:
    // > * Break after a comma.
    // > * Break before an operator.
    // > ...
    switch (right) {
      case ",": return TokenBreak.SHOULD_NOT;
    }
    if (isBinaryOperator(left) || isUnaryOperator(left)) {
      // Prefer to break before binary operators
      //    foo
      //    && bar
      // and not between a unary operator and its operand:
      //    -4
      return TokenBreak.SHOULD_NOT;
    }
    return TokenBreak.MAY;
  }

  private static boolean isKeyword(String tok) {
    return TokenStrings.RESERVED.contains(tok);
  }

  private static final ImmutableSet<String> BINARY_OPERATORS = ImmutableSet.of(
      "||", "&&", "|", "&", "^", "==", "!=",
      "<=", ">=","<", ">",
      "<<", ">>>", ">>",
      "+", "-",  // Ambiguous with PrefixOperator
      "*", "/", "%",
      "=", "*=", "/=", "%=", "+=", "-=", "<<=", ">>=", ">>>=", "&=", "^=", "|=",
      "->"  // Not really
      );

  private static boolean isBinaryOperator(String tok) {
    return BINARY_OPERATORS.contains(tok);
  }

  private static final ImmutableSet<String> UNARY_OPERATORS = ImmutableSet.of(
      "+", "-", "++", "--", "~", "!"
      );

  private static boolean isUnaryOperator(String tok) {
    return UNARY_OPERATORS.contains(tok);
  }
}
