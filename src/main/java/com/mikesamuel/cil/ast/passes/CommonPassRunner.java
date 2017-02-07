package com.mikesamuel.cil.ast.passes;

import java.util.logging.Logger;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.mikesamuel.cil.ast.BaseInnerNode;
import com.mikesamuel.cil.ast.BaseNode;
import com.mikesamuel.cil.ast.CompilationUnitNode;
import com.mikesamuel.cil.ast.ExpressionNode;
import com.mikesamuel.cil.ast.NodeType;
import com.mikesamuel.cil.ast.StatementNode;
import com.mikesamuel.cil.ast.Trees;
import com.mikesamuel.cil.ast.meta.StaticType.TypePool;
import com.mikesamuel.cil.ast.meta.TypeInfoResolver;
import com.mikesamuel.cil.parser.Input;
import com.mikesamuel.cil.parser.LeftRecursion;
import com.mikesamuel.cil.parser.ParseErrorReceiver;
import com.mikesamuel.cil.parser.ParseResult;
import com.mikesamuel.cil.parser.ParseState;
import com.mikesamuel.cil.ptree.PTree;

/**
 * Runs the common passes.
 */
public final class CommonPassRunner {
  /** Receives warnings about passes. */
  public final Logger logger;

  private boolean useLongNames;
  private boolean injectCasts;
  private TypeInfoResolver typeInfoResolver;
  private TypePool typePool;

  /** @param logger receives warnings about passes. */
  public CommonPassRunner(Logger logger) {
    this.logger = logger;
  }

  /**
   * Runs passes on the given compilation units and returns the result.
   */
  public ImmutableList<CompilationUnitNode> run(
      Iterable<? extends CompilationUnitNode> unprocessed) {
    ImmutableList<CompilationUnitNode> cus = ImmutableList.copyOf(unprocessed);
    cus = new DefragmentTypesPass(logger).run(cus);

    DeclarationPass dp = new DeclarationPass(logger) {
      @SuppressWarnings("synthetic-access")
      @Override
      protected TypeInfoResolver getFallbackTypeInfoResolver() {
        if (typeInfoResolver != null) {
          return typeInfoResolver;
        } else {
          return super.getFallbackTypeInfoResolver();
        }
      }
    };
    typeInfoResolver = dp.run(cus);

    ExpressionScopePass scopePass = new ExpressionScopePass(
        typeInfoResolver, logger);
    scopePass.run(cus);

    DisambiguationPass disambigPass = new DisambiguationPass(
        typeInfoResolver, logger, useLongNames);
    cus = disambigPass.run(cus);

    // TODO: should the type pool be linked to any previous type pool?
    typePool = new TypePool(typeInfoResolver);
    ClassMemberPass classMemberPass = new ClassMemberPass(
        logger, typePool);
    classMemberPass.run(cus);

    TypingPass tp = new TypingPass(logger, typePool, injectCasts);
    return tp.run(cus);
  }

  /**
   * Processes one standalone compilation unit.
   * <p>
   * {@link #run(Iterable)} should be preferred where there are multiple
   * since that way only one type pool is allocated, and each compilation unit
   * can refer to types declared in the others.
   */
  public CompilationUnitNode run(CompilationUnitNode cu) {
    ImmutableList<CompilationUnitNode> after = run(ImmutableList.of(cu));
    Preconditions.checkState(after.size() == 1);
    return after.get(0);
  }

  /**
   * Runs passes on the given expression.
   */
  public ExpressionNode run(ExpressionNode e) {
    return (ExpressionNode) wrapProcessThenUnwrap(e);
  }

  /**
   * Runs passes on the given expression.
   */
  public StatementNode run(StatementNode e) {
    return (StatementNode) wrapProcessThenUnwrap(e);
  }

  private BaseNode wrapProcessThenUnwrap(BaseNode n) {
    CompilationUnitNode cu = createEnvelope();
    boolean replaced = replaceFirst(cu, n);
    Preconditions.checkState(replaced);
    CompilationUnitNode after = run(cu);
    return after.finder(n.getClass()).exclude(n.getClass())
        .findOne().get();
  }

  private static boolean replaceFirst(BaseNode container, BaseNode target) {
    if (container instanceof BaseInnerNode) {
      BaseInnerNode icontainer = (BaseInnerNode) container;
      for (int i = 0, n = icontainer.getNChildren(); i < n; ++i) {
        BaseNode child = icontainer.getChild(i);
        if (child.getNodeType() == target.getNodeType()) {
          icontainer.replace(i, target);
          return true;
        }
        if (replaceFirst(child, target)) {
          return true;
        }
      }
    }
    return false;
  }

  /** Whether to rewrite unqualified type names to qualified names. */
  public boolean shouldUseLongNames() {
    return useLongNames;
  }

  /** @see #shouldUseLongNames */
  public void setUseLongNames(boolean useLongNames) {
    this.useLongNames = useLongNames;
  }

  /** Whether to make implicit casts explicit. */
  public boolean shouldInjectCasts() {
    return injectCasts;
  }

  /** @see #shouldInjectCasts */
  public void setInjectCasts(boolean injectCasts) {
    this.injectCasts = injectCasts;
  }

  /** The logger that receives errors and warnings. */
  public Logger getLogger() {
    return logger;
  }


  private static CompilationUnitNode createEnvelope() {
    Input input = Input.builder()
        // A compilation unit with exactly one statement and one expression.
        .code("class C { {;} Object o = null; }")
        .source(CommonPassRunner.class.getName())
        .build();
    ParseResult result = PTree.complete(NodeType.CompilationUnit).getParSer()
        .parse(
            new ParseState(input), new LeftRecursion(),
            ParseErrorReceiver.DEV_NULL);
    Preconditions.checkState(
        ParseResult.Synopsis.SUCCESS == result.synopsis,
        input.content());
    ParseState afterParse = result.next();
    return (CompilationUnitNode) Trees.of(input, afterParse.output);
  }

  /**
   * After processing, the type info resolver that includes all types declared
   * in the compilation units and the fallback resolver that resolves system
   * classes.
   */
  public TypeInfoResolver getTypeInfoResolver() {
    return typeInfoResolver;
  }

  /**
   * Sets the class loader used for the fallback type info resolver.
   */
  public void setTypeInfoResolver(TypeInfoResolver fallback) {
    this.typeInfoResolver = fallback;
  }

  /**
   * The type pool from the last run.
   */
  public TypePool getTypePool() {
    return typePool;
  }
}
