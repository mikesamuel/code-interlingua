package com.mikesamuel.cil.ast.j8.ti;

import java.util.logging.Logger;

import org.junit.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.mikesamuel.cil.ast.j8.ArgumentListNode;
import com.mikesamuel.cil.ast.j8.CompilationUnitNode;
import com.mikesamuel.cil.ast.j8.J8BaseInnerNode;
import com.mikesamuel.cil.ast.j8.J8BaseNode;
import com.mikesamuel.cil.ast.j8.J8FileNode;
import com.mikesamuel.cil.ast.j8.J8NodeType;
import com.mikesamuel.cil.ast.j8.MethodNameNode;
import com.mikesamuel.cil.ast.j8.StatementExpressionNode;
import com.mikesamuel.cil.ast.meta.CallableInfo;
import com.mikesamuel.cil.ast.meta.JavaLang;
import com.mikesamuel.cil.ast.meta.Name;
import com.mikesamuel.cil.ast.meta.StaticType.TypePool;
import com.mikesamuel.cil.ast.passes.CommonPassRunner;
import com.mikesamuel.cil.ast.passes.PassTestHelpers;
import com.mikesamuel.cil.ast.passes.AbstractRewritingPass.Parent;
import com.mikesamuel.cil.parser.SList;

import junit.framework.TestCase;

@SuppressWarnings("javadoc")
public final class InvocationTypeInferenceTest extends TestCase {

  public static final class Identity {
    public static <T> T f(T x) { return x; }
  }

  @Test
  public void testInference() {
    Logger logger = Logger.getAnonymousLogger();
    ImmutableList<J8FileNode> cus = PassTestHelpers.parseCompilationUnits(
        logger,
        new String[] {
            "//Foo.java",
            "import " + Identity.class.getEnclosingClass().getName()
            + ".Identity;",
            "class Foo {",
            "  { Identity.f(\"\"); }",
            "}",
        });
    CommonPassRunner runner = new CommonPassRunner(logger);
    cus = runner.run(cus);
    CompilationUnitNode cu = (CompilationUnitNode)
        Iterables.getOnlyElement(cus);
    StatementExpressionNode invocationStmt = cu.finder(
        StatementExpressionNode.class).findOne().get();
    MethodNameNode methodName = invocationStmt.finder(MethodNameNode.class)
        .exclude(J8NodeType.ArgumentList)
        .findOne().get();
    ArgumentListNode argumentList =
        invocationStmt.finder(ArgumentListNode.class)
        .exclude(J8NodeType.ArgumentList)
        .findOne().get();

    boolean isInvocationPolyExpr = false;
    TypePool typePool = runner.getTypePool();
    Name className = Name.DEFAULT_PACKAGE;
    for (String id : getClass().getName().split("[.]")) {
      className = className.child(
          id,
          Character.isLowerCase(id.charAt(0))
          ? Name.Type.PACKAGE : Name.Type.CLASS);
    }
    className = className.child("Identity", Name.Type.CLASS);
    SList<Parent> argListPath = chainTo(argumentList, cu, null);
    ImmutableList.Builder<SList<Parent>> pathsToActuals =
        ImmutableList.builder();
    for (int i = 0, n = argumentList.getNChildren(); i < n; ++i) {
      pathsToActuals.add(SList.append(
          argListPath, new Parent(i, argumentList)));
    }

    CallableInfo ci = methodName.getCallableInfo();
    InvocationTypeInference iti = new InvocationTypeInference(
        logger, typePool, ci,
        methodName.getSourcePosition(),
        pathsToActuals.build(), isInvocationPolyExpr);
    assertTrue(iti.isInvocationApplicable());
    Inferences infs = iti.inferTypeVariables();
    assertEquals(
        JavaLang.JAVA_LANG_STRING, infs.normalResultType.typeSpecification);
    assertEquals(
        ImmutableMap.of(
            ci.typeParameters.get(0),
            typePool.type(JavaLang.JAVA_LANG_STRING, null, logger)
        ),
        infs.resolutions);
    assertEquals(
        ImmutableList.of(),
        infs.thrownTypes);
  }


  private static SList<Parent> chainTo(
      J8BaseNode target, J8BaseNode node, SList<Parent> p) {
    for (int i = 0, n = node.getNChildren(); i < n; ++i) {
      J8BaseNode child = node.getChild(i);
      SList<Parent> c = SList.<Parent>append(
          p, new Parent(i, (J8BaseInnerNode) node));
      if (child == target) {
        return c;
      }
      SList<Parent> attempt = chainTo(target, child, c);
      if (attempt != null) {
        return attempt;
      }
    }
    return null;
  }
}
