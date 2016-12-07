package com.mikesamuel.cil.ast.passes;

import static com.mikesamuel.cil.ast.meta.Name.Type.CLASS;
import static com.mikesamuel.cil.ast.meta.Name.Type.PACKAGE;
import static com.mikesamuel.cil.ast.meta.Name.Type.TYPE_PARAMETER;

import java.util.List;
import java.util.logging.Logger;

import org.junit.Test;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.mikesamuel.cil.ast.CompilationUnitNode;
import com.mikesamuel.cil.ast.NodeType;
import com.mikesamuel.cil.ast.Trees;
import com.mikesamuel.cil.ast.meta.Name;
import com.mikesamuel.cil.parser.Input;
import com.mikesamuel.cil.parser.LeftRecursion;
import com.mikesamuel.cil.parser.ParseErrorReceiver;
import com.mikesamuel.cil.parser.ParseResult;
import com.mikesamuel.cil.parser.ParseState;
import com.mikesamuel.cil.ptree.PTree;

import junit.framework.TestCase;

@SuppressWarnings("javadoc")
public final class ClassNamingPassTest extends TestCase {

  private static final Logger LOGGER = Logger.getLogger(
      ClassNamingPassTest.class.getName());

  @Test
  public static void testClassFinder() {
    List<CompilationUnitNode> compilationUnits = parseCompilationUnits(
        new String[][] {
          {
            "package foo.bar;",
            "",
            "final class C {",
            "  interface I { interface J { } }",
            "  private static final Bar x = new Bar(new Baz() {}) {",
            "     Baz y = new Baz() {};",
            "  };",
            "}",
            "class D { }",
          },
          {
            "@interface Foo {",
            "  enum A { B(), C }",
            "}",
          },
          {
            "package com.example;",
            "enum X {",
            "  A(1),",
            "  B(2) { interface Z {} },",
            "  C(3) {},",
            "  ;",
            "  X(int i) { }",
            "}",
          },
          {
            "package com.example.parameterized;",
            "",
            "class P<T> {",
            "  void foo() {",
            "  }",
            "  <T> void bar() {",
            "  }",
            "}",
          },
        });
    ClassNamingPass p = new ClassNamingPass(LOGGER);

    ImmutableList<Name> want = ImmutableList.of(
        Name.DEFAULT_PACKAGE
        .child("foo", PACKAGE)
        .child("bar", PACKAGE)
        .child("C", CLASS),

        Name.DEFAULT_PACKAGE
        .child("foo", PACKAGE)
        .child("bar", PACKAGE)
        .child("C", CLASS)
        .child("I", CLASS),

        Name.DEFAULT_PACKAGE
        .child("foo", PACKAGE)
        .child("bar", PACKAGE)
        .child("C", CLASS)
        .child("I", CLASS)
        .child("J", CLASS),

        Name.DEFAULT_PACKAGE
        .child("foo", PACKAGE)
        .child("bar", PACKAGE)
        .child("C", CLASS)
        .child("1", CLASS),

        Name.DEFAULT_PACKAGE
        .child("foo", PACKAGE)
        .child("bar", PACKAGE)
        .child("C", CLASS)
        .child("2", CLASS),

        Name.DEFAULT_PACKAGE
        .child("foo", PACKAGE)
        .child("bar", PACKAGE)
        .child("C", CLASS)
        .child("1", CLASS)
        .child("1", CLASS),

        Name.DEFAULT_PACKAGE
        .child("foo", PACKAGE)
        .child("bar", PACKAGE)
        .child("D", CLASS),

        Name.DEFAULT_PACKAGE
        .child("Foo", CLASS),

        Name.DEFAULT_PACKAGE
        .child("Foo", CLASS)
        .child("A", CLASS),

        Name.DEFAULT_PACKAGE
        .child("com", PACKAGE)
        .child("example", PACKAGE)
        .child("X", CLASS),

        Name.DEFAULT_PACKAGE
        .child("com", PACKAGE)
        .child("example", PACKAGE)
        .child("X", CLASS)
        .child("1", CLASS),

        Name.DEFAULT_PACKAGE
        .child("com", PACKAGE)
        .child("example", PACKAGE)
        .child("X", CLASS)
        .child("1", CLASS)
        .child("Z", CLASS),

        Name.DEFAULT_PACKAGE
        .child("com", PACKAGE)
        .child("example", PACKAGE)
        .child("X", CLASS)
        .child("2", CLASS),

        Name.DEFAULT_PACKAGE
        .child("com", PACKAGE)
        .child("example", PACKAGE)
        .child("parameterized", PACKAGE)
        .child("P", CLASS),

        Name.DEFAULT_PACKAGE
        .child("com", PACKAGE)
        .child("example", PACKAGE)
        .child("parameterized", PACKAGE)
        .child("P", CLASS)
        .child("T", TYPE_PARAMETER),

        Name.DEFAULT_PACKAGE
        .child("com", PACKAGE)
        .child("example", PACKAGE)
        .child("parameterized", PACKAGE)
        .child("P", CLASS)
        .method("bar", "(1)")
        .child("T", TYPE_PARAMETER)
);

    ClassNamingPass.DeclarationsAndScopes declsAndScopes =
        p.run(compilationUnits);
    ImmutableList<Name> got = ImmutableList.copyOf(
        declsAndScopes.declarations.keySet());

    if (!want.equals(got)) {
      assertEquals(
          Joiner.on('\n').join(want),
          Joiner.on('\n').join(got));
      assertEquals(want, got);
    }
  }

  static ImmutableList<CompilationUnitNode> parseCompilationUnits(
      String[]... linesPerFile) {
    ImmutableList.Builder<CompilationUnitNode> b = ImmutableList.builder();
    for (String[] lines : linesPerFile) {
      Input.Builder inputBuilder = Input.builder()
          .code(Joiner.on('\n').join(lines));
      if (lines.length != 0) {
        inputBuilder.source(lines[0]);
      }
      Input inp = inputBuilder.build();
      ParseResult result = PTree.complete(NodeType.CompilationUnit).getParSer()
          .parse(new ParseState(inp), new LeftRecursion(),
              ParseErrorReceiver.DEV_NULL);
      assertEquals(ParseResult.Synopsis.SUCCESS, result.synopsis);
      ParseState afterParse = result.next();
      CompilationUnitNode cu = (CompilationUnitNode)
          Trees.of(inp, afterParse.output);
      b.add(cu);
    }
    return b.build();
  }
}
