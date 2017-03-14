package com.mikesamuel.cil.template;

import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.mikesamuel.cil.HereBe;
import com.mikesamuel.cil.ast.Trees;
import com.mikesamuel.cil.ast.j8.CompilationUnitNode;
import com.mikesamuel.cil.ast.j8.J8BaseNode;
import com.mikesamuel.cil.ast.j8.J8FileNode;
import com.mikesamuel.cil.ast.j8.J8NodeType;
import com.mikesamuel.cil.ast.j8.SingleStaticImportDeclarationNode;
import com.mikesamuel.cil.ast.meta.TypeInfoResolver;
import com.mikesamuel.cil.ast.passes.CommonPassRunner;
import com.mikesamuel.cil.event.Event;
import com.mikesamuel.cil.parser.Input;
import com.mikesamuel.cil.parser.LeftRecursion;
import com.mikesamuel.cil.parser.ParseErrorReceiver;
import com.mikesamuel.cil.parser.ParseResult;
import com.mikesamuel.cil.parser.ParseState;
import com.mikesamuel.cil.parser.SList;
import com.mikesamuel.cil.ptree.PTree;
import com.mikesamuel.cil.util.LogUtils;

/**
 * A bundle of templates parsed in non-standard mode.
 */
public class TemplateBundle {

  private final Logger logger;
  private final ImmutableList.Builder<J8FileNode> fileNodes
      = ImmutableList.builder();
  private ClassLoader loader;

  static final boolean DEBUG_INTERP = false;

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
  public static boolean optsIntoTemplateProcessing(J8FileNode node) {
    for (SingleStaticImportDeclarationNode imp
         : ((J8BaseNode) node).finder(SingleStaticImportDeclarationNode.class)
              .exclude(J8NodeType.TypeDeclaration,
                       J8NodeType.TemplateDirectives,
                       J8NodeType.TemplateInterpolation)
              .allowNonStandard(true)  // Descend into pseudo roots.
              .find()) {
      if (isOptInDeclaration(imp)) {
        return true;
      }
    }
    return false;
  }

  static boolean isOptInDeclaration(SingleStaticImportDeclarationNode imp) {
    return OPT_IN_IMPORT.equals(imp.getTextContent("."));
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
   * The logger that receives messages about
   */
  public Logger getLogger() {
    return logger;
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

    ParseResult result = PTree.complete(J8NodeType.CompilationUnit).getParSer()
        .parse(new ParseState(inp), new LeftRecursion(), err);
    switch (result.synopsis) {
      case SUCCESS: {
        ParseState afterParse = result.next();
        SList<Event> parseEvents = afterParse.output;
        ImmutableList<Event> fixedEvents = Templates.postprocess(
            inp, SList.forwardIterable(parseEvents));
        J8BaseNode root = Trees.forGrammar(J8NodeType.GRAMMAR)
            .of(inp, fixedEvents);
        fileNodes.add((J8FileNode) root);
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
    passes.setErrorLevel(Level.WARNING);
    ImmutableList<J8FileNode> processed = passes.run(fileNodes.build());

    ImmutableList.Builder<CompilationUnitNode> b = ImmutableList.builder();
    for (J8FileNode fn : processed) {
      if (optsIntoTemplateProcessing(fn)) {
        apply(fn, inputObj, passes, b);
      } else if (fn instanceof CompilationUnitNode) {
        LogUtils.log(
            logger, Level.FINE, fn,
            "Skipping template processing for file that does not opt-in.",
            null);
        b.add((CompilationUnitNode) fn);
      } else {
        LogUtils.log(
            logger, Level.SEVERE,
            fn, fn.getVariant() + " does not opt into template processing",
            null);
      }
    }
    return b.build();
  }

  void apply(
      J8FileNode fn, DataBundle input, CommonPassRunner passes,
      ImmutableList.Builder<CompilationUnitNode> out) {
    TemplateProcessingPass ppass = new TemplateProcessingPass(
        logger, passes.getTypePool(), getLoader(), input, out);

    ppass.run(ImmutableList.of((J8FileNode) fn.deepClone()));
  }
}
