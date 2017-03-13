package com.mikesamuel.cil.ast.j8;

import com.google.common.collect.ImmutableList;
import com.mikesamuel.cil.ast.Trees;
import com.mikesamuel.cil.ast.j8.J8NodeType;
import com.mikesamuel.cil.ast.j8.J8NodeVariant;
import com.mikesamuel.cil.ast.j8.StringLiteralNode;
import com.mikesamuel.cil.parser.Input;
import com.mikesamuel.cil.parser.LeftRecursion;
import com.mikesamuel.cil.parser.ParseErrorReceiver;
import com.mikesamuel.cil.parser.ParseResult;
import com.mikesamuel.cil.parser.ParseState;
import com.mikesamuel.cil.ptree.PTree;

/**
 * Utilities for coercing expression results to ASTs.
 */
final class NodeCoercion {

  private NodeCoercion() {
    // static API
  }

  /**
   * Makes a best effort to coerce an expression result to an AST node of the
   * given type.
   *
   * @param input the value to coerce.
   * @param nodeType the type of node we want out.
   *
   * @return a node of the given type or a partially coerced input.
   */
  static Object tryToCoerce(Object input, J8NodeType nodeType) {
    Object value = input;
    // Try a variety of strategies to coerce value to something that can be
    // injected into an AST.
    if (value instanceof J8NodeVariant) {
      value = ((J8NodeVariant) value).buildNode(ImmutableList.of());
    }

    boolean allowStringLit = true;
    // Treat primitive wrappers as substitutable for the corresponding
    // Literal node types.
    if (value instanceof Number) {
      boolean isFloat = value instanceof Double || value instanceof Float;
      J8NodeType literal = isFloat
          ? J8NodeType.FloatingPointLiteral
          : J8NodeType.IntegerLiteral;

      if (Intermediates.reachedFrom(literal, nodeType)) {
        String suffix = "";
        if (value instanceof Float) {
          suffix = "F";
        } else if (value instanceof Long) {
          suffix = "L";
        }
        value = value + suffix;
        allowStringLit = false;
      }
    } else if (value instanceof Character) {
      if (Intermediates.reachedFrom(J8NodeType.CharacterLiteral, nodeType)) {
        StringBuilder sb = new StringBuilder();
        sb.append('\'');
        Tokens.encodeCodepointOnto(((Character) value).charValue(), sb);
        sb.append('\'');
        value = sb.toString();
        allowStringLit = false;
      }
    } else if (value instanceof Boolean) {
      if (Intermediates.reachedFrom(J8NodeType.BooleanLiteral, nodeType)) {
        value = value.toString();
        allowStringLit = false;
      }
    }

    // Invoke a brief parser to allow substituting
    // 1. "public" for Modifier.Public
    // 2. "123" for IntegerLiteral.Builtin
    // 3. other strings for a StringLiteral
    // when such are reachable from J8NodeType.
    if (value instanceof CharSequence) {
      String content = new StringBuilder()
          .append((CharSequence) value)
          .toString();

      if (allowStringLit
          && Intermediates.reachedFrom(J8NodeType.StringLiteral, nodeType)) {
        value = StringLiteralNode.Variant.Builtin.buildNode(
            Tokens.encodeString(content));
      } else {
        Input inp = Input.builder().code(content).build();
        ParseState start = new ParseState(inp);
        ParseResult result = PTree.complete(nodeType).getParSer()
            .parse(start, new LeftRecursion(), ParseErrorReceiver.DEV_NULL);
        if (result.synopsis == ParseResult.Synopsis.SUCCESS) {
          value = Trees.forGrammar(nodeType.getGrammar()).of(result.next());
        }
      }
    }
    return value;
  }

}
