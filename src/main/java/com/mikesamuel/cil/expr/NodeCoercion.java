package com.mikesamuel.cil.expr;

import com.google.common.collect.ImmutableList;
import com.mikesamuel.cil.ast.Intermediates;
import com.mikesamuel.cil.ast.NodeType;
import com.mikesamuel.cil.ast.NodeVariant;
import com.mikesamuel.cil.ast.StringLiteralNode;
import com.mikesamuel.cil.ast.Trees;
import com.mikesamuel.cil.parser.Input;
import com.mikesamuel.cil.parser.LeftRecursion;
import com.mikesamuel.cil.parser.ParseErrorReceiver;
import com.mikesamuel.cil.parser.ParseResult;
import com.mikesamuel.cil.parser.ParseState;
import com.mikesamuel.cil.ptree.PTree;
import com.mikesamuel.cil.ptree.Tokens;

/**
 * Utilities for coercing expression results to ASTs.
 */
public final class NodeCoercion {

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
  public static Object tryToCoerce(Object input, NodeType nodeType) {
    Object value = input;
    // Try a variety of strategies to coerce value to something that can be
    // injected into an AST.
    if (value instanceof NodeVariant) {
      value = ((NodeVariant) value).buildNode(ImmutableList.of());
    }

    boolean allowStringLit = true;
    // Treat primitive wrappers as substitutable for the corresponding
    // Literal node types.
    if (value instanceof Number) {
      boolean isFloat = value instanceof Double || value instanceof Float;
      NodeType literal = isFloat
          ? NodeType.FloatingPointLiteral
          : NodeType.IntegerLiteral;

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
      if (Intermediates.reachedFrom(NodeType.CharacterLiteral, nodeType)) {
        StringBuilder sb = new StringBuilder();
        sb.append('\'');
        Tokens.encodeCodepointOnto(((Character) value).charValue(), sb);
        sb.append('\'');
        value = sb.toString();
        allowStringLit = false;
      }
    } else if (value instanceof Boolean) {
      if (Intermediates.reachedFrom(NodeType.BooleanLiteral, nodeType)) {
        value = value.toString();
        allowStringLit = false;
      }
    }

    // Invoke a brief parser to allow substituting
    // 1. "public" for Modifier.Public
    // 2. "123" for IntegerLiteral.Builtin
    // 3. other strings for a StringLiteral
    // when such are reachable from NodeType.
    if (value instanceof CharSequence) {
      String content = new StringBuilder()
          .append((CharSequence) value)
          .toString();

      if (allowStringLit
          && Intermediates.reachedFrom(NodeType.StringLiteral, nodeType)) {
        value = StringLiteralNode.Variant.Builtin.buildNode(
            Tokens.encodeString(content));
      } else {
        Input inp = Input.builder().code(content).build();
        ParseState start = new ParseState(inp);
        ParseResult result = PTree.complete(nodeType).getParSer()
            .parse(start, new LeftRecursion(), ParseErrorReceiver.DEV_NULL);
        if (result.synopsis == ParseResult.Synopsis.SUCCESS) {
          value = Trees.of(result.next());
        }
      }
    }
    return value;
  }

}
