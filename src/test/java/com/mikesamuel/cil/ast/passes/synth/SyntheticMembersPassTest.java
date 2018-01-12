package com.mikesamuel.cil.ast.passes.synth;

import java.util.logging.Logger;

import org.junit.Test;

import com.google.common.collect.ImmutableList;
import com.mikesamuel.cil.ast.j8.J8FileNode;
import com.mikesamuel.cil.ast.passes.CommonPassRunner;
import com.mikesamuel.cil.ast.passes.PassTestHelpers;
import com.mikesamuel.cil.ast.passes.PassTestHelpers.PassRunner;
import com.mikesamuel.cil.parser.Unparse.UnparseVerificationException;

import junit.framework.TestCase;

@SuppressWarnings("javadoc")
public final class SyntheticMembersPassTest extends TestCase {

  private static void runPass(
      String[][] expected,
      String[][] inputs,
      String... expectedErrors)
  throws UnparseVerificationException {
    PassTestHelpers.assertAnnotatedOutput(
        new PassRunner() {

          @Override
          public ImmutableList<J8FileNode> runPasses(
              Logger logger, ImmutableList<J8FileNode> files) {
            CommonPassRunner cpr = new CommonPassRunner(logger);
            ImmutableList<J8FileNode> processed = cpr.run(files);
            SyntheticMemberPass smp = new SyntheticMemberPass(
                logger, cpr.getMethodVariantPool(), cpr.getMemberInfoPool());
            return smp.run(processed);
          }

        },
        expected,
        inputs,
        null,
        expectedErrors);
  }

  @Test
  public static void testSimpleBridgeNeeded() throws Exception {
    runPass(
        new String[][] {
          {
            "interface I<T> { T f(T x); }",
          },
          {
            "class C implements I<String> {",
            "  public String f(String x) { return x; }",
            "  @com.mikesamuel.cil.ast.meta.Synthetic",
            "  public String f(java.lang.Object x) {",
            "    return this.f((java.lang.String) x);",
            "  }",
            "  public C() {}",
            "}",
          },
        },
        new String[][] {
          {
            "//I",
            "interface I<T> {",
            "  T f(T x);",
            "}",
          },
          {
            "//C",
            "class C implements I<String> {",
            "  // Since <T> binds to String above f(String) implements f(T)",
            "  public String f(String x) { return x; }",
            "}",
          },
        });
  }

  @Test
  public static void testPrivateAccessors() throws Exception {
    runPass(
        new String[][] {
          {
            "public class Outer {",
            "  private int x;",
            "  @com.mikesamuel.cil.ast.meta.Synthetic",
            "  int access$get$x() {",
            "    return this.x;",
            "  }",
            "  @com.mikesamuel.cil.ast.meta.Synthetic",
            "  int access$set$x(int new$x) {",
            "    return this.x = new$x;",
            "  }",
            "  private int y, z;",
            "  @com.mikesamuel.cil.ast.meta.Synthetic",
            "  int access$get$y() {",
            "    return this.y;",
            "  }",
            "  private void clear() { x = 0; }",
            "  @com.mikesamuel.cil.ast.meta.Synthetic",
            "  void access$call$clear()",
            "  { this.clear(); }",
            "  public Inner inner() { return new Inner(false); }",
            "  public class Inner {",
            "    private Inner() {}",
            "    @com.mikesamuel.cil.ast.meta.Synthetic",
            "    Inner(boolean a0) { this(); }",
            "    public void incr() {",
            "      Outer.this.access$set$x((Outer.this.access$get$x()+1));",
            "    }",
            "    public int x() {",
            "      return Outer.this.access$get$x()",
            "           * access$get$y();",
            "    }",
            "    public void clear() { Outer.this.access$call$clear(); }",
            "  }",
            "  public Outer() {}",
            "}",
          },
        },
        new String[][] {
          {
            "//Outer",
            "public class Outer {",
            "  private int x;",
            "  private int y, z;",
            "",
            "  private void clear() { x = 0; }",
            "",
            "  public Inner inner() {",
            "    return new Inner();  // Access to private constructor",
            "  }",
            "",
            "  public class Inner {",
            "    private Inner() {}",
            "",
            "    public void incr() {",
            "      ++x;  // Private field set",
            "    }",
            "    public int x() {",
            "      return Outer.this.x * y;",
            "    }  // Private field read",
            "    public void clear() {",
            "      Outer.this.clear();  // Method call",
            "    }",
            "  }",
            "}",
          }
        });
  }

  @Test
  public static void testSuperCalls() throws Exception {
    runPass(
        new String[][] {
          {
            "class Super {",
            "  int f() { return this.access$sup$f(); }",
            "  @com.mikesamuel.cil.ast.meta.Synthetic",
            "  protected final int access$sup$f() { return 1; }",
            "  public Super() {}",
            "}",
          },
          {
            "class Sub extends Super {",
            "  int f() { return 2; }",
            "  int g() { return this.access$sup$f(); }",
            "  public Sub() {}",
            "}",
          },
        },
        new String[][] {
          {
            "//Super",
            "class Super {",
            "  int f() { return 1; }",
            "}",
          },
          {
            "//Sub",
            "class Sub extends Super {",
            "  int f() { return 2; }",
            "  int g() { return super.f(); }",
            "}",
          }
        });
  }


  @Test
  public static void testMultiAssignments() throws Exception {
    runPass(
        new String[][] {
          {
            "public class Outer {",
            "  static final Inner[] INNERS = {",
            "    new Inner(), new Inner(), new Inner(),",
            "    new Inner(), new Inner(), new Inner(),",
            "    new Inner(), new Inner(), new Inner(),",
            "  };",
            "  static{",
            "    @com.mikesamuel.cil.ast.meta.Synthetic",
            "    Outer.Inner tmp__6;",
            "    @com.mikesamuel.cil.ast.meta.Synthetic",
            "    Outer.Inner tmp__7;",
            "    @com.mikesamuel.cil.ast.meta.Synthetic",
            "    Outer.Inner tmp__8;",
            "    int i = 0;",
            "    INNERS[i++].access$set$s((short) 1234);",
            "    (tmp__6 = INNERS[i++]).access$set$s(",
            "        (short) (tmp__6.access$get$s() + 1));",
            "    (tmp__7 = INNERS[i++]).access$set$s(",
            "        (short) (tmp__7.access$get$s() - 1));",
            "    (tmp__8 = INNERS[i++]).access$set$s(",
            "        (short) (tmp__8.access$get$s() - (7)));",
            "  }",
            "  static int j = 0;",
            "  static short k0 = (INNERS[j++].access$set$s((short) 1234));",
            "  static short k1;",
            "  static{",
            "    @com.mikesamuel.cil.ast.meta.Synthetic",
            "    Outer.Inner tmp__4;",
            "    k1 = ((short) ((tmp__4 = INNERS[j++]).access$set$s(",
            "        (short) (tmp__4.access$get$s() + 1)) - 1));",
            "  }",
            "  static short k2;",
            "  static{",
            "    @com.mikesamuel.cil.ast.meta.Synthetic",
            "    Outer.Inner tmp__5;",
            "    k2 = ((tmp__5 = INNERS[j++]).access$set$s(",
            "        (short) (tmp__5.access$get$s() - 1)));",
            "  }",
            "  static short k3;",
            "  static{",
            "    @com.mikesamuel.cil.ast.meta.Synthetic",
            "    Outer.Inner tmp__9;",
            "    k3 = ((tmp__9 = INNERS[j++]).access$set$s(",
            "        (short) (tmp__9.access$get$s() - (7))));",
            "  }",
            "  static short f0(int x) {",
            "      return (INNERS[x++].access$set$s((short) 1234));",
            "  }",
            "  static short f1(int x) {",
            "    @com.mikesamuel.cil.ast.meta.Synthetic",
            "    Outer.Inner tmp__1;",
            "    return ((short) ((tmp__1 = INNERS[x++]).access$set$s(",
            "        (short) (tmp__1.access$get$s() + 1)) - 1));",
            "  }",
            "  static short f2(int x) {",
            "    @com.mikesamuel.cil.ast.meta.Synthetic",
            "    Outer.Inner tmp__2;",
            "    return ((tmp__2 = INNERS[x++]).access$set$s(",
            "        (short) (tmp__2.access$get$s() - 1)));",
            "  }",
            "  static short f3(int x) {",
            "    @com.mikesamuel.cil.ast.meta.Synthetic",
            "    Outer.Inner tmp__3;",
            "    return ((tmp__3 = INNERS[x++]).access$set$s(",
            "        (short) (tmp__3.access$get$s() - (7))));",
            "  }",
            "  static class Inner {",
            "    private short s;",
            "    @com.mikesamuel.cil.ast.meta.Synthetic",
            "    short access$get$s() {",
            "      return this.s;",
            "    }",
            "    @com.mikesamuel.cil.ast.meta.Synthetic",
            "    short access$set$s(short new$s) {",
            "      return this.s = new$s;",
            "    }",
            "    @Override public String toString() { return \"\" + s; }",
            "    public Inner() {}",
            "  }",
            "  public static void main(String... argv) {",
            "    for (Inner inner : INNERS) {",
            "      System.out.println(inner);",
            "    }",
            "  }",
            "  public Outer() {}",
            "}",
          },
        },
        new String[][] {
          {
            "//Outer",
            "public class Outer {",
            "  static final Inner[] INNERS = {",
            "    new Inner(), new Inner(), new Inner(),",
            "    new Inner(), new Inner(), new Inner(),",
            "    new Inner(), new Inner(), new Inner(),",
            "  };",
            // Complex left hand sides in a block statement
            "  static {",
            "    int i = 0;",
            "    INNERS[i++].s = (short) 1234;",
            "    INNERS[i++].s++;",
            "    --INNERS[i++].s;",
            "    INNERS[i++].s -= 7;",
            "  }",
            // Complex left hand sides in initializers
            "  static int j = 0;",
            "  static short k0 = (INNERS[j++].s = (short) 1234);",
            "  static short k1 = (INNERS[j++].s++);",
            "  static short k2 = (--INNERS[j++].s);",
            "  static short k3 = (INNERS[j++].s -= 7);",
            // Complex left hand sides in expression contexts
            "  static short f0(int x) {",
            "    return (INNERS[x++].s = (short) 1234);",
            "  }",
            "  static short f1(int x) {",
            "    return (INNERS[x++].s++);",
            "  }",
            "  static short f2(int x) {",
            "    return (--INNERS[x++].s);",
            "  }",
            "  static short f3(int x) {",
            "    return (INNERS[x++].s -= 7);",
            "  }",
            "  static class Inner {",
            "    private short s;",
            "    @Override public String toString() {",
            "      return \"\" + s;",
            "    }",
            "  }",
            "  public static void main(String... argv) {",
            "    for (Inner inner : INNERS) {",
            "      System.out.println(inner);",
            "    }",
            "  }",
            "}",
          }
        },
        // -= 7 misidentified as lossy conversion by TypingPass.
        "Cast needed before assigning short",
        "Cast needed before assigning short",
        "Cast needed before assigning short");
  }

  // TODO:
  // Variadic bridge methods
  // Void bridge methods
  // Bridges that have multiple overrides.
}
