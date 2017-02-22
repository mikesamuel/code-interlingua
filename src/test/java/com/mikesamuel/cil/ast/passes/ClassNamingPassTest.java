package com.mikesamuel.cil.ast.passes;

import static com.mikesamuel.cil.ast.meta.Name.Type.CLASS;
import static com.mikesamuel.cil.ast.meta.Name.Type.PACKAGE;
import static com.mikesamuel.cil.ast.meta.Name.Type.TYPE_PARAMETER;

import java.lang.reflect.Modifier;
import java.util.List;
import java.util.logging.Logger;

import org.junit.Test;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.mikesamuel.cil.ast.meta.MemberInfo;
import com.mikesamuel.cil.ast.meta.Name;
import com.mikesamuel.cil.ast.traits.FileNode;

import junit.framework.TestCase;

@SuppressWarnings("javadoc")
public final class ClassNamingPassTest extends TestCase {

  private static final Logger LOGGER = Logger.getLogger(
      ClassNamingPassTest.class.getName());

  @Test
  public static void testClassFinder() {
    List<FileNode> compilationUnits =
        PassTestHelpers.parseCompilationUnits(
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
            "  static { }",
            // declared valueOf should not collide with implied valueOf(String)
            "  public static final A valueOf(int i) { return null; }",
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
    ClassNamingPass.DeclarationsAndScopes declsAndScopes =
        p.run(compilationUnits);

    {
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
          .method("bar", 1)
          .child("T", TYPE_PARAMETER)
          );

      ImmutableList<Name> got = ImmutableList.copyOf(
          declsAndScopes.declarations.keySet());

      if (!want.equals(got)) {
        assertEquals(  // Nice eclipse diff.
            Joiner.on('\n').join(want),
            Joiner.on('\n').join(got));
        assertEquals(want, got);
      }
    }

    {
      ImmutableList<String> want = ImmutableList.of(
          "private static final /foo/bar/C.x",
          "public /foo/bar/C.<init>(1)",
          "/foo/bar/C$1.y",
          "public /foo/bar/D.<init>(1)",
          "public static final /Foo$A.B",
          "public static final /Foo$A.C",
          "public static /Foo$A.values(1)",
          "public static /Foo$A.valueOf(1)",
          "public static final /com/example/X.A",
          "public static final /com/example/X.B",
          "public static final /com/example/X.C",
          "private /com/example/X.<init>(1)",
          "private static /com/example/X.<clinit>(1)",
          "public static final /com/example/X.valueOf(1)",  // Declared
          "public static /com/example/X.values(1)",
          "public static /com/example/X.valueOf(2)",  // Implicit
          "/com/example/parameterized/P.foo(1)",
          "/com/example/parameterized/P.bar(1)",
          "public /com/example/parameterized/P.<init>(1)"
          );

      ImmutableList.Builder<String> b = ImmutableList.builder();

      for (UnresolvedTypeDeclaration d : declsAndScopes.declarations.values()) {
        for (MemberInfo mi : d.decl.getDeclaredTypeInfo().declaredMembers) {
          String modString = Modifier.toString(mi.modifiers);
          b.add(
              modString + (modString.isEmpty() ? "" : " ")
              + mi.canonName.toString());
        }
      }

      ImmutableList<String> got = b.build();

      if (!want.equals(got)) {
        assertEquals(  // Nice eclipse diff.
            Joiner.on('\n').join(want),
            Joiner.on('\n').join(got));
        assertEquals(want, got);
      }
    }
  }
}
