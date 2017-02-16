package com.mikesamuel.cil.template;

import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.common.base.Functions;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.mikesamuel.cil.HereBe;
import com.mikesamuel.cil.ast.BaseNode;
import com.mikesamuel.cil.ast.CompilationUnitNode;
import com.mikesamuel.cil.ast.NodeType;
import com.mikesamuel.cil.ast.SingleStaticImportDeclarationNode;
import com.mikesamuel.cil.ast.Trees;
import com.mikesamuel.cil.ast.meta.TypeInfoResolver;
import com.mikesamuel.cil.ast.passes.CommonPassRunner;
import com.mikesamuel.cil.expr.DataBundle;
import com.mikesamuel.cil.expr.InterpretationContext;
import com.mikesamuel.cil.expr.InterpretationContextImpl;
import com.mikesamuel.cil.expr.Locals;
import com.mikesamuel.cil.parser.Input;
import com.mikesamuel.cil.parser.LeftRecursion;
import com.mikesamuel.cil.parser.ParseErrorReceiver;
import com.mikesamuel.cil.parser.ParseResult;
import com.mikesamuel.cil.parser.ParseState;
import com.mikesamuel.cil.ptree.PTree;
import com.mikesamuel.cil.util.LogUtils;

/**
 * A bundle of templates parsed in non-standard mode.
 */
public class TemplateBundle {

  private final Logger logger;
  private final ImmutableList.Builder<CompilationUnitNode> cus
      = ImmutableList.builder();
  private ClassLoader loader;

  TemplateBundle(Logger logger) {
    this.logger = Preconditions.checkNotNull(logger);
  }

  {
    loader = TemplateBundle.class.getClassLoader();
    if (loader == null) { loader = ClassLoader.getSystemClassLoader(); }
  }

  private static final String OPT_IN_IMPORT =
      HereBe.class.getName() + "." + HereBe._TEMPLATES_.name();

  /**
   * True iff the given compilation unit opts-into template processing.
   * <p>
   * @see HereBe#_TEMPLATES_
   */
  public static boolean optsIntoTemplateProcessing(CompilationUnitNode node) {
    for (SingleStaticImportDeclarationNode imp
         : node.finder(SingleStaticImportDeclarationNode.class)
              .exclude(NodeType.TypeDeclaration)
              .find()) {
      if (OPT_IN_IMPORT.equals(imp.getTextContent("."))) {
        return true;
      }
    }
    return false;
  }

  /**
   * The loader used to resolve information about core library classes.
   */
  public ClassLoader getLoader() {
    return loader;
  }

  /**
   * Sets the loader used to resolve information about core library classes.
   */
  public void setLoader(ClassLoader newLoader) {
    this.loader = Preconditions.checkNotNull(newLoader);
  }

  /**
   * Adds a compilation unit to the bundle.
   */
  public TemplateBundle addCompilationUnit(Input inp) {
    Preconditions.checkArgument(inp.allowNonStandardProductions);

    class ParseErrorReceiverImpl implements ParseErrorReceiver {
      int greatestIndex = 0;
      String bestMessage;

      @Override
      public void error(ParseState state, String message) {
        if (state.index >= greatestIndex) {
          greatestIndex = state.index;
          bestMessage = message;
        }
      }

    }

    ParseErrorReceiverImpl err = new ParseErrorReceiverImpl();

    ParseResult result = PTree.complete(NodeType.CompilationUnit).getParSer()
        .parse(new ParseState(inp), new LeftRecursion(), err);
    switch (result.synopsis) {
      case SUCCESS: {
        ParseState afterParse = result.next();
        BaseNode root = Trees.of(inp, afterParse.output);
        cus.add((CompilationUnitNode) root);
        return this;
      }
      case FAILURE:
        String message = err.bestMessage != null
            ? err.bestMessage : "Failed to parse compilation unit";
        LogUtils.log(
            logger, Level.SEVERE, inp.getSourcePosition(err.greatestIndex),
            message, null);
        return this;
    }
    throw new AssertionError(result);
  }

  /**
   * Processes compilation units and interprets template directives to produce
   * an output bundle.
   */
  public ImmutableList<CompilationUnitNode> apply(DataBundle inputObj) {
    CommonPassRunner passes = new CommonPassRunner(logger);
    passes.setTypeInfoResolver(
        TypeInfoResolver.Resolvers.forClassLoader(getLoader()));
    ImmutableList<CompilationUnitNode> processed = passes.run(cus.build());

    InterpretationContext<Object> context = new InterpretationContextImpl(
        logger, getLoader(), passes.getTypePool());
    Locals<Object> locals = new Locals<>();
    locals.initializeFrom(inputObj, context, Functions.identity());

    ImmutableList.Builder<CompilationUnitNode> b = ImmutableList.builder();
    for (CompilationUnitNode cu : processed) {
      if (!optsIntoTemplateProcessing(cu)) {
        b.add(cu);
      } else {

      }
    }
    return b.build();
  }

}
