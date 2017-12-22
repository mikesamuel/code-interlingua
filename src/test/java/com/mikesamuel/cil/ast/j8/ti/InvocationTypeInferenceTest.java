package com.mikesamuel.cil.ast.j8.ti;

import java.util.Map;
import java.util.logging.Logger;

import org.junit.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
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
import com.mikesamuel.cil.ast.meta.Name;
import com.mikesamuel.cil.ast.meta.StaticType;
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

  private static Infs assertInferences(Infs want, String[]... inputLines) {
    Logger logger = Logger.getAnonymousLogger();
    logger.setUseParentHandlers(false);
    ImmutableList<J8FileNode> cus = PassTestHelpers.parseCompilationUnits(
        logger, inputLines);
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
    Infs got = Infs.from(iti.inferTypeVariables());
    if (!want.equals(got)) {
      assertEquals(want.toString(), got.toString());
      assertEquals(want, got);
    }
    return got;
  }

  @Test
  public static void testInferenceProperType() {
    assertInferences(
        new Infs(
            false,
            ImmutableMap.of(
                "/com/mikesamuel/cil/ast/j8/ti"
                + "/InvocationTypeInferenceTest$Identity.f(1).<T>",
                "/java/lang/String"),
            "/java/lang/String",
            ImmutableSet.<String>of()),
        new String[] {
            "//Foo.java",
            "import " + Identity.class.getEnclosingClass().getName()
            + ".Identity;",
            "class Foo {",
            "  { Identity.f(\"\"); }",
            "}",
        });
  }

  @Test
  public static void testInferenceNull() {
    assertInferences(
        new Infs(
            false,
            ImmutableMap.of(
                "/com/mikesamuel/cil/ast/j8/ti"
                + "/InvocationTypeInferenceTest$Identity.f(1).<T>",
                "/java/lang/Object"),
            "/java/lang/Object",
            ImmutableSet.<String>of()),
        new String[] {
            "//Foo.java",
            "import " + Identity.class.getEnclosingClass().getName()
            + ".Identity;",
            "class Foo {",
            "  { Identity.f(null); }",
            "}",
        });
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

  // TODO: test zero actual parameters
  //     T foo() { return null; }
  //     T bar() { return null; }
  //     String x = b ? foo() : bar();  // in polyexpr context
  //     foo();  // in standalone context
  // TODO: test zero actual parameters to variadic
  //     T foo(T... a) { return null; }
  //     in both contexts above


  static final class Infs {
    final boolean dependsOnUncheckedConversion;
    final ImmutableMap<String, String> resolutions;
    final String normalResultType;
    final ImmutableSet<String> thrownTypes;

    Infs(boolean dependsOnUncheckedConversion,
        ImmutableMap<String, String> resolutions, String normalResultType,
        ImmutableSet<String> thrownTypes) {
      this.dependsOnUncheckedConversion = dependsOnUncheckedConversion;
      this.resolutions = resolutions;
      this.normalResultType = normalResultType;
      this.thrownTypes = thrownTypes;
    }

    static Infs from(Inferences infs) {
      ImmutableMap.Builder<String, String> rb = ImmutableMap.builder();
      for (Map.Entry<Name, StaticType> e : infs.resolutions.entrySet()) {
        rb.put(
            e.getKey().toString(),
            e.getValue().typeSpecification.toString());
      }
      ImmutableSet.Builder<String> tb = ImmutableSet.builder();
      for (StaticType tt : infs.thrownTypes) {
        tb.add(tt.typeSpecification.toString());
      }
      return new Infs(
          infs.dependsOnUncheckedConversion,
          rb.build(),
          infs.normalResultType.typeSpecification.toString(),
          tb.build());
    }

    @Override
    public int hashCode() {
      final int prime = 31;
      int result = 1;
      result = prime * result + (dependsOnUncheckedConversion ? 1231 : 1237);
      result = prime * result + ((normalResultType == null) ? 0 : normalResultType.hashCode());
      result = prime * result + ((resolutions == null) ? 0 : resolutions.hashCode());
      result = prime * result + ((thrownTypes == null) ? 0 : thrownTypes.hashCode());
      return result;
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      }
      if (obj == null) {
        return false;
      }
      if (getClass() != obj.getClass()) {
        return false;
      }
      Infs other = (Infs) obj;
      if (dependsOnUncheckedConversion != other.dependsOnUncheckedConversion) {
        return false;
      }
      if (normalResultType == null) {
        if (other.normalResultType != null) {
          return false;
        }
      } else if (!normalResultType.equals(other.normalResultType)) {
        return false;
      }
      if (resolutions == null) {
        if (other.resolutions != null) {
          return false;
        }
      } else if (!resolutions.equals(other.resolutions)) {
        return false;
      }
      if (thrownTypes == null) {
        if (other.thrownTypes != null) {
          return false;
        }
      } else if (!thrownTypes.equals(other.thrownTypes)) {
        return false;
      }
      return true;
    }

    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder();
      sb.append("(Infs");
      if (dependsOnUncheckedConversion) {
        sb.append(" dependsOnUncheckedConversion");
      }
      if (!resolutions.isEmpty()) {
        sb.append(" resolutions=").append(resolutions);
      }
      sb.append(" normalResultType=").append(normalResultType);
      if (!thrownTypes.isEmpty()) {
        sb.append(" thrownTypes=").append(thrownTypes);
      }
      return sb.append(')').toString();
    }
  }
}
