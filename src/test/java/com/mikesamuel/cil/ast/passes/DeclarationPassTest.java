package com.mikesamuel.cil.ast.passes;

import java.lang.reflect.Modifier;
import java.util.logging.Logger;

import org.junit.Test;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.mikesamuel.cil.ast.BaseNode;
import com.mikesamuel.cil.ast.CompilationUnitNode;
import com.mikesamuel.cil.ast.Java8Comments;
import com.mikesamuel.cil.ast.Trees.Decorator;
import com.mikesamuel.cil.ast.meta.TypeInfo;
import com.mikesamuel.cil.ast.traits.TypeDeclaration;
import com.mikesamuel.cil.parser.Unparse.UnparseVerificationException;

import junit.framework.TestCase;

@SuppressWarnings("javadoc")
public class DeclarationPassTest extends TestCase {

  private static final Decorator DECORATOR = new Decorator() {

    @Override
    public String decorate(BaseNode node) {
      if (node instanceof TypeDeclaration) {
        TypeDeclaration td = (TypeDeclaration) node;
        TypeInfo ti = td.getDeclaredTypeInfo();
        if (ti != null) {
          return Java8Comments.blockComment(
              (
                  Modifier.toString(ti.modifiers)
                  + (ti.isAnonymous ? " anonymous" : "")
                  + " " + ti.canonName
                  + (ti.superType.isPresent()
                      ? " extends " + ti.superType.get() : "")
                  + (ti.interfaces.isEmpty()
                      ? "" : " implements "
                          + Joiner.on(", ").join(ti.interfaces))
                  + (ti.outerClass.isPresent()
                      ? " in " + ti.outerClass.get() : "")
                  + (ti.innerClasses.isEmpty()
                      ? "" : " contains "
                          + Joiner.on(", ").join(ti.innerClasses))
              )
              .trim(),
              false);
        }
      }
      return null;
    }

  };

  private static void assertDeclarations(
      String[][] expectedLines,
      String[][] inputLines,
      String... expectedErrors)
  throws UnparseVerificationException {
    PassTestHelpers.assertAnnotatedOutput(
        new PassTestHelpers.PassRunner() {

          @Override
          public ImmutableList<CompilationUnitNode> runPasses(
              Logger logger, ImmutableList<CompilationUnitNode> cus) {
            DeclarationPass dp = new DeclarationPass(logger);
            dp.run(cus);
            return cus;
          }
        },
        expectedLines,
        inputLines,
        DECORATOR,
        expectedErrors);
  }

  @Test
  public static void testEmptyCompilationUnit() throws Exception {
    assertDeclarations(
        new String[][] {
          {
          }
        },
        new String[][] {
          {
          },
        });

    assertDeclarations(
        new String[][] {
          {
            "package foo;",
          }
        },
        new String[][] {
          {
            "package foo;",
          },
        });


    assertDeclarations(
        new String[][] {
          {
            "@java.lang.annotations.ParametersAreNonnullByDefault"
            + " package foo;",
          }
        },
        new String[][] {
          {
            "@java.lang.annotations.ParametersAreNonnullByDefault",
            "package foo;",
          },
        });
  }

  @Test
  public static void testOneClass() throws Exception {
    assertDeclarations(
        new String[][] {
          {
            "package foo;"
            + " /* /foo/C extends /java/lang/Object */"
            + "class C {}",
          }
        },
        new String[][] {
          {
            "package foo;",
            "class C {}"
          },
        });
  }

  @Test
  public static void testOneTypeInDefaultPackage() throws Exception {
    assertDeclarations(
        new String[][] {
          {
            "/* /C extends /java/lang/Object */"
            + "class C {}",
          }
        },
        new String[][] {
          {
            "class C {}"
          },
        });
  }

  @Test
  public static void testTypeInDefaultPackageResolvedWhenMasksJavaLang()
  throws Exception {
    assertDeclarations(
        new String[][] {
          {
            "/* /C extends /Object */",
            "class C extends Object {}",
            "/* /Object extends /java/lang/Object */",
            "class Object {}",
          }
        },
        new String[][] {
          {
            "class C extends Object {}",
            "class Object {}",  // Masks java.lang.Object
          },
        });
  }

  @Test
  public static void testOneInterface() throws Exception {
    assertDeclarations(
        new String[][] {
          {
            "package foo.bar;",
            "/* /foo/bar/I extends /java/lang/Object"
            + " implements /java/lang/Runnable */",
            "interface I extends Runnable {}",
          }
        },
        new String[][] {
          {
            "package foo.bar;",
            "interface I extends Runnable {}"
          },
        });
  }

  @Test
  public static void testInternalSubtypeRelationshipForward() throws Exception {
    assertDeclarations(
        new String[][] {
          {
            "package com.example;",
            "/* public /com/example/C extends /java/lang/Object */",
            "public class C {}",
          },
          {
            "package com.example;",
            "/* public final /com/example/D extends /com/example/C */",
            "public final class D extends C {}",
          }
        },
        new String[][] {
          {
            "package com.example;",
            "public class C {}"
          },
          {
            "package com.example;",
            "public final class D extends C {}"
          },
        });
  }


  @Test
  public static void testInternalSubtypeRelationshipBackward()
  throws Exception {
    assertDeclarations(
        new String[][] {
          {
            "package com.example;",
            "/* public final /com/example/D extends /com/example/C */",
            "public final class D extends C {}",
          },
          {
            "package com.example;",
            "/* public /com/example/C extends /java/lang/Object */",
            "public class C {}",
          }
        },
        new String[][] {
          {
            "package com.example;",
            "public final class D extends C {}"
          },
          {
            "package com.example;",
            "public class C {}"
          },
        });
  }

  @Test
  public static void testInnerTypes() throws Exception {
    assertDeclarations(
        new String[][] {
          {
            "package foo;",
            "/* /foo/C extends /java/lang/Object"
            + " contains /foo/C$I, /foo/C$J */",
            "class C {",
            "  /* /foo/C$I extends /java/lang/Object in /foo/C */",
            "  interface I {}",
            "  /* /foo/C$J extends /java/lang/Object in /foo/C */",
            "  interface J {}",
            "}",
          }
        },
        new String[][] {
          {
            "package foo;",
            "class C {",
            "  interface I {}",
            "  interface J {}",
            "}",
          },
        });
  }

  @Test
  public static void testDuplicateTopLevelType() throws Exception {
    assertDeclarations(
        new String[][] {
          {
            "package foo;"
            + " /* /foo/C extends /java/lang/Object */"
            + "class C {}",
          },
          {
            "package foo;"
            + " /* /foo/C */"
            + "class C {}",
          }
        },
        new String[][] {
          {
            "//a.java",
            "package foo;",
            "class C {",
            "}",
          },
          {
            "//b.java",
            "package foo;",
            "class C {",
            "}",
          },
        },
        "Duplicate definition for /foo/C "
        );
  }

  @Test
  public static void testDuplicateInnerType() throws Exception {
    assertDeclarations(
        new String[][] {
          {
            "package foo;",
            "/* /foo/C extends /java/lang/Object"
            + " contains /foo/C$I */",  // Only one mentioned as inner
            "class C {",
            "  /* /foo/C$I extends /java/lang/Object in /foo/C */",  // Resolved
            "  interface I {}",
            "  /* /foo/C$I in /foo/C */",  // Partially resolved
            "  interface I {}",
            "}",
          }
        },
        new String[][] {
          {
            "//Foo.java",
            "package foo;",
            "class C {",
            "  interface I {}",
            "  interface I {}",
            "}",
          },
        },
        "//Foo.java:5+3-17: Duplicate definition for /foo/C$I"
        + " originally defined at //Foo.java:4+3-17");
  }

  @Test
  public static void testAnonymousTypeInMethod() throws Exception {
    assertDeclarations(
        new String[][] {
          {
            "package foo;",
            "import com.google.common.base.Supplier;",
            "/* /foo/C extends /java/lang/Object contains /foo/C.foo(1)$1 */",
            "class C {",
            "  public <",
            "  /* /foo/C.foo(1).<T> extends /java/lang/Object */",
            "  T> Supplier<T> foo(T x) {",
            "    return",
            "    /* anonymous /foo/C.foo(1)$1"
            +     " extends /com/google/common/base/Supplier in /foo/C */",
            "    new Supplier<T> () { @Override public T get() { return x; } };",
            "  }",
            "}"
          }
        },
        new String[][] {
          {
            "package foo;",
            "import com.google.common.base.Supplier;",
            "class C {",
            "  public <T> Supplier<T> foo(T x) {",
            "    return new Supplier<T>() {",
            "      @Override public T get() { return x; }",
            "    };",
            "  }",
            "}"
          },
        });

  }

  @Test
  public static void testAnonymousTypeInConstructor() throws Exception {
    assertDeclarations(
        new String[][] {
          {
            "package foo;",
            "import java.io.Serializable;",
            "import java.util.*;",
            "/* /foo/C extends /java/lang/Object contains /foo/C.<init>(1)$1 */",
            "class C {",
            "  public <",
            "  /* /foo/C.<init>(1).<T> extends /java/lang/Comparable"
            +   " implements /java/io/Serializable */",
            "  T extends Comparable<T> & Serializable> C(T x) {",
            "    super (",
            "        /* anonymous /foo/C.<init>(1)$1"
            +         " extends /java/util/Map$Entry in /foo/C */",
            "        new Map.Entry<T, T> () {",
            "          @Override public T getKey() { return x; }",
            "          @Override public T getValue() { return x; }",
            "        }",
            "    );",
            "  }",
            "}",
          }
        },
        new String[][] {
          {
            "package foo;",
            "import java.io.Serializable;",
            "import java.util.*;",
            "class C {",
            "  public <T extends Comparable<T> & Serializable> C(T x) {",
            "    super(new Map.Entry<T, T>() {",
            "      @Override public T getKey() { return x; }",
            "      @Override public T getValue() { return x; }",
            "    });",
            "  }",
            "}",
          },
        });
  }

  @Test
  public static void testNamedTypeInMethod() throws Exception {
    assertDeclarations(
        new String[][] {
          {
            "/* public /C extends /java/lang/Object contains /C.f(1)$R */",
            "public interface C {",
            "  default void f() {",
            "    /* /C.f(1)$R extends /java/lang/Object"
            +     " implements /java/lang/Runnable in /C */",
            "    class R implements Runnable { public void run() {} }",
            "    (new R() ) .run();",
            "  }",
            "}",
          }
        },
        new String[][] {
          {
            "public interface C {",
            "  default void f() {",
            "    class R implements Runnable {",
            "      public void run() {}",
            "    }",
            "    (new R()).run();",
            "  }",
            "}",
          },
        });

  }


  @Test
  public static void testForwardDeclarationsInBlocks() throws Exception {
    assertDeclarations(
        new String[][] {
          {
            "/* public /C extends /java/lang/Object"
            + " contains /C.f(1)$E, /C.f(1)$D, /C.f(1)$F, /C$D */",
            "public class C {",
            "  static Class[] f() {",
            "    /* /C.f(1)$E extends /C$D in /C */",
            "    class E extends D {}",
            "    /* /C.f(1)$D extends /java/lang/Object in /C */",
            "    class D {}",
            "    /* /C.f(1)$F extends /C.f(1)$D in /C */",
            "    class F extends D {}",
            "    return new Class[] { E.class, F.class, };",
            "  }",
            "  /* static /C$D extends /java/lang/Object in /C */",
            "  static class D {}",
            "}",
          }
        },
        new String[][] {
          {
            "public class C {",
            "",
            "  static Class[] f() {",
            "    class E extends D {}",
            "    class D {}",
            "    class F extends D {}",
            "    return new Class[] { E.class, F.class };",
            "  }",
            "  static class D {}",
            "}",
          },
        });
  }

  @Test
  public static void testCyclicDeclarationsInBlocks() throws Exception {
    assertDeclarations(
        new String[][] {
          {
            // TODO pull this example into the type reference resolution tests
            // when they're written.
            "/* /C extends /java/lang/Object contains /C.f(1)$C */",
            "class C {",
            "  public static C f(boolean sub) {",
            "    if (!sub) { return new C(); }",  // This should resolve to /C
            // Forward declaration rules do not prevent a class from referring
            // to itself so this is the correct resolution even though this
            // input is invalid due to class cycles.
            "    /* /C.f(1)$C extends /C.f(1)$C in /C */",
            "    class C extends C {}",
            "    return new C();",  // This resolves to f(1)$C
            "  }",
            "}",
          }
        },
        new String[][] {
          {
            "class C {",
            "  public static C f(boolean sub) {",
            "    if (!sub) { return new C(); }",
            "    class C extends C {}",
            "    return new C();",
            "  }",
            "}",
          },
        });
  }

  @Test
  public static void testNamedTypeInConstructor() throws Exception {
    assertDeclarations(
        new String[][] {
          {
            "/* public /C extends /java/lang/Object contains /C.<init>(1)$R */",
            "public class C {",
            "  <",
            "  /* /C.<init>(1).<T> extends /java/lang/Object */",
            "  T> C() {",
            "    /* /C.<init>(1)$R extends /java/lang/Object"
            +     " implements /java/lang/Runnable in /C */",
            "    class R implements Runnable { public void run() {} }",
            "    (new R() ) .run();",
            "  }",
            "}",
          }
        },
        new String[][] {
          {
            "public class C {",
            "  <T> C() {",
            "    class R implements Runnable {",
            "      public void run() {}",
            "    }",
            "    (new R()).run();",
            "  }",
            "}",
          },
        });
  }

  @Test
  public static void testAnonymousTypesInOverloadedMethods() throws Exception {
    assertDeclarations(
        new String[][] {
          {
            "import com.google.common.base.Supplier;",
            "/* /C extends /java/lang/Object contains /C.f(1)$1, /C.f(2)$1 */",
            "class C {",
            "  Supplier f(int i) {",
            "    return",
            "    /* anonymous /C.f(1)$1 extends"
            +     " /com/google/common/base/Supplier in /C */",
            "    new Supplier() { @Override public Object get() { return i; } };",
            "  }",
            "  Supplier f(float f) {",
            "    return",
            "    /* anonymous /C.f(2)$1 extends"
            +     " /com/google/common/base/Supplier in /C */",
            "    new Supplier() { @Override public Object get() { return f; } };",
            "  }",
            "}",
          }
        },
        new String[][] {
          {
            "import com.google.common.base.Supplier;",
            "class C {",
            "  Supplier f(int i) {",
            "    return new Supplier() {",
            "      @Override public Object get() { return i; }",
            "    };",
            "  }",
            "  Supplier f(float f) {",
            "    return new Supplier() {",
            "      @Override public Object get() { return f; }",
            "    };",
            "  }",
            "}",
          },
        });
  }

  @Test
  public static void testForwardReferenceToTypeParameter() throws Exception {
    assertDeclarations(
        new String[][] {
          {
            "/* /Bar extends /java/lang/Object contains /Bar$T */",
            "class Bar {",
            "  /* static /Bar$T extends /java/lang/Object in /Bar */",
            "  static class T {}",
            "  static <",
            "  /* /Bar.f(1).<P> extends /Bar.f(1).<T> */",
            "  P extends T,",
            "  /* /Bar.f(1).<T> extends /java/lang/CharSequence */",
            "  T extends CharSequence> void f() {}",
            "}",
          }
        },
        new String[][] {
          {
            "class Bar {",
            "  static class T {}",
            "  static <P extends T, T extends CharSequence> void f() {}",
            "}",
          },
        });
  }


  @Test
  public static void testSelfReferenceToTypeParameter() throws Exception {
    assertDeclarations(
        new String[][] {
          {
            "/* public /Foo extends /java/lang/Object */",
            "public class Foo<",
            "/* /Foo.<T> extends /java/lang/Object */",
            "T> {"
            + " <"
            // Illegal cyclic reference but correct from a scoping point of
            // view.
            // This scoping is necessary so that declarations like
            //   <T extends Comparable<T>>
            //   <E extends Enum<E>>
            // work properly.
            + "/* /Foo.f(1).<T> extends /Foo.f(1).<T> */"
            + "T extends T> void f(T x) {} "
            + "}",
          }
        },
        new String[][] {
          {
            "public class Foo<T> {",
            "  <T extends T> void f(T x) {}",
            "}",
          },
        });
  }

}
