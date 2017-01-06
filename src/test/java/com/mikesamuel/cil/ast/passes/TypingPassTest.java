package com.mikesamuel.cil.ast.passes;

import java.util.List;
import java.util.logging.Logger;

import javax.annotation.Nullable;

import org.junit.Test;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.mikesamuel.cil.ast.BaseNode;
import com.mikesamuel.cil.ast.CastExpressionNode;
import com.mikesamuel.cil.ast.CastNode;
import com.mikesamuel.cil.ast.CompilationUnitNode;
import com.mikesamuel.cil.ast.ExpressionNode;
import com.mikesamuel.cil.ast.Java8Comments;
import com.mikesamuel.cil.ast.StatementExpressionNode;
import com.mikesamuel.cil.ast.Trees.Decorator;
import com.mikesamuel.cil.ast.meta.StaticType;
import com.mikesamuel.cil.ast.meta.StaticType.TypePool;
import com.mikesamuel.cil.ast.meta.TypeInfoResolver;
import com.mikesamuel.cil.ast.passes.PassTestHelpers.LoggableOperation;
import com.mikesamuel.cil.ast.traits.MethodDescriptorReference;
import com.mikesamuel.cil.ast.traits.Typed;
import com.mikesamuel.cil.ast.traits.WholeType;
import com.mikesamuel.cil.parser.Unparse.UnparseVerificationException;

import junit.framework.TestCase;

@SuppressWarnings("javadoc")
public final class TypingPassTest extends TestCase {

  private static void assertTyped(
      String[][] inputs,
      Class<? extends BaseNode> target,
      String[] expectedTypes,
      String... expectedErrors)
  throws UnparseVerificationException {
    assertTyped(null, inputs, target, expectedTypes, null, expectedErrors);
  }

  private static void assertTyped(
      @Nullable String[][] expectedWithCasts,
      String[][] inputs,
      Class<? extends BaseNode> target,
      String[] expectedTypes,
      @Nullable Decorator decorator,
      String... expectedErrors)
  throws UnparseVerificationException {
    ImmutableList<CompilationUnitNode> processedCus =
        PassTestHelpers.expectErrors(
            new LoggableOperation<ImmutableList<CompilationUnitNode>>() {

              @Override
              public ImmutableList<CompilationUnitNode> run(Logger logger) {
                ImmutableList<CompilationUnitNode> cus =
                    PassTestHelpers.parseCompilationUnits(inputs);
                DeclarationPass dp = new DeclarationPass(logger);
                TypeInfoResolver typeInfoResolver = dp.run(cus);

                ExpressionScopePass scopePass = new ExpressionScopePass(
                    typeInfoResolver, logger);
                scopePass.run(cus);

                DisambiguationPass disambigPass = new DisambiguationPass(
                    typeInfoResolver, logger, false);
                cus = disambigPass.run(cus);

                TypePool typePool = new TypePool(typeInfoResolver);
                ClassMemberPass classMemberPass = new ClassMemberPass(
                    logger, typePool);
                classMemberPass.run(cus);

                TypingPass tp = new TypingPass(logger, typePool, true);
                return tp.run(cus);
              }

            },
            expectedErrors);


    if (expectedWithCasts != null) {
      String got = PassTestHelpers.serializeNodes(processedCus, decorator);
      String want = decorator == null
          ? PassTestHelpers.normalizeCompilationUnitSource(expectedWithCasts)
          : PassTestHelpers.joinExpectedLines(expectedWithCasts);
      assertEquals(want, got);
    }

    List<BaseNode> typed = Lists.newArrayList();
    for (CompilationUnitNode cu : processedCus) {
      for (BaseNode n : cu.finder(target).find()) {
        if (n instanceof Typed) {
          typed.add(n);
        } else if (n instanceof WholeType) {
          typed.add(n);
        } else {
          boolean found = false;
          for (Typed t
               : n.finder(Typed.class)
                 .exclude(WholeType.class).exclude(Typed.class)
                 .find()) {
            typed.add((BaseNode) t);
            found = true;
          }
          if (!found) {
            for (WholeType t
                 : n.finder(WholeType.class)
                   .exclude(WholeType.class)
                   .find()) {
              typed.add((BaseNode) t);
              found = true;
            }
          }
        }
      }
    }
    ImmutableList.Builder<String> got = ImmutableList.builder();
    for (BaseNode t : typed) {
      StaticType typ = (t instanceof Typed)
          ? ((Typed)     t).getStaticType()
          : ((WholeType) t).getStaticType();
      got.add(
          PassTestHelpers.serializeNodes(ImmutableList.of(t), null)
          + " : " + typ);
    }

    assertEquals(
        Joiner.on("\n\n").join(expectedTypes),
        Joiner.on("\n\n").join(got.build()));
  }


  @Test
  public static final void testLiterals() throws Exception {
    assertTyped(
        new String[][] {
          {
            "//C",
            "class C {",
            "  int x = 3;",
            "  long l = -42L;",
            "  float f = 3.f;",
            "  double d = 3.;",
            "  char c = 'c';",
            "  String s = \"\";",
            "  Object o = null;",
            "}",
          }
        },
        ExpressionNode.class,
        new String[] {
            "3 : int",
            "-42L : long",
            "3.f : float",
            "3. : double",
            "'c' : char",
            "\"\" : /java/lang/String",
            "null : <null>",
        });

  }

  @Test
  public static final void testSimpleUnboxing() throws Exception {
    assertTyped(
        new String[][] {
          {
            "class C {",
            "  long i = (long) (+(int) Integer.valueOf(\"4\"));",
            "}",
          },
        },
        new String[][] {
          {
            "//C",
            "class C {",
            "  long i = +Integer.valueOf(\"4\");",
            "}",
          },
        },
        ExpressionNode.class,
        new String[] {
            "(long) (+(int) Integer.valueOf(\"4\")) : long",
            "+(int) Integer.valueOf(\"4\") : int",
            "\"4\" : /java/lang/String",
        },
        null);
  }

  @Test
  public static final void testMethodDispatchDoesntBoxOrUnboxUnnecessarily()
  throws Exception {
    assertTyped(
        new String[][] {
          {
            "class C {",
            "  void f(int i) {}",
            "  String f(Integer i) { return null; }",
            "  {",
            "    this./*(I)V*/f(42);",
            "    this./*(Ljava/lang/Integer;)Ljava/lang/String;*/f(null);",
            "    String s = this./*(Ljava/lang/Integer;)Ljava/lang/String;*/f(Integer",
            "        ./*(I)Ljava/lang/Integer;*/valueOf(42));",
            "  }",
            "}",
          },
        },
        new String[][] {
          {
            "//C",
            "class C {",
            "  void f(int i) {}",
            "  String f(Integer i) { return null; }",
            "  {",
            "    this.f(42);",
            "    this.f(null);",
            "    String s = this.f(Integer.valueOf(42));",
            "  }",
            "}",
          },
        },
        ExpressionNode.class,
        new String[] {
            "null : <null>",  // returned from f(Integer).
            "42 : int",  // actual to first call.
            "null : <null>",  // actual to second call.
            // third call is used as an expression not an expression statement.
            "this.f(Integer.valueOf(42)) : /java/lang/String",
            // actual to third call.
            "Integer.valueOf(42) : /java/lang/Integer",
            // actual to embedded call.
            "42 : int",
        },
        DECORATE_METHOD_NAMES);

    assertTyped(
        new String[][] {
          {
            "class C {",
            "  void f(int i) {}",
            "  String f(Integer i) { return null; }",
            "  {",
            "    /*(I)V*/f(42);",
            "    /*(Ljava/lang/Integer;)Ljava/lang/String;*/f(null);",
            "    String s = /*(Ljava/lang/Integer;)Ljava/lang/String;*/f(Integer",
            "        ./*(I)Ljava/lang/Integer;*/valueOf(42));",
            "  }",
            "}",
          },
        },
        new String[][] {
          {
            "//C",
            "class C {",
            "  void f(int i) {}",
            "  String f(Integer i) { return null; }",
            "  {",
            "    f(42);",
            "    f(null);",
            "    String s = f(Integer.valueOf(42));",
            "  }",
            "}",
          },
        },
        ExpressionNode.class,
        new String[] {
            "null : <null>",  // returned from f(Integer).
            "42 : int",  // actual to first call.
            "null : <null>",  // actual to second call.
            // third call is used as an expression not an expression statement.
            "f(Integer.valueOf(42)) : /java/lang/String",
            // actual to third call.
            "Integer.valueOf(42) : /java/lang/Integer",
            // actual to embedded call.
            "42 : int",
        },
        DECORATE_METHOD_NAMES);
  }

  @Test
  public static final void testMethodDispatchWithBoxyCasts()
  throws Exception {
    assertTyped(
        new String[][] {
          {
            "class C {",
            "  void f(Integer i) {}",
            "  void g(int i) {}",
            "  {",
            "    /*(Ljava/lang/Integer;)V*/f((java.lang.Integer) (0));",
            "    /*(Ljava/lang/Integer;)V*/f("
            +          "Integer./*(I)Ljava/lang/Integer;*/valueOf(0));",
            "    /*(I)V*/g(0);",
            "    /*(I)V*/g((int) ("
            +          "Integer./*(I)Ljava/lang/Integer;*/valueOf(0)));",
            "  }",
            "}",
          },
        },
        new String[][] {
          {
            "//C",
            "class C {",
            "  void f(Integer i) {}",
            "  void g(int i) {}",
            "  {",
            "    f(0);",
            "    f(Integer.valueOf(0));",
            "    g(0);",
            "    g(Integer.valueOf(0));",
            "  }",
            "}",
          },
        },
        CastExpressionNode.class,
        new String[] {
            "(java.lang.Integer) (0) : null",
            "(int) (Integer.valueOf(0)) : null",
        },
        DECORATE_METHOD_NAMES);
  }

  @Test
  public static final void testMethodShadowing()
  throws Exception {
    assertTyped(
        new String[][] {
          {
            "class C {",
            "  int f(int i) { return 0; }",
            "  int i = /*(I)I*/f(0);",
            "  class I {",
            "    int f(Object o) { return 1; }",
            "    int i = /*(Ljava/lang/Object;)I*/f((java.lang.Object) (0));",
            "  }",
            "}",
          },
        },
        new String[][] {
          {
            "class C {",
            "  int f(int i) { return 0; }",
            "  int i = f(0);",
            "  class I {",
            "    int f(Object o) { return 1; }",
            "    int i = f(0);",
            "  }",
            "}",
          },
        },
        StatementExpressionNode.class,
        new String[] {

        },
        DECORATE_METHOD_NAMES
        );

  }

  @Test
  public static final void testMethodChosenBasedOnTypeParameters()
  throws Exception {
if (false)  // HACK TODO
    assertTyped(
        new String[][] {
          {
            // TODO
          },
        },
        new String[][] {
          {
            "//Foo",
            "public class Foo {",
            "  static <T> T f(T x) {",
            "    System.err.println(\"T \" + x);",
            "    return x;",
            "  }",
            "  static <T> T f(CharSequence x) {",
            "    System.err.println(\"CharSequence \" + x);",
            "    return null;",
            "  }",
            "  public static void main(String... argv) {",
            "    CharSequence c = \"c\";",
            "    String s = \"s\";",
            "    Foo.f(c);",
            "    Foo.f(s);",
            "    Foo.<String>f(s);",
            "  }",
            "}",
          },
        },
        StatementExpressionNode.class,
        new String[] {

        },
        DECORATE_METHOD_NAMES
        );
  }

  @Test
  public static final void testStaticMethodImport()
  throws Exception {
    assertTyped(
        new String[][] {
          {
          },
        },
        new String[][] {
          {
          },
        },
        StatementExpressionNode.class,
        new String[] {

        },
        DECORATE_METHOD_NAMES
        );
  }

  @Test
  public static final void testStaticMethodImportMaskedByLocalDeclaration()
  throws Exception {
    assertTyped(
        new String[][] {
          {
            // TODO
          },
        },
        new String[][] {
          {
          },
        },
        StatementExpressionNode.class,
        new String[] {

        },
        DECORATE_METHOD_NAMES
        );
  }

  @Test
  public static final void testMethodChosenBasedOnParameterizedSuperType()
  throws Exception {
    assertTyped(
        new String[][] {
          {
            // TODO
          },
        },
        new String[][] {
          {
          },
        },
        StatementExpressionNode.class,
        new String[] {

        },
        DECORATE_METHOD_NAMES
        );
  }

  @Test
  public static final void testArgumentTypePromotion() throws Exception {
    assertTyped(
        new String[][] {
          {
            "public class Foo {",
            "  static void f(short s) {",
            "    System.err./*(Ljava/lang/String;)V*/println(\"short\");",
            "  }",
            "  static void f(long l) { System.err./*(Ljava/lang/String;)V*/println(\"long\"); }",
            "  static void f(double d) {",
            "    System.err./*(Ljava/lang/String;)V*/println(\"double\");",
            "  }",
            "  static void f(Integer i) {",
            "    System.err./*(Ljava/lang/String;)V*/println(\"Integer\");",
            "  }",
            "  public static void main(String[] argv) {",
            "    /*(S)V*/f((short) ((byte) 0));",
            "    /*(S)V*/f((short) 0);",
            "    /*(J)V*/f((long) ((char) 0));",
            "    /*(J)V*/f((long) ((int) 0));",
            "    /*(J)V*/f((long) 0);",
            "    /*(D)V*/f((double) ((float) 0));",
            "    /*(D)V*/f((double) 0);",
            "  }",
            "}",
          },
        },
        new String[][] {
          {
            "public class Foo {",
            "  static void f(short s) {",
            "    System.err.println(\"short\");",
            "  }",
            "",
            "  static void f(long l) {",
            "    System.err.println(\"long\");",
            "  }",
            "",
            "  static void f(double d) {",
            "    System.err.println(\"double\");",
            "  }",
            "",
            "  static void f(Integer i) {",
            "    System.err.println(\"Integer\");",
            "  }",
            "",
            "  public static void main(String[] argv) {",
            "    f((byte) 0);",
            "    f((short) 0);",
            "    f((char) 0);",
            "    f((int) 0);",
            "    f((long) 0);",
            "    f((float) 0);",
            "    f((double) 0);",
            "  }",
            "}",
          },
        },
        CastNode.class,
        new String[] {
            // Inserted casts have types attached.
            "short : short",
            "long : long",
            "long : long",
            "double : double",
        },
        DECORATE_METHOD_NAMES
        );
  }

  @Test
  public static final void testVariadicAndIterated()
  throws Exception {
    // TODO
    // void f(int... is) {}
    // void f(int a, int... rest) {}
    // void f(int a, int b, int c) {}
    // { f(); f(0); f(0, 1); f(0, 1, 2); f(0, 1, 2, 3); }
    // Test multiple order of method declarations.
  }

  private static final Decorator DECORATE_METHOD_NAMES = new Decorator() {

    @Override
    public String decorate(BaseNode node) {
      if (node instanceof MethodDescriptorReference) {
        String descriptor = ((MethodDescriptorReference) node)
            .getMethodDescriptor();
        if (descriptor != null) {
          return Java8Comments.blockCommentMinimalSpace(descriptor);
        }
      }
      return null;
    }

  };
}
