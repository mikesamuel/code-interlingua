package com.mikesamuel.cil.ast.passes;

import java.util.logging.Logger;

import org.junit.Test;

import com.google.common.collect.ImmutableList;
import com.mikesamuel.cil.ast.BaseNode;
import com.mikesamuel.cil.ast.CompilationUnitNode;
import com.mikesamuel.cil.ast.Java8Comments;
import com.mikesamuel.cil.ast.Trees.Decorator;
import com.mikesamuel.cil.ast.meta.MemberInfo;
import com.mikesamuel.cil.ast.meta.StaticType.TypePool;
import com.mikesamuel.cil.ast.meta.TypeInfoResolver;
import com.mikesamuel.cil.ast.passes.PassTestHelpers.PassRunner;
import com.mikesamuel.cil.ast.traits.MemberDeclaration;
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
          public ImmutableList<CompilationUnitNode> runPasses(
              Logger logger, ImmutableList<CompilationUnitNode> cus) {
            DeclarationPass declPass = new DeclarationPass(logger);
            TypeInfoResolver typeInfoResolver = declPass.run(cus);
            DisambiguationPass disambigPass = new DisambiguationPass(
                typeInfoResolver, logger, false);
            ImmutableList<CompilationUnitNode> rewritten =
                disambigPass.run(cus);
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
            "  /* (CallableInfo /C.f(2) @ (Ljava/lang/String;)Ljava/lang/String; : /C.f(2).<T>) */",
            "  <T extends String> T f(T s) { return s; }",
            "  /* (CallableInfo /C.f(3) @ (Ljava/lang/Object;)Ljava/lang/Object; : /C.f(3).<T>) */",
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

  private static final Decorator DECORATOR = new Decorator() {

    @Override
    public String decorate(BaseNode node) {
      if (node instanceof MemberDeclaration) {
        MemberDeclaration md = (MemberDeclaration) node;
        MemberInfo mi = md.getMemberInfo();
        return Java8Comments.blockComment(
            mi != null ? mi.toString() : "?",
            false);
      }
      return null;
    }

  };
}
