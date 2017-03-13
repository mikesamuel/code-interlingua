package com.mikesamuel.cil.template;

import java.io.IOException;
import java.io.Reader;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import com.google.common.collect.Maps;
import com.google.common.io.CharSource;
import com.mikesamuel.cil.ast.Trees;
import com.mikesamuel.cil.ast.j8.J8BaseNode;
import com.mikesamuel.cil.ast.j8.J8NodeType;
import com.mikesamuel.cil.expr.InterpretationContext;
import com.mikesamuel.cil.parser.Input;
import com.mikesamuel.cil.parser.LeftRecursion;
import com.mikesamuel.cil.parser.ParseErrorReceiver;
import com.mikesamuel.cil.parser.ParseResult;
import com.mikesamuel.cil.parser.ParseState;
import com.mikesamuel.cil.parser.SList;
import com.mikesamuel.cil.ptree.PTree;

/**
 * A bundle of data that allows dynamic field access via
 * {@link InterpretationContext#getFieldDynamic}.
 */
public final class DataBundle {

  private final Map<String, Object> data = Maps.newLinkedHashMap();

  /**
   * The data associated with the given key or the defaultValue if there is none
   * such.
   */
  public @Nullable Object getOrDefault(
      String key, @Nullable Object defaultValue) {
    return data.getOrDefault(key, defaultValue);
  }

  /**
   * Unmodifiable set containing all keys such that
   * {@code getOrDefault(key, defaultValue)} does not fall back to the default.
   */
  public Set<String> keySet() {
    return Collections.unmodifiableSet(data.keySet());
  }

  /**
   * Initializes a data bundle from a file in JSON format.
   *
   * <p>
   * <code>{ "code": "...", "nodeType": "..." }</code> are parsed to ASTs.
   */
  public static DataBundle fromJsonFile(String source, CharSource code)
  throws IOException {
    JSONObject obj;
    try (Reader in = code.openBufferedStream()) {
      JSONTokener lexer = new JSONTokener(in);
      obj = new JSONObject(lexer);
      if (lexer.nextClean() != 0 || lexer.more()) {
        throw lexer.syntaxError("Unconsumed JSON input from " + source);
      }
    }
    Object x = from(source, null, obj);
    if (x instanceof DataBundle) {
      return (DataBundle) x;
    }
    DataBundle b = new DataBundle();
    b.data.put("x", x);
    return b;
  }

  private static Object from(
      String source, SList<Object> keyChain, JSONArray jarr) {
    int n = jarr.length();
    Object[] arr = new Object[n];
    for (int i = 0; i < n; ++i) {
      arr[i] = from(source, SList.append(keyChain, i), jarr.get(i));
    }
    return arr;
  }

  private static final String[] ZERO_STRINGS = new String[0];

  private static Object from(
      String source, SList<Object> keyChain, JSONObject obj) {
    String[] keys = JSONObject.getNames(obj);
    if (keys == null) { keys = ZERO_STRINGS; }
    if (keys.length == 2 && obj.has("nodeType")
        && obj.has("code")) {
      // Recognize
      //   { "nodeType": "Foo", "code": "..." }
      // and parse "..." using NodeType.Foo to get a FooNode AST.
      J8NodeType nodeType = obj.getEnum(J8NodeType.class, "nodeType");
      Input inp = Input.builder()
          .source(source)
          .code(obj.getString("code"))
          .build();
      ParseErrorReceiver err = ParseErrorReceiver.DEV_NULL;  // TODO
      ParseResult r = PTree.complete(nodeType.getParSer()).getParSer().parse(
          new ParseState(inp), new LeftRecursion(), err);
      switch (r.synopsis) {
        case FAILURE:
          throw new JSONException(
              "Failed to parse " + nodeType + " from " +
              source + " reached via " + SList.forwardIterable(keyChain));
        case SUCCESS:
          ParseState afterParse = r.next();
          J8BaseNode parsed = Trees
              .forGrammar(J8NodeType.CompilationUnit.getGrammar())
              .of(inp, afterParse.output);
          return parsed;
      }
      throw new AssertionError(r.synopsis);
    }
    DataBundle b = new DataBundle();
    for (String key : keys) {
      b.data.put(key, from(source, SList.append(keyChain, key), obj.get(key)));
    }
    return b;
  }

  private static Object from(
      String source, SList<Object> keyChain, Object value) {
    if (value instanceof JSONObject) {
      return from(source, keyChain, (JSONObject) value);
    } else if (value instanceof JSONArray) {
      return from(source, keyChain, (JSONArray) value);
    }
    return value;
  }
}
