package com.mikesamuel.cil.ast.passes;

import java.util.logging.Logger;

import org.junit.Test;

import com.google.common.collect.ImmutableList;
import com.mikesamuel.cil.ast.BaseNode;
import com.mikesamuel.cil.ast.CompilationUnitNode;
import com.mikesamuel.cil.ast.Java8Comments;
import com.mikesamuel.cil.ast.Trees.Decorator;
import com.mikesamuel.cil.ast.meta.ExpressionNameResolver;
import com.mikesamuel.cil.ast.meta.ExpressionNameResolver.DeclarationPositionMarker;
import com.mikesamuel.cil.ast.meta.TypeInfoResolver;
import com.mikesamuel.cil.ast.passes.PassTestHelpers.PassRunner;
import com.mikesamuel.cil.ast.traits.ExpressionNameScope;
import com.mikesamuel.cil.ast.traits.LimitedScopeElement;
import com.mikesamuel.cil.parser.Unparse.UnparseVerificationException;

import junit.framework.TestCase;

@SuppressWarnings("javadoc")
public final class ExpressionScopePassTest extends TestCase {

  private static final Decorator DECORATOR = new Decorator() {
    @Override
    public String decorate(BaseNode node) {
      DeclarationPositionMarker m = null;
      if (node instanceof LimitedScopeElement) {
        m = ((LimitedScopeElement) node).getDeclarationPositionMarker();
      }
      ExpressionNameResolver r = null;
      if (node instanceof ExpressionNameScope) {
        r = ((ExpressionNameScope) node).getExpressionNameResolver();
      }
      String s = m != null
          ? (r != null ? m + " " + r : m.toString())
          : (r != null ? r.toString() : null);
      return s != null ? Java8Comments.blockComment(s, false) : null;
    }
  };

  /**
   * Some fields that should be considered available via the ClassLoader based
   * TypeInfo resolver.
   */
  public static class S {
    public int i;
    public static String s;
    public static String t;
    @SuppressWarnings("unused")
    private double d;
  }

  public enum E {
    X,
    Y,
    Z,
  }

  public enum F {
    A,
    B,
    C,
  }


  @Test
  public static void testScopeyScopes() throws Exception {
    assertScopesAndMarkers(
        new String[][] {
          {
            "/* (Names"
            + " /com/mikesamuel/cil/ast/passes/ExpressionScopePassTest$F.B"
            + ",/com/mikesamuel/cil/ast/passes/ExpressionScopePassTest$E{X,Y,Z}"
            + ") */",
            "package foo;",
            "import " + ExpressionScopePassTest.class.getName() + ".S;",
            "import static " + E.class.getName() + ".*;",
            "import static " + F.class.getName() + ".B;",
            "class P extends S",
            // P.s masks S.s.
            // (Also, now I'm commenting on comments on comments.  Help me.)
            "/* ("
            + "Names /foo/P.s"
            + ",/com/mikesamuel/cil/ast/passes/ExpressionScopePassTest$S{i,t}"
            + ") */",
            "{",
            "  String s;",
            "  /* (Block /foo/P.<init>(1){i,j}) */",
            "  P(int i) {",
            "    super ();",
            "    /* #0 */",
            "    this.i = i;",
            "    /* #1 */",
            "    int j = i + 1;",
            "    /* #1 */",
            "    print(j);",
            "  }",
            "}",
          },
        },
        new String[][] {
          {
            "//P",
            "package foo;",
            "import " + ExpressionScopePassTest.class.getName() + ".S;",
            "import static " + E.class.getName() + ".*;",
            "import static " + F.class.getName() + ".B;",
            "class P extends S {",
            "  String s;",
            "  P(int i) {",
            "    super();",
            "    this.i = i;",
            "    int j = i + 1;",
            "    print(j);",
            "  }",
            "}",
          },
        }
        );
  }


  @Test
  public static void testSwitchScopes() throws Exception {
    assertScopesAndMarkers(
        new String[][] {
          {
            "/* (Names) */",
            "package foo;",
            "class C",
            "/* (Names /foo/C.i) */",
            "{",
            "  int i;",
            "  /* (Block) */",
            "  void f()",
            "  /* (Block) */",
            "  {",
            "    /* EARLIEST */",
            "    switch (i)",
            "    /* (Block /foo/C.f(1):i) */",
            "    {",  // this.i
            "      case 0 :",
            "      /* EARLIEST */",
            "      g(i);",  // this.i;
            "      /* EARLIEST */",
            "      break;",
            "      case 1 :",
            "      /* #0 */",
            "      int i = 0;",
            "      /* #0 */",
            "      h(i);",  // local i
            "      /* #0 */",
            "      break;",
            "      case 2 :",
            "      /* #0 */",
            "      i = 2;",  // local i
            "    }",
            "    /* EARLIEST */",
            "    println(i);",  // this.i
            "  }",
            "}",
          },
        },
        new String[][] {
          {
            "//P",
            "package foo;",
            "class C {",
            "  int i;",
            "  void f() {",
            "    switch (i) {",  // this.i
            "      case 0:",
            "        g(i);",  // this.i;
            "        break;",
            "      case 1:",
            "        int i = 0;",
            "        h(i);",  // local i
            "        break;",
            "      case 2:",
            "        i = 2;",  // local i
            "    }",
            "    println(i);",  // this.i
            "  }",
            "}",
          },
        }
        );
  }

  @Test
  public static void testScopingOfCtorActualsForAnonClass() throws Exception {
    assertScopesAndMarkers(
        new String[][] {
          {
            "/* (Names) */",
            "import java.util.List;",
            "import java.util.ArrayList;",
            "class C",
            "/* (Names /C.i) */",
            "{",
            "  int i;",
            "  /* (Block) */",
            "  List f()",
            "  /* (Block) */",
            "  {",
            "    /* EARLIEST */",
            "    return new ArrayList <> (i)",
            "    /* (Names "
            +       "/C.f(1)$1.i,"
            +       "/java/util/AbstractList.modCount"  // protected
                    // ArrayList defines a package private elementData which is
                    // not visible.
            +       ") */",
            "    { int i; };",
            "  }",
            "}",
          },
        },
        new String[][] {
          {
            "import java.util.List;",
            "import java.util.ArrayList;",
            "class C {",
            "  int i;",
            "  List f() {",
            "    return new ArrayList<>(i) { int i; };",
            "  }",
            "}",
          }
        });
  }

  private static void assertScopesAndMarkers(
      String[][] want, String[][] inputs, String... expectedErrors)
  throws UnparseVerificationException {

    PassTestHelpers.assertAnnotatedOutput(
        new PassRunner() {

          @Override
          public ImmutableList<CompilationUnitNode> runPasses(
              Logger logger, ImmutableList<CompilationUnitNode> cus) {
            DeclarationPass dp = new DeclarationPass(logger);
            TypeInfoResolver tir = dp.run(cus);
            ExpressionScopePass esp = new ExpressionScopePass(tir, logger);
            esp.run(cus);
            return cus;
          }
        },
        want,
        inputs,
        DECORATOR,
        expectedErrors
        );
  }

}
