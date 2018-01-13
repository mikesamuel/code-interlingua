package com.mikesamuel.cil.ast.passes;

import java.util.logging.Logger;

import org.junit.Test;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.mikesamuel.cil.ast.NodeI;
import com.mikesamuel.cil.ast.Trees.Decorator;
import com.mikesamuel.cil.ast.j8.J8FileNode;
import com.mikesamuel.cil.ast.j8.J8MemberDeclaration;
import com.mikesamuel.cil.ast.j8.Java8Comments;
import com.mikesamuel.cil.ast.meta.MemberInfo;
import com.mikesamuel.cil.ast.meta.StaticType.TypePool;
import com.mikesamuel.cil.ast.meta.TypeInfoResolver;
import com.mikesamuel.cil.ast.passes.PassTestHelpers.PassRunner;
import com.mikesamuel.cil.parser.Unparse.UnparseVerificationException;

import junit.framework.TestCase;

@SuppressWarnings("javadoc")
public final class ClassMemberPassTest extends TestCase {

  private static void assertMembers(
      String[][] expected,
      String[][] inputs,
      String... expectedErrors)
  throws UnparseVerificationException {
    PassTestHelpers.assertAnnotatedOutput(
        new PassRunner() {

          @Override
          public ImmutableList<J8FileNode> runPasses(
              Logger logger, ImmutableList<J8FileNode> files) {
            DeclarationPass declPass = new DeclarationPass(logger);
            TypeInfoResolver typeInfoResolver =
                declPass.run(files).typeInfoResolver;
            ExpressionScopePass esp = new ExpressionScopePass(
                typeInfoResolver, logger);
            esp.run(files);
            DisambiguationPass disambigPass = new DisambiguationPass(
                typeInfoResolver, logger, false);
            ImmutableList<J8FileNode> rewritten = disambigPass.run(files);
            TypePool typePool = new TypePool(typeInfoResolver);
            ClassMemberPass classMemberPass = new ClassMemberPass(
                logger, typePool);
            classMemberPass.run(rewritten);
            return rewritten;
          }

        },
        expected,
        inputs,
        DECORATOR,
        expectedErrors);
  }

  @Test
  public static void testMembers() throws Exception {
    assertMembers(
        new String[][] {
          {
            "class C {",
            "  /* (FieldInfo static final /C.X : /java/lang/Integer.TYPE) */",
            "  static final int X;",
            "  /* (FieldInfo private /C.x : /java/lang/Integer.TYPE) */",
            "  private int x;",
            "  /* (CallableInfo private static /C.<clinit>(1) @ ()V : /java/lang/Void.TYPE) */",
            "  static{ X = 42; }",
            "  /* (CallableInfo /C.<init>(1) @ ()V : /java/lang/Void.TYPE) */",
            "  C() { x = X; }",
            "  /* (CallableInfo /C.<init>(2) @ (Ljava/lang/String;)V : /java/lang/Void.TYPE) */",
            "  C(String s) { x = X; }",
            "  /* (CallableInfo /C.<init>(3) @ ([Ljava/lang/Integer;)V : /java/lang/Void.TYPE) */",
            "  C(Integer... is) { x = X; }",
            "  /* (CallableInfo /C.f(1) @ (Ljava/lang/Integer;)I : /java/lang/Integer.TYPE) */",
            "  int f(Integer i) { return i; }",
            "  /* (CallableInfo /C.f(2)<T> @ (Ljava/lang/String;)Ljava/lang/String; : /C.f(2).<T>) */",
            "  <T extends String> T f(T s) { return s; }",
            "  /* (CallableInfo /C.f(3)<T> @ (Ljava/lang/Object;)Ljava/lang/Object; : /C.f(3).<T>) */",
            "  <T> T f(T o) { return o; }",
            "}",
          },
        },
        new String[][] {
          {
            "//C",
            "class C {",
            "  static final int X;",
            "  private int x;",
            "  static { X = 42; }",
            "  C() { x = X; }",
            "  C(String s) { x = X; }",
            "  C(Integer... is) { x = X; }",
            "  int f(Integer i) { return i; }",
            "  <T extends String> T f(T s) { return s; }",
            "  <T> T f(T o) { return o; }",
            "}",
          },
        });
  }

  @Test
  public static void testMultideclaration() throws Exception {
    assertMembers(
        new String[][] {
          {
            "class C {",
            "  /* (FieldInfo static final /C.PI : /java/lang/Integer.TYPE),"
            +   " (FieldInfo static final /C.SQRT2 : /java/lang/Integer.TYPE)"
            + " */",
            "  /** roughly */",
            "  static final int PI = 3, SQRT2 = 1;",
            "  /* (CallableInfo public /C.<init>(1) @ ()V"
            +   " : /java/lang/Void.TYPE) */",
            "  public C() {}",
            "}",
          },
        },
        new String[][] {
          {
            "class C {",
            "  /** roughly */",
            "  static final int PI = 3, SQRT2 = 1;",
            "}",
          },
        });
  }

  private static final Decorator DECORATOR = new Decorator() {

    @Override
    public String decorate(NodeI<?, ?, ?> node) {
      if (node instanceof J8MemberDeclaration) {
        J8MemberDeclaration md = (J8MemberDeclaration) node;
        ImmutableList<MemberInfo> mi = md.getMemberInfo();
        return Java8Comments.blockComment(
            mi != null ? Joiner.on(", ").join(mi) : "?",
            false);
      }
      return null;
    }

  };
}
