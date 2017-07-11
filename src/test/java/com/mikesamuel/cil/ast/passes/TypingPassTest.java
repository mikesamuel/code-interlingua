package com.mikesamuel.cil.ast.passes;

import java.util.List;
import java.util.logging.Logger;

import javax.annotation.Nullable;

import org.junit.Test;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.mikesamuel.cil.ast.NodeI;
import com.mikesamuel.cil.ast.Trees.Decorator;
import com.mikesamuel.cil.ast.j8.CastExpressionNode;
import com.mikesamuel.cil.ast.j8.CastNode;
import com.mikesamuel.cil.ast.j8.ClassLiteralNode;
import com.mikesamuel.cil.ast.j8.ClassOrInterfaceTypeNode;
import com.mikesamuel.cil.ast.j8.ConditionalExpressionNode;
import com.mikesamuel.cil.ast.j8.ExpressionNode;
import com.mikesamuel.cil.ast.j8.J8BaseNode;
import com.mikesamuel.cil.ast.j8.J8BinaryOp;
import com.mikesamuel.cil.ast.j8.J8ExpressionNameReference;
import com.mikesamuel.cil.ast.j8.J8FileNode;
import com.mikesamuel.cil.ast.j8.J8MethodDescriptorReference;
import com.mikesamuel.cil.ast.j8.J8Typed;
import com.mikesamuel.cil.ast.j8.J8WholeType;
import com.mikesamuel.cil.ast.j8.Java8Comments;
import com.mikesamuel.cil.ast.j8.PrimaryNode;
import com.mikesamuel.cil.ast.j8.StatementExpressionNode;
import com.mikesamuel.cil.ast.j8.UnqualifiedClassInstanceCreationExpressionNode;
import com.mikesamuel.cil.ast.meta.MethodDescriptor;
import com.mikesamuel.cil.ast.meta.Name;
import com.mikesamuel.cil.ast.meta.StaticType;
import com.mikesamuel.cil.ast.meta.StaticType.TypePool;
import com.mikesamuel.cil.ast.meta.TypeInfoResolver;
import com.mikesamuel.cil.ast.meta.TypeSpecification;
import com.mikesamuel.cil.ast.passes.PassTestHelpers.LoggableOperation;
import com.mikesamuel.cil.parser.Unparse.UnparseVerificationException;

import junit.framework.TestCase;

@SuppressWarnings("javadoc")
public final class TypingPassTest extends TestCase {

  private static void assertTyped(
      String[][] inputs,
      Class<? extends NodeI<?, ?, ?>> target,
      String[] expectedTypes,
      String... expectedErrors)
  throws UnparseVerificationException {
    assertTyped(null, inputs, target, expectedTypes, null, expectedErrors);
  }

  private static void assertTyped(
      @Nullable String[][] expectedWithCasts,
      String[][] inputs,
      @Nullable Class<? extends NodeI<?, ?, ?>> target,
      @Nullable String[] expectedTypes,
      @Nullable Decorator decorator,
      String... expectedErrors)
  throws UnparseVerificationException {
    ImmutableList<J8FileNode> processedFiles =
        PassTestHelpers.expectErrors(
            new LoggableOperation<ImmutableList<J8FileNode>>() {

              @Override
              public ImmutableList<J8FileNode> run(Logger logger) {
                ImmutableList<J8FileNode> files =
                    PassTestHelpers.parseCompilationUnits(logger, inputs);
                DeclarationPass dp = new DeclarationPass(logger);
                TypeInfoResolver typeInfoResolver =
                    dp.run(files).typeInfoResolver;

                ExpressionScopePass scopePass = new ExpressionScopePass(
                    typeInfoResolver, logger);
                scopePass.run(files);

                DisambiguationPass disambigPass = new DisambiguationPass(
                    typeInfoResolver, logger, false);
                files = disambigPass.run(files);

                TypePool typePool = new TypePool(typeInfoResolver);
                ClassMemberPass classMemberPass = new ClassMemberPass(
                    logger, typePool);
                classMemberPass.run(files);

                TypingPass tp = new TypingPass(logger, typePool, true);
                return tp.run(files);
              }

            },
            expectedErrors);


    if (expectedWithCasts != null) {
      String got = PassTestHelpers.serializeNodes(processedFiles, decorator);
      String want = decorator == null
          ? PassTestHelpers.normalizeCompilationUnitSource(expectedWithCasts)
            .get()
          : PassTestHelpers.joinExpectedLines(expectedWithCasts);
      assertEquals(want, got);
    }

    if (target != null) {
      List<NodeI<?, ?, ?>> typed = Lists.newArrayList();
      for (J8FileNode fileNode : processedFiles) {
        for (NodeI<?, ?, ?> n : ((J8BaseNode) fileNode).finder(target).find()) {
          if (n instanceof J8Typed) {
            typed.add(n);
          } else if (n instanceof J8WholeType) {
            typed.add(n);
          } else {
            boolean found = false;
            for (J8Typed t
                   : ((J8BaseNode) n).finder(J8Typed.class)
                   .exclude(J8WholeType.class).exclude(J8Typed.class)
                   .find()) {
              typed.add(t);
              found = true;
              break;
            }
            if (!found) {
              for (J8WholeType t
                     : ((J8BaseNode) n).finder(J8WholeType.class)
                     .exclude(J8WholeType.class)
                     .find()) {
                typed.add(t);
                break;
              }
            }
          }
        }
      }
      ImmutableList.Builder<String> got = ImmutableList.builder();
      for (NodeI<?, ?, ?> t : typed) {
        StaticType typ = (t instanceof J8Typed)
          ? ((J8Typed)     t).getStaticType()
          : ((J8WholeType) t).getStaticType();
        got.add(
            PassTestHelpers.serializeNodes(
                ImmutableList.of((J8BaseNode) t), null)
            + " : " + typ);
      }

      assertEquals(
          Joiner.on("\n\n").join(expectedTypes),
          Joiner.on("\n\n").join(got.build()));
    }

    // Sanity check that the processed CUs have associated types, even if only
    // the error type, with every Typed and WholeType.
    List<J8BaseNode> untyped = Lists.newArrayList();
    for (J8FileNode fileNode : processedFiles) {
      sanityCheckUntyped((J8BaseNode) fileNode, untyped);
    }
    if (!untyped.isEmpty()) {
      StringBuilder msg = new StringBuilder(
          "Some nodes are missing type information");
      for (J8BaseNode untypedNode : untyped) {
        msg.append('\n')
            .append(untypedNode.getVariant())
            .append(" @ ")
            .append(untypedNode.getSourcePosition());
      }
      fail(msg.toString());
    }
  }

  private static void sanityCheckUntyped(
      J8BaseNode node, List<? super J8BaseNode> out) {
    if (node instanceof J8Typed) {
      J8Typed typed = (J8Typed) node;
      if (typed.getStaticType() == null) {
        out.add(node);
      }
    } else if (node instanceof J8WholeType) {
      J8WholeType wholeType = (J8WholeType) node;
      if (wholeType.getStaticType() == null) {
        out.add(node);
      }
    }

    for (J8BaseNode child : node.getChildren()) {
      if (!(node instanceof ClassOrInterfaceTypeNode
            && child instanceof ClassOrInterfaceTypeNode)) {
        sanityCheckUntyped(child, out);
      }
    }
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
            "  public C() {}",
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
            "  public C() {}",
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
            "  public C() {}",
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
  public static final void testMethodDispatchWithBoxyCasts() throws Exception {
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
            "  public C() {}",
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
            "(java.lang.Integer) (0) : /java/lang/Integer",
            "(int) (Integer.valueOf(0)) : int",
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
            "    public I() {}",
            "  }",
            "  public C() {}",
            "}",
          },
        },
        new String[][] {
          {
            "//C",
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
  public static final void testParameterizedMethodCalls()
  throws Exception {
    assertTyped(
        new String[][] {
          {
            "import java.util.Collections;",
            "class C {",
            "  {",
            "    Collections./*()Ljava/util/List;*/emptyList();",
            "    Collections.<String> /*()Ljava/util/Set;*/emptySet();",
            "    Collections.<? extends Long> /*()Ljava/util/Set;*/emptySet();",
            "    Collections.<?> /*()Ljava/util/Set;*/emptySet();",
            "  }",
            "  public C() {}",
            "}",
          },
        },
        new String[][] {
          {
            "//C",
            "import java.util.Collections;",
            "class C {",
            "  {",
            "    Collections.emptyList();",
            "    Collections.<String>emptySet();",
            "    Collections.<? extends Long>emptySet();",
            "    Collections.<?>emptySet();",
            "  }",
            "}",
          },
        },
        StatementExpressionNode.class,
        new String[] {
            "Collections.emptyList()"
            + " : /java/util/List<? extends /java/lang/Object>",
            "Collections.<String> emptySet()"
            + " : /java/util/Set</java/lang/String>",
            "Collections.<? extends Long> emptySet()"
            + " : /java/util/Set<? extends /java/lang/Long>",
            "Collections.<?> emptySet()"
            + " : /java/util/Set<? extends /java/lang/Object>",
        },
        DECORATE_METHOD_NAMES
        );
  }

  @Test
  public static final void testMethodChosenBasedOnTypeParameters()
  throws Exception {
    assertTyped(
        new String[][] {
          {
            "public class Foo {",
            "  static <T> T f(T x) {",
            "    System.err./*(Ljava/lang/String;)V*/println(\"T\" + x);",
            "    return x;",
            "  }",
            "  static <T> T f(CharSequence x) {",
            "    System.err./*(Ljava/lang/String;)V*/println(\"CharSequence \" + x);",
            "    return null;",
            "  }",
            "  public static void main(String... argv) {",
            "    CharSequence c = (java.lang.CharSequence) (\"c\");",
            "    String s = \"s\";",
            "    Foo./*(Ljava/lang/CharSequence;)Ljava/lang/Object;*/f(c);",
            "    Foo./*(Ljava/lang/CharSequence;)Ljava/lang/Object;*/f((java.lang",
            "            .CharSequence) (s));",
            // Per experiment with javac, this binds to the second overload
            // despite the fact that, with type variable substitution,
            // the String formal type is more specific than CharSequence.
            // TODO: This should be
//          "    Foo.<String> /*(Ljava/lang/CharSequence;)Ljava/lang/Object;*/"
//          + "f((java.lang",
//          "            .CharSequence) (s));",
            // not
            "    Foo.<String> /*(Ljava/lang/Object;)Ljava/lang/Object;*/f(s);",
            "  }",
            "  public Foo() {}",
            "}",
          },
        },
        new String[][] {
          {
            "//Foo",
            "public class Foo {",
            // TODO: I believe this passes spuriously because (T x) is rejected
            // out of hand before it gets to checking specificity.
            "  static <T> T f(T x) {",
            "    System.err.println(\"T\" + x);",
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
            // This goes to f(CharSequence) despite <T> being more specific
            // when bound to String
            "    Foo.<String>f(s);",
            "  }",
            "}",
          },
        },
        StatementExpressionNode.class,
        new String[] {
            "System.err.println(\"T\" + x) : void",
            "System.err.println(\"CharSequence \" + x) : void",
            "Foo.f(c) : /java/lang/Object",
            "Foo.f((java.lang.CharSequence) (s)) : /java/lang/Object",
            // TODO: should cast s to CharSequence
            "Foo.<String> f(s) : /java/lang/String",
        },
        DECORATE_METHOD_NAMES
        );
  }

  @Test
  public static final void testSubtraction()
  throws Exception {
    assertTyped(
        new String[][] {
          {
            "public class Foo {",
            "  static void f(byte b) {",
            "    System.err./*(Ljava/lang/String;)V*/println(\"b\");",
            "  }",
            "  static void f(short s) { System.err./*(Ljava/lang/String;)V*/println(\"s\"); }",
            "  static void f(char c) { System.err./*(Ljava/lang/String;)V*/println(\"c\"); }",
            "  static void f(int i) { System.err./*(Ljava/lang/String;)V*/println(\"i\"); }",
            "  static void f(long j) { System.err./*(Ljava/lang/String;)V*/println(\"j\"); }",
            "  static void f(float f) { System.err./*(Ljava/lang/String;)V*/println(\"f\"); }",
            "  static void f(double d) { System.err./*(Ljava/lang/String;)V*/println(\"d\"); }",
            "  public static void main(String... argv) {",
            "    byte b = (byte) 0;",
            "    short s = (short) 0;",
            "    char c = (char) 0;",
            "    int i = 0;",
            "    long j = (long) (0);",
            "    float f = (float) (0);",
            "    double d = (double) (0);",
            "    /*(I)V*/f((int) b - (int) b);",
            "    /*(I)V*/f((int) b - i);",
            "    /*(I)V*/f((int) s - (int) s);",
            "    /*(I)V*/f(i - (int) s);",
            "    /*(I)V*/f((int) c - (int) c);",
            "    /*(I)V*/f((int) c - i);",
            "    /*(I)V*/f(i - i);",
            "    /*(J)V*/f(j - j);",
            "    /*(J)V*/f((long) i - j);",
            "    /*(F)V*/f(f - f);",
            "    /*(F)V*/f(f - (float) i);",
            "    /*(D)V*/f(d - d);",
            "    /*(D)V*/f((double) i - d);",
            "  }",
            "  public Foo() {}",
            "}",
          },
        },
        new String[][] {
          {
            "public class Foo {",
            "  static void f(byte b)   { System.err.println(\"b\"); }",
            "  static void f(short s)  { System.err.println(\"s\"); }",
            "  static void f(char c)   { System.err.println(\"c\"); }",
            "  static void f(int i)    { System.err.println(\"i\"); }",
            "  static void f(long j)   { System.err.println(\"j\"); }",
            "  static void f(float f)  { System.err.println(\"f\"); }",
            "  static void f(double d) { System.err.println(\"d\"); }",
            "",
            "  public static void main(String... argv) {",
            "    byte b = (byte) 0;",
            "    short s = (short) 0;",
            "    char c = (char) 0;",
            "    int i = 0;",
            "    long j = 0;",
            "    float f = 0;",
            "    double d = 0;",
            "",
            "    f(b - b);",
            "    f(b - i);",
            "    f(s - s);",
            "    f(i - s);",
            "    f(c - c);",
            "    f(c - i);",
            "    f(i - i);",
            "    f(j - j);",
            "    f(i - j);",
            "    f(f - f);",
            "    f(f - i);",
            "    f(d - d);",
            "    f(i - d);",
            "  }",
            "}",
          }
        },
        null,
        null,
        DECORATE_METHOD_NAMES);
  }

  @Test
  public static final void testStaticMethodImport()
  throws Exception {
    assertTyped(
        new String[][] {
          {
            "package foo;",
            "import static bar.Bar.f;",
            "import static bar.Baz.f;",
            "import static bar.Boo.*;",
            "public class Foo {",
            "  public static void main(String... argv) {",
            "    /* /bar/Bar(I)I*/",
            "    f(0);",
            "    /* /bar/Baz(Ljava/lang/Integer;)I*/",
            "    f(null);",
            "    /* /bar/Bar(I)I*/",
            "    f((int) ((short) 0));",
            "  }",
            "  public Foo() {}",
            "}",
          },
          {
            "package bar;",
            "public class Bar {",
            "  public static int f(int i) {",
            "    System.err.",
            "    /* /java/io/PrintStream(Ljava/lang/String;)V*/",
            "    println(\"Bar.f\");",
            "    return 0;",
            "  }",
            "  public Bar() {}",
            "}",
          },
          {
            "package bar;",
            "public class Baz {",
            "  public static int f(Integer i) {",
            "    System.err.",
            "    /* /java/io/PrintStream(Ljava/lang/String;)V*/",
            "    println(\"Baz.f\");",
            "    return 0;",
            "  }",
            "  public Baz() {}",
            "}",
          },
          {
            "package bar;",
            "public class Boo {",
            "  public static int f(short i) {",
            "    System.err.",
            "    /* /java/io/PrintStream(Ljava/lang/String;)V*/",
            "    println(\"Boo.f\");",
            "    return 0;",
            "  }",
            "  public Boo() {}",
            "}",
          },
        },
        new String[][] {
          {
            "package foo;",
            "import static bar.Bar.f;",
            "import static bar.Baz.f;",
            "import static bar.Boo.*;",  // Masked by explicit
            "",
            "public class Foo {",
            "  public static void main(String... argv) {",
            "    f(0);",
            "    f(null);",
            "    f((short) 0);",  // Does not resolve to Boo.
            "  }",
            "}",
          },
          {
            "//Bar",
            "package bar;",
            "public class Bar {",
            "  public static int f(int i) {",
            "    System.err.println(\"Bar.f\");",
            "    return 0;",
            "  }",
            "}",
          },
          {
            "//Baz",
            "package bar;",
            "public class Baz {",
            "  public static int f(Integer i) {",
            "    System.err.println(\"Baz.f\");",
            "    return 0;",
            "  }",
            "}",
          },
          {
            "//Boo",
            "package bar;",
            "public class Boo {",
            "  public static int f(short i) {",
            "    System.err.println(\"Boo.f\");",
            "    return 0;",
            "  }",
            "}",
          },
        },
        null,
        null,
        DECORATE_METHOD_NAMES_INCL_CONTAINER
        );
  }

  @Test
  public static final void testStaticWildcardImports()
  throws Exception {
    assertTyped(
        new String[][] {
          {
            "package foo;",
            "import static foo.Boo.*;",
            "public class Foo {",
            "  public static void main(String... argv) {",
            "    /* /foo/Boo(I)V*/",
            "    f(0);",
            "    /* /foo/Boo(Ljava/lang/Object;)V*/",
            "    f((java.lang.Object) (x));",
            "  }",
            "  public Foo() {}",
            "}",
          },
          {
            "package foo;",
            "import static java.lang.System.err;",
            "public class Boo {",
            "  static final Integer x = null;",
            "  static void f(int i) {",
            "    err.",
            "    /* /java/io/PrintStream(Ljava/lang/String;)V*/",
            "    println(\"int\");",
            "  }",
            "  static void f(Object o) {",
            "    err.",
            "    /* /java/io/PrintStream(Ljava/lang/Object;)V*/",
            "    println(o);",
            "  }",
            "  public Boo() {}",
            "}",
          },
        },
        new String[][] {
          {
            "//Foo",
            "package foo;",
            "import static foo.Boo.*;",
            "",
            "public class Foo {",
            "  public static void main(String... argv) {",
            "    f(0);",
            "    f(x);",
            "  }",
            "}",
          },
          {
            "//Boo",
            "package foo;",
            "import static java.lang.System.err;",
            "",
            "public class Boo {",
            "  static final Integer x = null;",
            "  static void f(int i) {",
            "    err.println(\"int\");",
            "  }",
            "  static void f(Object o) {",
            "    err.println(o);",
            "  }",
            "}",
          },
        },
        null, null,
        DECORATE_METHOD_NAMES_INCL_CONTAINER
        );
  }

  @Test
  public static final void testQualifiedNew() throws Exception {
    assertTyped(
        new String[][] {
          {
            "class C {",
            "  { System.err.println((java.lang.Object) (d.new E()));",
            "  }",
            "  static D d = new D();",
            "  public C() {}",
            "}"
          },
          {
            "class D {",
            "  class E {",
            "    public E() {}",
            "  }",
            "  public D() {}",
            "}",
          },
        },
        new String[][] {
          {
            "//C",
            "class C {",
            "  {",
            "    System.err.println(d.new E());",
            "  }",
            "  static D d = new D();",
            "  public C() {}",
            "}"
          },
          {
            "//D",
            "class D {",
            "  class E {",
            "    public E() {}",
            "  }",
            "  public D() {}",
            "}",
          },
        },
        UnqualifiedClassInstanceCreationExpressionNode.class,
        new String[] {
            "E : /D$E",
            "D : /D",
        },
        null);
  }

  @Test
  public static final void testMethodSpecificityIgnoresParameterizedSuperType()
  throws Exception {
    assertTyped(
        new String[][] {
          {
            "public class Foo extends Bar<String> {",
            "  public static void main(String[] args) {",
            // Even though the signature, post template variable substitution,
            // of the first f is f(String) which is more specific than
            // f(CharSequence), java still chooses the latter.
            "    /*()V*/new Foo()./*(Ljava/lang/CharSequence;)"
            +        "Ljava/lang/Object;*/f((java",
            "            .lang.CharSequence) (\"\"));",
            "  }",
            "  public Foo() {}",
            "}",
          },
          {
            "class Bar<T> {",
            "  <T> T f(T x) {",
            "    System.err."
            +   "/*(Ljava/lang/String;)V*/println(\"Bar.f(T)\");",
            "    return null;",
            "  }",
            "  <T> T f(CharSequence cs) {",
            "    System.err."
            +   "/*(Ljava/lang/String;)V*/println(\"Bar.f(CharSequence)\");",
            "    return null;",
            "  }",
            "  public Bar() {}",
            "}",
          },
        },
        new String[][] {
          {
            "//Foo",
            "public class Foo extends Bar<String> {",
            "  public static void main(String[] args) {",
            "    new Foo().f(\"\");",
            "  }",
            "}",
          },
          {
            "class Bar<T> {",
            "",
            "  <T> T f(T x) {",
            "    System.err.println(\"Bar.f(T)\");",
            "    return null;",
            "  }",
            "",
            "  <T> T f(CharSequence cs) {",
            "    System.err.println(\"Bar.f(CharSequence)\");",
            "    return null;",
            "  }",
            "}",
          },
        },
        StatementExpressionNode.class,
        new String[] {
            // TODO: infer type for <T> and use that.
            "new Foo().f((java.lang.CharSequence) (\"\")) : /java/lang/Object",
            "System.err.println(\"Bar.f(T)\") : void",
            "System.err.println(\"Bar.f(CharSequence)\") : void",
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
            "  static void f(long l) { "
            +   "System.err./*(Ljava/lang/String;)V*/println(\"long\"); "
            + "}",
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
            "  public Foo() {}",
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
    assertTyped(
        new String[][] {
          {
            "class C {",
            "  void f(int... is) {}",
            "  void f(int a, int... rest) {}",
            "  void f(int a, int b, int c) {}",
            "  {",
            "    /*([I)V*/f();",
            "    /*(I[I)V*/f(0);",
            "    /*(I[I)V*/f(0, 1);",
            "    /*(III)V*/f(0, 1, 2);",
            "    /*(I[I)V*/f(0, 1, 2, 3);",
            "    /*([I)V*/f(null);",
            "    /*([I)V*/f(new int[] { 1, 2, 3, });",
            "  }",
            "  public C() {}",
            "}",
          },
        },
        new String[][] {
          {
            "//C",
            "class C {",
            "  void f(int... is) {}",
            "  void f(int a, int... rest) {}",
            "  void f(int a, int b, int c) {}",
            "  {",
            "    f();",  // ?
            "    f(0);",
            "    f(0, 1);",
            "    f(0, 1, 2);",
            "    f(0, 1, 2, 3);", // ?
            "    f(null);", // ?
            "    f(new int[] { 1, 2, 3 });",
            "  }",
            "}",
          },
        },
        null,
        null,
        DECORATE_METHOD_NAMES
        );

    // Same test but with opposite method declaration order to flush out errors
    // due to fixating on order of declaration.
    assertTyped(
        new String[][] {
          {
            "class C {",
            "  void f(int a, int b, int c) {}",
            "  void f(int a, int... rest) {}",
            "  void f(int... is) {}",
            "  {",
            "    /*([I)V*/f();",
            "    /*(I[I)V*/f(0);",
            "    /*(I[I)V*/f(0, 1);",
            "    /*(III)V*/f(0, 1, 2);",
            "    /*(I[I)V*/f(0, 1, 2, 3);",
            "    /*([I)V*/f(null);",
            "    /*([I)V*/f(new int[] { 1, 2, 3, });",
            "  }",
            "  public C() {}",
            "}",
          },
        },
        new String[][] {
          {
            "//C",
            "class C {",
            "  void f(int a, int b, int c) {}",
            "  void f(int a, int... rest) {}",
            "  void f(int... is) {}",
            "  {",
            "    f();",  // ?
            "    f(0);",
            "    f(0, 1);",
            "    f(0, 1, 2);",
            "    f(0, 1, 2, 3);", // ?
            "    f(null);", // ?
            "    f(new int[] { 1, 2, 3 });",
            "  }",
            "}",
          },
        },
        null,
        null,
        DECORATE_METHOD_NAMES
        );

  }

  @Test
  public static final void testClassLiterals() throws Exception {
    assertTyped(
        null,
        new String[][] {
          {
            "//C",
            "class C {",
            "  static class Outer<T> {",
            "    class Inner<U> {}",
            "  }",
            "  Class<?> tc = C.class;",
            "  Class<?> tac = C[].class;",
            "  Class<?> nc = ((int.class));",
            "  Class<?> nac = double[][].class;",
            "  Class<?> bc = boolean.class;",
            "  Class<?> bac = boolean[].class;",
            "  Class<?> vc = (void.class);",
            "  Class<?> pc = C.Outer.Inner.class;",
            "}",
          },
        },
        ClassLiteralNode.class,
        new String[] {
            "C.class : /java/lang/Class</C>",
            "C[].class : /java/lang/Class</C[]>",
            "int.class : /java/lang/Class</java/lang/Integer>",
            "double[][].class : /java/lang/Class</java/lang/Double.TYPE[][]>",
            "boolean.class : /java/lang/Class</java/lang/Boolean>",
            "boolean[].class : /java/lang/Class</java/lang/Boolean.TYPE[]>",
            "void.class : /java/lang/Class</java/lang/Void>",

            "C.Outer.Inner.class : /java/lang/Class<"
            + "/C$Outer<? extends /java/lang/Object>"
            + "$Inner<? extends /java/lang/Object>>",
        },
        null);
  }

  @Test
  public static final void testArrayAccess() throws Exception {
    assertTyped(
        null,
        new String[][] {
          {
            "//C",
            "class C {",
            "  int f(int[] is) { return is[0]; }",
            "  int f(int[][] is) { return is[0]; }",
            "  int f(Object... os) { return os[Integer.valueOf(0)]; }",
            "}",
          },
        },
        PrimaryNode.class,
        new String[] {
            "is[0] : int",
            "is[0] : int[]",
            // The index is unboxed.
            "os[(int) (Integer.valueOf(0))] : /java/lang/Object",
            "Integer.valueOf(0) : /java/lang/Integer",
        },
        null);
  }

  @Test
  public static final void testLogicalOperators() throws Exception {
    assertTyped(
        new String[][] {
          {
            "class C {",
            "  {",
            "    boolean f = false, t = true,",
            "        b = ((f || t && f) | t & Boolean.TRUE) ^ f,",
            "        c = Boolean.FALSE | b;",
            "  }",
            "}",
          },
        },
        J8BinaryOp.class,
        new String[] {
            "((f || t && f) | t & (boolean) Boolean.TRUE) ^ f : boolean",
            "(f || t && f) | t & (boolean) Boolean.TRUE : boolean",
            "f || t && f : boolean",
            "t && f : boolean",
            "t & (boolean) Boolean.TRUE : boolean",
            "(boolean) Boolean.FALSE | b : boolean",
        });
  }

  @Test
  public static final void testBitwiseOperators() throws Exception {
    assertTyped(
        new String[][] {
          {
            "class C {",
            "  {",
            "    int i = 0, x = 4, n = -1,",
            "        k = (i | x & Integer.valueOf(0)) ^ n,",
            "        c = Integer.valueOf(3) | k;",
            "  }",
            "}",
          },
        },
        J8BinaryOp.class,
        new String[] {
            "(i | x & (int) Integer.valueOf(0)) ^ n : int",
            "i | x & (int) Integer.valueOf(0) : int",
            "x & (int) Integer.valueOf(0) : int",
            "(int) Integer.valueOf(3) | k : int",
        });
    // TODO: byte, char, short, long, double, float, boolean
  }

  @Test
  public static final void testShiftOperators() throws Exception {
    assertTyped(
        new String[][] {
          {
            "class C {",
            "  C() {}",
            "  void f(byte b)   { System.err.println('b'); }",
            "  void f(char c)   { System.err.println('c'); }",
            "  void f(short s)  { System.err.println('s'); }",
            "  void f(int i)    { System.err.println('i'); }",
            "  void f(long j)   { System.err.println('j'); }",
            "  void f(float f)  { System.err.println('f'); }",
            "  void f(double d) { System.err.println('d'); }",
            "  {",
            "    byte b = (byte) 0;",
            "    short s = (short) 0;",
            "    char c = '\\0';",
            "    int i = 0;",
            "    long j = (long) (0);",
            "    float f = (float) (0);",
            "    double d = (double) (0);",
            "    f((int) b << (int) b);",
            "    f((int) c >> (int) c);",
            "    f((int) s >>> (int) s);",
            "    f(i << i);",
            "    f(j << j);",
            "    f(f << f);",
            "    f(d << d);",
            "    f(i << (int) b);",
            "    f(i >> (int) c);",
            "    f(i << (int) s);",
            "    f(i << j);",
            "    f(i >>> f);",
            "    f(i << d);",
            "    f((int) b << i);",
            "    f((int) c << i);",
            "    f((int) s << i);",
            "    f(j << i);",
            "    f(f >> i);",
            "    f(d >>> i);",
            "  }",
            "}",
          },
        },
        new String[][] {
          {
            "//C",
            "class C {",
            "  C() {}",
            "  void f(byte b)   { System.err.println('b'); }",
            "  void f(char c)   { System.err.println('c'); }",
            "  void f(short s)  { System.err.println('s'); }",
            "  void f(int i)    { System.err.println('i'); }",
            "  void f(long j)   { System.err.println('j'); }",
            "  void f(float f)  { System.err.println('f'); }",
            "  void f(double d) { System.err.println('d'); }",
            "  {",
            "    byte b = (byte) 0;",
            "    short s = (short) 0;",
            "    char c = '\\0';",
            "    int i = 0;",
            "    long j = 0;",
            "    float f = 0;",
            "    double d = 0;",
            "",
            "    f(b << b);",
            "    f(c >> c);",
            "    f(s >>> s);",
            "    f(i << i);",
            "    f(j << j);",
            "    f(f << f);",
            "    f(d << d);",
            "",
            "    f(i << b);",
            "    f(i >> c);",
            "    f(i << s);",
            "    f(i << j);",
            "    f(i >>> f);",
            "    f(i << d);",
            "",
            "    f(b << i);",
            "    f(c << i);",
            "    f(s << i);",
            "    f(j << i);",
            "    f(f >> i);",
            "    f(d >>> i);",
            "  }",
            "}",
          },
        },
        J8BinaryOp.class,
        new String[] {
          "(int) b << (int) b : int",
          "(int) c >> (int) c : int",
          "(int) s >>> (int) s : int",
          "i << i : int",
          "j << j : long",
          "f << f : /error/ErrorType.TYPE",
          "d << d : /error/ErrorType.TYPE",
          "i << (int) b : int",
          "i >> (int) c : int",
          "i << (int) s : int",
          "i << j : int",
          "i >>> f : /error/ErrorType.TYPE",
          "i << d : /error/ErrorType.TYPE",
          "(int) b << i : int",
          "(int) c << i : int",
          "(int) s << i : int",
          "j << i : long",
          "f >> i : /error/ErrorType.TYPE",
          "d >>> i : /error/ErrorType.TYPE",
        },
        null,
        "//C:25+6-12: Expected integral shift operands not float * float",
        "//C:26+6-12: Expected integral shift operands not double * double",
        "//C:32+6-13: Expected integral shift operands not int * float",
        "//C:33+6-12: Expected integral shift operands not int * double",
        "//C:39+6-12: Expected integral shift operands not float * int",
        "//C:40+6-13: Expected integral shift operands not double * int");
  }

  @Test
  public static final void testEqualityOperators() throws Exception {
    assertTyped(
        new String[][] {
          {
            "import java.io.Serializable;",
            "",
            "public class C {",
            "  static void f(boolean z) { System.err.println('z'); }",
            "  static void f(byte b)    { System.err.println('b'); }",
            "  static void f(char c)    { System.err.println('c'); }",
            "  static void f(short s)   { System.err.println('s'); }",
            "  static void f(int i)     { System.err.println('i'); }",
            "  static void f(long j)    { System.err.println('j'); }",
            "  static void f(float f)   { System.err.println('f'); }",
            "  static void f(double d)  { System.err.println('d'); }",
            "  static void f(Object o)  { System.err.println('o'); }",
            "",
            "  public static void main(String... argv) {",
            "    boolean z = true;",
            "    byte b = (byte) 0;",
            "    short s = (short) 0;",
            "    char c = '\\0';",
            "    int i = 0;",
            "    long j = (long) (0);",
            "    float f = (float) (0);",
            "    double d = (double) (0);",
            "    Object o = new Object();",
            "    Integer I = new Integer(0);",
            "    Boolean Z = new Boolean(true);",
            "",
            "    f(z != z);",
            "    f((int) b == (int) b);",
            "    f((int) s != (int) s);",
            "    f((int) c == (int) c);",
            "    f(i != i);",
            "    f(j == j);",
            "    f(f != f);",
            "    f(d == d);",
            "    f(o != o);",
            "    f(null == null);",
            "",
            "    f(i == (int) b);",
            "    f(i != (int) s);",
            "    f(i == (int) c);",
            "    f((long) i != j);",
            "    f((float) i == f);",
            "    f((double) i != d);",
            "    f(i == o);",
            "    f(i != null);",
            "",
            "    f((int) b == i);",
            "    f((int) s != i);",
            "    f((int) c == i);",
            "    f(j != (long) i);",
            "    f(f == (float) i);",
            "    f(d != (double) i);",
            "    f(o == i);",
            "    f(null != i);",
            "",
            "    f(null != z);",
            "    f(z != o);",
            "    f(null != o);",
            "    f(o != null);",
            "",
            "    f((int) I == i);",
            "    f(z == (boolean) Z);",
            "    f(I == Z);",
            "    f((boolean) Z == i);",  // Don't care whether Z unboxed
            "  }",
            "  public C() {}",
            "}",
          },
        },
        new String[][] {
          {
            "//C",
            "import java.io.Serializable;",
            "",
            "public class C {",
            "  static void f(boolean z) { System.err.println('z'); }",
            "  static void f(byte b)    { System.err.println('b'); }",
            "  static void f(char c)    { System.err.println('c'); }",
            "  static void f(short s)   { System.err.println('s'); }",
            "  static void f(int i)     { System.err.println('i'); }",
            "  static void f(long j)    { System.err.println('j'); }",  // L10
            "  static void f(float f)   { System.err.println('f'); }",
            "  static void f(double d)  { System.err.println('d'); }",
            "  static void f(Object o)  { System.err.println('o'); }",
            "",
            "  public static void main(String... argv) {",
            "    boolean z = true;",
            "    byte b = (byte) 0;",
            "    short s = (short) 0;",
            "    char c = '\\0';",
            "    int i = 0;",  // L20
            "    long j = 0;",
            "    float f = 0;",
            "    double d = 0;",
            "    Object o = new Object();",
            "    Integer I = new Integer(0);",
            "    Boolean Z = new Boolean(true);",
            "",
            "    f(z != z);",
            "    f(b == b);",
            "    f(s != s);",  // L30
            "    f(c == c);",
            "    f(i != i);",
            "    f(j == j);",
            "    f(f != f);",
            "    f(d == d);",
            "    f(o != o);",
            "    f(null == null);",
            "",
            "    f(i == b);",
            "    f(i != s);",  // L40
            "    f(i == c);",
            "    f(i != j);",
            "    f(i == f);",
            "    f(i != d);",
            "    f(i == o);",  // disjoint L45
            "    f(i != null);",  // disjoint L46
            "",
            "    f(b == i);",
            "    f(s != i);",
            "    f(c == i);",  // L50
            "    f(j != i);",
            "    f(f == i);",
            "    f(d != i);",
            "    f(o == i);",  // disjoint L54
            "    f(null != i);",  // disjoint L55
            "",
            "    f(null != z);",  // disjoint L57
            "    f(z != o);",  // disjoint L58
            "",
            "    f(null != o);", // L60
            "    f(o != null);",
            "",
            "    f(I == i);",
            "    f(z == Z);",
            "    f(I == Z);",  // disjoint L65
            "    f(Z == i);",  // disjoint L66
            "  }",
            "}",
          },
        },
        null,
        null,
        null,
        "//C:45+11-12: Cannot unbox /java/lang/Object",
        "//C:46+11-15: Invalid operand of type <null>",
        "//C:54+6-7: Cannot unbox /java/lang/Object",
        "//C:55+6-10: Invalid operand of type <null>",
        "//C:57+6-10: Invalid operand of type <null>",
        "//C:58+11-12: Cannot unbox /java/lang/Object",
        "//C:65+6-12: Incompatible types for comparison"
        + " /java/lang/Integer * /java/lang/Boolean",
        "//C:66+6-12: Incompatible types for comparison boolean * int");
  }

  @Test
  public static final void testRelationalOperators() throws Exception {
    assertTyped(
        new String[][] {
          {
            "//C",
            "import java.io.Serializable;",
            "",
            "public class C {",
            "  static void f(boolean z) { System.err.println('z'); }",
            "  static void f(Object o)  { System.err.println('o'); }",
            "",
            "  public static void main(String... argv) {",
            "    boolean z = true;",
            "    byte b = (byte) 0;",  // L10
            "    short s = (short) 0;",
            "    char c = '\0';",
            "    int i = 0;",
            "    long j = (long) (0);",
            "    float f = (float) (0);",
            "    double d = (double) (0);",
            "    Long J = new Long((long) (1));",
            "    Integer I = new Integer(0);",
            "    Boolean Z = new Boolean(true);",
            "",  // L20
            "    f(z < z);",  // L21 incomparable
            "    f((int) b > (int) b);",
            "    f((int) s <= (int) s);",
            "    f((int) c >= (int) c);",
            "    f(i < i);",
            "    f(j > j);",
            "    f(f <= f);",
            "    f(d >= d);",
            "    f((long) J < (long) J);",
            "    f(null > null);",  // L30 incomparable
            "",
            "    f(i >= (int) b);",
            "    f(i <= (int) s);",
            "    f(i > (int) c);",
            "    f((long) i < j);",
            "    f((float) i >= f);",
            "    f((double) i <= d);",
            "    f((long) i > (long) J);",
            "    f(i < null);",  // L39 incomporable
            "",  // L40
            "    f((int) b >= i);",
            "    f((int) s <= i);",
            "    f((int) c > i);",
            "    f(j < (long) i);",
            "    f(f >= (float) i);",
            "    f(d <= (double) i);",
            "    f((long) J > (long) i);",
            "    f(null < i);",  // L48 incomparable
            "",
            "    f(null <= z);",  // L50 incomparable
            "    f(z < J);",  // L51 incomparable
            "",
            "    f(null <= J);",  // L53 incomparable
            "    f((long) J < null);",  // Don't care about cast
            "",
            "    f((int) I >= i);",
            "    f((long) J < j);",
            "    f(f((java.lang.Object) (J)) > j);",  // Don't care about cast
            "",
            "    f(null instanceof Serializable);",  // L60
            "    f(i instanceof Serializable);",  // L61 primitive
            "    f(I instanceof Serializable);",
            "    f(Z instanceof Serializable);",
            "    f(I instanceof String);",  // L64 not assignable
            "  }",
            "  public C() {}",
            "}",
          },
        },
        new String[][] {
          {
            "//C",
            "import java.io.Serializable;",
            "",
            "public class C {",
            "  static void f(boolean z) { System.err.println('z'); }",
            "  static void f(Object o)  { System.err.println('o'); }",
            "",
            "  public static void main(String... argv) {",
            "    boolean z = true;",
            "    byte b = (byte) 0;",  // L10
            "    short s = (short) 0;",
            "    char c = '\0';",
            "    int i = 0;",
            "    long j = 0;",
            "    float f = 0;",
            "    double d = 0;",
            "    Long J = new Long(1);",
            "    Integer I = new Integer(0);",
            "    Boolean Z = new Boolean(true);",
            "",  // L20
            "    f(z < z);",  // L21 incomparable
            "    f(b > b);",
            "    f(s <= s);",
            "    f(c >= c);",
            "    f(i < i);",
            "    f(j > j);",
            "    f(f <= f);",
            "    f(d >= d);",
            "    f(J < J);",
            "    f(null > null);",  // L30 incomparable
            "",
            "    f(i >= b);",
            "    f(i <= s);",
            "    f(i > c);",
            "    f(i < j);",
            "    f(i >= f);",
            "    f(i <= d);",
            "    f(i > J);",
            "    f(i < null);",  // L39 incomporable
            "",  // L40
            "    f(b >= i);",
            "    f(s <= i);",
            "    f(c > i);",
            "    f(j < i);",
            "    f(f >= i);",
            "    f(d <= i);",
            "    f(J > i);",
            "    f(null < i);",  // L48 incomparable
            "",
            "    f(null <= z);",  // L50 incomparable
            "    f(z < J);",  // L51 incomparable
            "",
            "    f(null <= J);",  // L53 incomparable
            "    f(J < null);",  // L54 incomparable
            "",
            "    f(I >= i);",
            "    f(J < j);",
            "    f(f(J) > j);",  // L58 incomparable
            "",
            "    f(null instanceof Serializable);",  // L60
            "    f(i instanceof Serializable);",  // L61 primitive
            "    f(I instanceof Serializable);",
            "    f(Z instanceof Serializable);",
            "    f(I instanceof String);",  // L64 not assignable
            "  }",
            "}",
          },
        },
        null,
        null,
        null,
        "//C:21+",
        "//C:30+",
        "//C:39+",
        "//C:48+",
        "//C:50+",
        "//C:51+",
        "//C:53+",
        "//C:54+",
        "//C:58+",
        "//C:61+",
        "//C:64+"
        );
  }

  @Test
  public static void testAssignmentOperators() throws Exception {
    assertTyped(
        new String[][] {
          {
            "//C",
            "import java.io.Serializable;",
            "",
            "public class C {",
            "",
            "  public static void main(String... argv) {",
            "    boolean z = true;",
            "    byte b = (byte) 0;",
            "    short s = (short) 0;",
            "    char c = '\\0';",  // L10
            "    int i = 0;",
            "    long j = (long) (0);",
            "    float f = (float) (0);",
            "    double d = (double) (0);",
            "",
            "    Long J = new Long((long) (1));",
            "    Integer I = new Integer(0);",
            "    Boolean Z = new Boolean(true);",
            "    String T = \"T\";",
            "",  // L20
            "    i = (int) (s);",
            "    i = 1;",
            "    T = f;",  // L23 bad operand types
            "",
            "    b += (long) (J);",
            "    T += i;",
            "    T -= T;",  // L27 bad operand types
            "    T += I;",
            "",
            "    s -= i;",  // L30 RHS is promoted to int then lossily to short
            "    c *= i;",  // L31 RHS is promoted to int then lossily to char
            "    d /= (double) (J);",
            "    i %= i;",
            "    i <<= i;",
            "    i >>>= j;",
            "    d >>>= i;",  // L36 bad operand types.
            "    j >>= i;",
            "    i &= i;",
            "    z &= z;",
            "    J |= (long) (b);",  // L40 needs reboxing cast
            "    f |= c;",  // L41 bad operand types
            "    j ^= j;",
            "  }",
            "  public C() {}",
            "}",
          },
        },
        new String[][] {
          {
            "//C",
            "import java.io.Serializable;",
            "",
            "public class C {",
            "",
            "  public static void main(String... argv) {",
            "    boolean z = true;",
            "    byte b = (byte) 0;",
            "    short s = (short) 0;",
            "    char c = '\\0';",  // L10
            "    int i = 0;",
            "    long j = 0;",
            "    float f = 0;",
            "    double d = 0;",
            "",
            "    Long J = new Long(1);",
            "    Integer I = new Integer(0);",
            "    Boolean Z = new Boolean(true);",
            "    String T = \"T\";",
            "",  // L20
            "    i = s;",
            "    i = 1;",
            "    T = f;",  // L23 bad operand types
            "",
            "    b += J;",
            "    T += i;",
            "    T -= T;",  // L27 bad operand types
            "    T += I;",
            "",
            "    s -= i;",  // L30 RHS is promoted to int then lossily to short
            "    c *= i;",  // L31 RHS is promoted to int then lossily to char
            "    d /= J;",
            "    i %= i;",
            "    i <<= i;",
            "    i >>>= j;",
            "    d >>>= i;",  // L36 bad operand types
            "    j >>= i;",
            "    i &= i;",
            "    z &= z;",
            "    J |= b;",  // L40 missing cast
            "    f |= c;",  // L41 bad operand types
            "    j ^= j;",
            "  }",
            "}",
          },
        },
        null,
        null,
        null,
        "//C:23+4-9: Incompatible types for assignment: /java/lang/String * float",
        "//C:25+4-5: Cast needed before assigning byte * /java/lang/Long -> long -> byte",
        "//C:27+4-5: Cannot unbox /java/lang/String",
        "//C:30+4-5: Cast needed before assigning short * int -> int -> short",
        "//C:31+4-5: Cast needed before assigning char * int -> int -> char",
        "//C:36+4-12: Expected integral shift operands not double * int",
        "//C:40+4-5: Cast needed before assigning /java/lang/Long * byte -> long -> /java/lang/Long",
        "//C:41+");
  }

  @Test
  public static void testConditionalExpression() throws Exception {
    assertTyped(
        null,
        new String[][] {
          {
            "//C",
            "import java.util.*;",
            "",
            "public class C {",
            "  static void f(boolean b) {",
            "    System.err.println(\"boolean\");",
            "  }",
            "",
            "  static void f(int i) {",
            "    System.err.println(\"int\");",
            "  }",
            "",
            "  static void f(long j) {",
            "    System.err.println(\"long\");",
            "  }",
            "",
            "  static void f(Object o) {",
            "    System.err.println(\"Object\");",
            "  }",
            "",
            "  static void f(Collection c) {",
            "    System.err.println(\"Collection\");",
            "  }",
            "",
            "  public static void main(String[] argv) {",
            "    boolean b = false;",
            "    Boolean B = Boolean.TRUE;",
            "    int i = 0;",
            "    Integer I = Integer.valueOf(1);",
            "    long j = 0;",
            "",
            "    f(b ? b : b);",
            "    f(b ? b : B);",
            "    f(B ? B : b);",
            "    f(B ? B : B);",
            "    f(b ? b : null);",  // f(Object)
            "    f(b ? null : b);",  // f(Object)
            "    f(b ? null : null);",  // f(Collection)
            "",
            "    f(B ? i : I);",
            "    f(b ? I : i);",
            "    f(b ? j : i);",
            "    f(I ? i : i);",  // L43 - ERROR
            "",
            "    List<?> list = Collections.emptyList();",
            "    Set<?> set = Collections.emptySet();",
            "    f(b ? list : set);",
            "    f(b ? Collections.<? extends String>emptyList()",
            "        : Collections.<? extends CharSequence>emptySet());",
            "    f(b ? list : null);",
            "    f(b ? null : list);",
            "",
            "    f(b ? b : i);",  // Reference branch boxes.
            "  }",
            "}",
          },
        },
        ConditionalExpressionNode.class,
        new String[] {
            "b ? b : b : boolean",
            "b ? b : (boolean) B : boolean",
            "(boolean) B ? (boolean) (B) : b : boolean",
            "(boolean) B ? (boolean) (B) : (boolean) B : boolean",
            "b ? b : null : boolean",
            "b ? null : b : boolean",
            "b ? null : null : <null>",
            "(boolean) B ? i : (int) I : int",
            "b ? (int) (I) : i : int",
            "b ? j : (long) i : long",
            "I ? i : i : int",
            "b ? list : set"
            + " : /java/util/Collection<? extends /java/lang/Object>",
            "b ? Collections.<? extends String> emptyList()"
            + " : Collections.<?\nextends CharSequence> emptySet()"
            + " : /java/util/Collection<? extends /java/lang/CharSequence>",
            "b ? list : null : /java/util/List<? extends /java/lang/Object>",
            "b ? null : list : /java/util/List<? extends /java/lang/Object>",
            "b ? b : i : /java/io/Serializable",
        },
        DECORATE_METHOD_NAMES,
        "//C:43+");

  }

  @Test
  public static void testConditionalConstantPromotion() throws Exception {
    assertTyped(
        new String[][] {
          {
            "public class C {",
            "  static void f(Object o) {",
            "    System.err./*(Ljava/lang/String;)V*/println(\"O\");",
            "  }",
            "  static void f(byte b) {"
            +  " System.err./*(Ljava/lang/String;)V*/println(\"b\");"
            + " }",
            "  public static void main(String... argv) {",
            "    byte b = true ? (byte) (0) : (byte) 0;",
            "    byte[] bs = { true ? (byte) (1) : (byte) -1, };",
            "    byte[][] bbs = { { true ? (byte) (1) : (byte) -1, }, };",
            "    short s = (true ? (short) (-1) : (short) b);",
            // TODO: this is wrong because we're not yet doing poly expression
            // context propagation based on argument types
//          "    /*(z)V*/f(true ? (byte) (0) : b);",
            "    /*(Ljava/lang/Object;)V*/f("
            +       "(java.lang.Object) (true ? 0 : (int) b));",
            "  }",
            "  public C() {}",
            "}",
          },
        },
        new String[][] {
          {
            "//C",
            "",
            "public class C {",
            "",
            "  static void f(Object o) {",
            "    System.err.println(\"O\");",
            "  }",
            "",
            "  static void f(byte b) {",
            "    System.err.println(\"b\");",  // L10
            "  }",
            "",
            "  public static void main(String... argv) {",
            "    byte b = true ? 0 : 0;",  // L14
            "    byte[] bs = { true ? 1 : -1 };", // L15
            "    byte[][] bbs = { { true ? 1 : -1 } };", // L16
            "    short s = (true ? -1 : b);",  // L17
            "    f(true ? 0 : b);  // f(byte)",
            "  }",
            "}",
          },
        },
        CastExpressionNode.class,
        new String[] {
          "(byte) (0) : byte",
          "(byte) 0 : byte",
          "(byte) (1) : byte",
          "(byte) -1 : byte",
          "(byte) (1) : byte",
          "(byte) -1 : byte",
          "(short) (-1) : short",
          "(short) b : short",
          // TODO: byte
          "(java.lang.Object) (true ? 0 : (int) b) : /java/lang/Object",
          // TODO: (byte) b : byte
          "(int) b : int",
        },
        DECORATE_METHOD_NAMES);
  }

  @Test
  public static final void testConstructor() throws Exception {
    assertTyped(
        new String[][] {
          {
            "class C extends B {",
            "  C(CharSequence cs) {",
            "    super (String.",
            "        /* /java/lang/String(Ljava/lang/Object;)Ljava/lang/String;*/",
            "        valueOf((java.lang.Object) (cs)));",
            "  }",
            "  C(Object o) { super (\"Object \" + o); }",
            "  public static C make(String s) {",
            "    return",
            "    /* /C(Ljava/lang/CharSequence;)V*/",
            "    new C((java.lang.CharSequence) (s));",
            "  }",
            "  public static C make(Object o) "
            + "{ return/* /C(Ljava/lang/Object;)V*/new C(o); }",
            "  public static C make() "
            + "{ return/* /C(Ljava/lang/CharSequence;)V*/new C(null); }",
            "}",
          },
          {
            "class B { B(String s) { ; } }",
          },
        },
        new String[][] {
          {
            "//C",
            "class C extends B {",
            "",
            "  C(CharSequence cs) {",
            "    super(String.valueOf(cs));",
            "  }",
            "",
            "  C(Object o) {",
            "    super(\"Object \" + o);",
            "  }",
            "",
            "  public static C make(String s) {",
            "    return new C(s);",
            "  }",
            "",
            "  public static C make(Object o) {",
            "    return new C(o);",
            "  }",
            "",
            "  public static C make() {",
            "    return new C(null);",
            "  }",
            "",
            "}",
          },
          {
            "//B",
            "class B {",
            "  B(String s) {",
            "    ;",
            "  }",
            "}",
          },
        },
        null,
        null,
        DECORATE_METHOD_NAMES_INCL_CONTAINER
        );
  }

  @Test
  public static final void testStringBuilderMethodResolution()
  throws Exception {
    // Test that bridge methods work properly.

    // StringBuilder.append(char) precludes several methods
    // (1) Appendable            Appendable.append(char)
    // (2) AbstractStringBuilder AbstractStringBuilder.append(char)
    // (3) Appendable            AbstractStringBuilder.append(char)  (bridge)
    // (4) AbstractStringBuilder StringBuilder.append(char)          (bridge)
    // (5) Appendable            StringBuilder.append(char)          (bridge)
    assertTyped(
        new String[][] {
          {
            "class C {",
            "  static final String S =",
            "  /* /java/lang/StringBuilder()V*/",
            "  new StringBuilder().",
            "  /* /java/lang/StringBuilder(C)Ljava/lang/StringBuilder;*/",
            "  append('a').",
            "  /* /java/lang/StringBuilder(C)Ljava/lang/StringBuilder;*/",
            "  append('b').",
            "  /* /java/lang/StringBuilder()Ljava/lang/String;*/",
            "  toString();",
            "  public C() {}",
            "}",
          },
        },
        new String[][] {
          {
            "//C",
            "class C {",
            "  static final String S =",
            "      new StringBuilder().append('a').append('b').toString();",
            "}",
          },
        },
        PrimaryNode.class,
        new String[] {
            "new StringBuilder().append('a').append('b').toString()"
            + " : /java/lang/String",

            "new StringBuilder().append('a').append('b')"
            + " : /java/lang/StringBuilder",

            "new StringBuilder().append('a') : /java/lang/StringBuilder",
        },
        DECORATE_METHOD_NAMES_INCL_CONTAINER
        );
  }

  @Test
  public static void testEnumCaseConstants() throws Exception {
    assertTyped(
        new String[][] {
          {
            "class Foo {",
            "  enum E { A(), B(), C(), }",
            "  static final E A = E.",
            "  /* /Foo$E.B*/",
            "  B;",
            "  public static void main(String[] args) {",
            "    E C = E.",
            "    /* /Foo$E.B*/",
            "    B;",
            "    for (String arg :",
            "        /* /Foo.main(1):args*/",
            "        args) {",
            "      E e = E.",
            "      /* /Foo$E.valueOf(1)*/",
            "      valueOf(/* /Foo.main(1):arg*/arg);",
            "      switch (/* /Foo.main(1):e*/e) {",
            "        case",
            "        /* /Foo$E.A*/",  // Not /Foo.A
            "        A : System.",
            "        /* /java/lang/System.out*/",
            "        out.",
            "        /* /java/io/PrintStream.println(5)*/",
            "        println(\"A\");",
            "        break;",
            "        case",
            "        /* /Foo$E.B*/",
            "        B : System.",
            "        /* /java/lang/System.out*/",
            "        out.",
            "        /* /java/io/PrintStream.println(5)*/",
            "        println(\"B\");",
            "        break;",
            "        case",
            "        /* /Foo$E.C*/",  // Not /Foo.main(1):C
            "        C : System.",
            "        /* /java/lang/System.out*/",
            "        out.",
            "        /* /java/io/PrintStream.println(5)*/",
            "        println(\"C\");",
            "        break;",
            "        default : System.",
            "        /* /java/lang/System.out*/",
            "        out.",
            "        /* /java/io/PrintStream.println(5)*/",
            "        println(\"D\");",
            "        break;",
            "      }",
            "    }",
            "  }",
            "  public Foo() {}",
            "}",
          },
        },
        new String[][] {
          {
            "//Foo",
            "class Foo {",
            "  enum E {",
            "    A, B, C,",
            "  }",
            "",
            "  static final E A = E.B;",
            "",
            "  public static void main(String[] args) {",
            "    E C = E.B;",
            "",
            "    for (String arg : args) {",
            "      E e = E.valueOf(arg);",
            "      switch (e) {",
            "      case A: System.out.println(\"A\"); break;",
            "      case B: System.out.println(\"B\"); break;",
            "      case C: System.out.println(\"C\"); break;",
            "      default: System.out.println(\"D\"); break;",
            "      }",
            "    }",
            "  }",
            "}",
          },
        },
        null,
        null,
        DECORATE_EXPR_NAMES);
  }

  @Test
  public static void testSwitchValueUnboxing() throws Exception {
    assertTyped(
        new String[][] {
          {
            "class Foo {",
            "  {",
            "    switch ((int) (Integer.valueOf(1))) { case 1 : break; }",
            "    switch (Integer.toString(1)) { case \"1\" : break; }",
            "  }",
            "  public Foo() {}",
            "}",
          },
        },
        new String[][] {
          {
            "//Foo",
            "class Foo {",
            "  {",
            "    switch (Integer.valueOf(1)) {",
            "      case 1: break;",
            "    }",
            "    switch (Integer.toString(1)) {",
            "      case \"1\": break;",
            "    }",
            "  }",
            "}",
          },
        },
        null,
        null,
        null);
  }

  @Test
  public static final void testSuperMethodResolution() throws Exception {
    assertTyped(
        new String[][] {
          {
            "package p;",
            "enum E {",
            "  A() {",
            "    public String toString() {",
            "      return super.",
            "      /* /p/E()Ljava/lang/String;*/",
            "      toString() + ':' + super.",
            "      /* /java/lang/Enum</p/E>()I*/",
            "      hashCode();",
            "    }",
            "  }",
            "  , B() {}",
            "  ,;",

            "  public String toString() {",
            "    return\"E.\" + super.",
            "    /* /java/lang/Enum</p/E>()Ljava/lang/String;*/",
            "    toString();",
            "  }",
            "}",
          },
        },
        new String[][] {
          {
            "//E",
            "package p;",
            "enum E {",
            "  A() {",
            "    public String toString() {",
            "      return super.toString() + ':' + super.hashCode();",
            "    }",
            "  },",
            "  B() {",
            "  }",
            "  ;",
            "",
            "  public String toString() {",
            "    return \"E.\" + super.toString();",
            "  }",
            "}",
          },
        },
        null,
        new String[] {},
        DECORATE_METHOD_NAMES_INCL_CONTAINER);

    // TODO: Test Outer.super.foo();
  }

  @Test
  public static final void testSuperFieldResolution() throws Exception {
    assertTyped(
        new String[][] {
          {
            "static class Base { int x = 42; public Base() {} }",
          },
          {
            "static class Sub extends Base {",
            "  int x = 12;",
            "  @Override public String toString() {",
            "    return\"x=\" +",
            "    /* /Sub.x*/",
            "    x + \"; super.x=\" + super.",
            "    /* /Base.x*/",
            "    x;",
            "  }",
            "  public Sub() {}",
            "}",
          },
        },
        new String[][] {
          {
            "static class Base {",
            "  int x = 42;",
            "}",
          },
          {
            "static class Sub extends Base {",
            "  int x = 12;",

            "  @Override",
            "  public String toString() {",
            "    return \"x=\" + x + \"; super.x=\" + super.x;",
            "  }",
            "}",
          },
        },
        null,
        new String[] {},
        DECORATE_EXPR_NAMES);
  }


  private static final Decorator DECORATE_METHOD_NAMES = new Decorator() {

    @Override
    public String decorate(NodeI<?, ?, ?> node) {
      if (node instanceof J8MethodDescriptorReference) {
        MethodDescriptor descriptor = ((J8MethodDescriptorReference) node)
            .getMethodDescriptor();
        if (descriptor != null) {
          return Java8Comments.blockCommentMinimalSpace(descriptor.toString());
        }
      }
      return null;
    }

  };

  private static final Decorator DECORATE_METHOD_NAMES_INCL_CONTAINER =
      new Decorator() {

        @Override
        public String decorate(NodeI<?, ?, ?> node) {
          if (node instanceof J8MethodDescriptorReference) {
            J8MethodDescriptorReference desc =
                (J8MethodDescriptorReference) node;

            StringBuilder sb = new StringBuilder();

            TypeSpecification declType = desc.getMethodDeclaringType();
            if (declType != null) {
              sb.append(declType.toString());
            }

            MethodDescriptor descriptor = desc.getMethodDescriptor();
            if (descriptor != null) {
              sb.append(descriptor);
            }
            if (sb.length() != 0) {
              return Java8Comments.blockCommentMinimalSpace(sb.toString());
            }
          }
          return null;
        }

      };

  private static final Decorator DECORATE_EXPR_NAMES = new Decorator() {

    @Override
    public String decorate(NodeI<?, ?, ?> node) {
      if (node instanceof J8ExpressionNameReference) {
        Name referent = ((J8ExpressionNameReference) node)
            .getReferencedExpressionName();
        if (referent != null) {
          return Java8Comments.blockCommentMinimalSpace(referent.toString());
        }
      }
      return null;
    }

  };
}
