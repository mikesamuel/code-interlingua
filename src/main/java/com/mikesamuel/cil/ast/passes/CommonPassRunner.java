package com.mikesamuel.cil.ast.passes;

import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.mikesamuel.cil.ast.Trees;
import com.mikesamuel.cil.ast.j8.CompilationUnitNode;
import com.mikesamuel.cil.ast.j8.ExpressionNode;
import com.mikesamuel.cil.ast.j8.J8BaseInnerNode;
import com.mikesamuel.cil.ast.j8.J8BaseNode;
import com.mikesamuel.cil.ast.j8.J8FileNode;
import com.mikesamuel.cil.ast.j8.J8NodeType;
import com.mikesamuel.cil.ast.j8.StatementNode;
import com.mikesamuel.cil.ast.meta.MemberInfoPool;
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
  private Level errorLevel = Level.SEVERE;

  private boolean useLongNames;
  private boolean injectCasts;
  private TypeInfoResolver typeInfoResolver;
  private TypePool typePool;
  private MethodVariantPool methodVariantPool;
  private MemberInfoPool memberInfoPool;

  /** @param logger receives warnings about passes. */
  public CommonPassRunner(Logger logger) {
    this.logger = logger;
  }

  /**
   * Runs passes on the given compilation units and returns the result.
   */
  public ImmutableList<J8FileNode> run(
      Iterable<? extends J8FileNode> unprocessed) {
    ImmutableList<J8FileNode> cus = ImmutableList.copyOf(unprocessed);
    cus = new DefragmentTypesPass(logger).setErrorLevel(errorLevel).run(cus);

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
    dp.setErrorLevel(errorLevel);
    DeclarationPass.Result dpResult = dp.run(cus);
    typeInfoResolver = dpResult.typeInfoResolver;
    methodVariantPool = dpResult.methodVariantPool;

    ExpressionScopePass scopePass = new ExpressionScopePass(
        typeInfoResolver, logger);
    scopePass.setErrorLevel(errorLevel);
    scopePass.run(cus);

    DisambiguationPass disambigPass = new DisambiguationPass(
        typeInfoResolver, logger, useLongNames);
    disambigPass.setErrorLevel(errorLevel);
    cus = disambigPass.run(cus);

    // TODO: should the type pool be linked to any previous type pool?
    typePool = new TypePool(typeInfoResolver);
    ClassMemberPass classMemberPass = new ClassMemberPass(
        logger, typePool);
    classMemberPass.setErrorLevel(errorLevel);
    classMemberPass.run(cus);

    TypingPass tp = new TypingPass(logger, typePool, injectCasts);
    tp.setErrorLevel(errorLevel);
    this.memberInfoPool = tp.memberInfoPool;
    return tp.run(cus);
  }

  /**
   * Processes one standalone compilation unit.
   * <p>
   * {@link #run(Iterable)} should be preferred where there are multiple
   * since that way only one type pool is allocated, and each compilation unit
   * can refer to types declared in the others.
   */
  public J8FileNode run(J8FileNode fn) {
    ImmutableList<J8FileNode> after = run(ImmutableList.of(fn));
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

  private J8BaseNode wrapProcessThenUnwrap(J8BaseNode n) {
    CompilationUnitNode cu = createEnvelope();
    boolean replaced = replaceFirst(cu, n);
    Preconditions.checkState(replaced);
    J8BaseNode after = (J8BaseNode) run(cu);
    return after.finder(n.getClass()).exclude(n.getClass())
        .findOne().get();
  }

  private static boolean replaceFirst(J8BaseNode container, J8BaseNode target) {
    if (container instanceof J8BaseInnerNode) {
      J8BaseInnerNode icontainer = (J8BaseInnerNode) container;
      for (int i = 0, n = icontainer.getNChildren(); i < n; ++i) {
        J8BaseNode child = icontainer.getChild(i);
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

  /**
   * The log level used for error messages noted by this pass.
   */
  public Level getErrorLevel() {
    return errorLevel;
  }

  /**
   * Sets the log level used for error messages noted by this pass.
   */
  public void setErrorLevel(Level newErrorLevel) {
    this.errorLevel = Preconditions.checkNotNull(newErrorLevel);
  }


  private static CompilationUnitNode createEnvelope() {
    Input input = Input.builder()
        // A compilation unit with exactly one statement and one expression.
        .code("class C { {;} Object o = null; }")
        .source(CommonPassRunner.class.getName())
        .build();
    ParseResult result = PTree.complete(J8NodeType.CompilationUnit).getParSer()
        .parse(
            new ParseState(input), new LeftRecursion(),
            ParseErrorReceiver.DEV_NULL);
    Preconditions.checkState(
        ParseResult.Synopsis.SUCCESS == result.synopsis,
        input.content());
    ParseState afterParse = result.next();
    return (CompilationUnitNode)
        Trees.forGrammar(J8NodeType.GRAMMAR)
        .of(input, afterParse.output);
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

  /**
   * The pool used to allocate method variants.
   */
  public MethodVariantPool getMethodVariantPool() {
    return methodVariantPool;
  }

  /**
   * A pool used to amortize the cost of repeated member info computations.
   */
  public MemberInfoPool getMemberInfoPool() {
    return memberInfoPool;
  }
}
