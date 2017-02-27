package com.mikesamuel.cil.template;

import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Nullable;

import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.mikesamuel.cil.HereBe;
import com.mikesamuel.cil.ast.BaseInnerNode;
import com.mikesamuel.cil.ast.BaseNode;
import com.mikesamuel.cil.ast.CompilationUnitNode;
import com.mikesamuel.cil.ast.ExpressionNode;
import com.mikesamuel.cil.ast.IdentifierNode;
import com.mikesamuel.cil.ast.NodeType;
import com.mikesamuel.cil.ast.NodeTypeHintNode;
import com.mikesamuel.cil.ast.SingleStaticImportDeclarationNode;
import com.mikesamuel.cil.ast.TemplateComprehensionNode;
import com.mikesamuel.cil.ast.TemplateConditionNode;
import com.mikesamuel.cil.ast.TemplateDirectiveNode;
import com.mikesamuel.cil.ast.TemplateDirectivesNode;
import com.mikesamuel.cil.ast.TemplateInterpolationNode;
import com.mikesamuel.cil.ast.TemplateLocalNode;
import com.mikesamuel.cil.ast.TemplateLoopNode;
import com.mikesamuel.cil.ast.TemplatePseudoRootNode;
import com.mikesamuel.cil.ast.Trees;
import com.mikesamuel.cil.ast.meta.Name;
import com.mikesamuel.cil.ast.meta.StaticType;
import com.mikesamuel.cil.ast.meta.TypeInfoResolver;
import com.mikesamuel.cil.ast.passes.AbstractRewritingPass;
import com.mikesamuel.cil.ast.passes.CommonPassRunner;
import com.mikesamuel.cil.ast.traits.FileNode;
import com.mikesamuel.cil.event.Event;
import com.mikesamuel.cil.expr.Completion;
import com.mikesamuel.cil.expr.DataBundle;
import com.mikesamuel.cil.expr.InterpretationContext;
import com.mikesamuel.cil.expr.InterpretationContextImpl;
import com.mikesamuel.cil.expr.Interpreter;
import com.mikesamuel.cil.expr.Locals;
import com.mikesamuel.cil.parser.ForceFitState;
import com.mikesamuel.cil.parser.Input;
import com.mikesamuel.cil.parser.LeftRecursion;
import com.mikesamuel.cil.parser.ParSer;
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
  private final ImmutableList.Builder<FileNode> fileNodes
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
  public static boolean optsIntoTemplateProcessing(FileNode node) {
    for (SingleStaticImportDeclarationNode imp
         : ((BaseNode) node).finder(SingleStaticImportDeclarationNode.class)
              .exclude(NodeType.TypeDeclaration)
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
        SList<Event> parseEvents = afterParse.output;
        ImmutableList<Event> fixedEvents = Templates.generalize(
            inp, SList.forwardIterable(parseEvents));
        BaseNode root = Trees.of(inp, fixedEvents);
        fileNodes.add((FileNode) root);
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
    ImmutableList<FileNode> processed = passes.run(fileNodes.build());

    ImmutableList.Builder<CompilationUnitNode> b = ImmutableList.builder();
    for (FileNode fn : processed) {
      if (optsIntoTemplateProcessing(fn)) {
        apply(fn, inputObj, passes, b);
      } else {
        LogUtils.log(
            logger, Level.FINE, fn,
            "Skipping template processing for file that does not opt-in.",
            null);
        // TODO: better error when fn contains template instructions or is
        // a pseudo root.
        b.add((CompilationUnitNode) fn);
      }
    }
    return b.build();
  }

  void apply(
      FileNode fn, DataBundle input, CommonPassRunner passes,
      ImmutableList.Builder<CompilationUnitNode> out) {
    InterpretationContext<Object> context = new InterpretationContextImpl(
        logger, getLoader(), passes.getTypePool());
    context.setThisValue(null, input);
    Locals<Object> fileLocals = new Locals<>();

    Interpreter<Object> interpreter = new Interpreter<>(context);
    TemplateProcessingPass ppass = new TemplateProcessingPass(
        logger, interpreter, out, fileLocals);
    ppass.run(ImmutableList.of(fn.deepClone()));
  }



  static final class TemplateProcessingPass
  extends AbstractRewritingPass {

    final Interpreter<Object> interpreter;
    final InterpretationContext<Object> context;
    final ImmutableList.Builder<CompilationUnitNode> out;
    private final LinkedList<TemplateScope> templateScopes =
        Lists.newLinkedList();


    TemplateProcessingPass(
        Logger logger,
        Interpreter<Object> interpreter,
        ImmutableList.Builder<CompilationUnitNode> out,
        Locals<Object> locals) {
      super(logger);
      this.interpreter = interpreter;
      this.context = interpreter.context;
      this.out = out;
      this.templateScopes.add(new TemplateScope(locals));
    }


    static final class TemplateScope {
      final Locals<Object> locals;
      boolean elide;

      TemplateScope(Locals<Object> locals) {
        this.locals = locals;
      }
    }

    private final Multimap<BaseNode, Interpolation> parentToInterpolations
      = Multimaps.newMultimap(
          new IdentityHashMap<BaseNode, Collection<Interpolation>>(),
          new Supplier<List<Interpolation>>() {

            @Override
            public List<Interpolation> get() {
              return new ArrayList<>();
            }

          });


    static final class Interpolation {
      final int indexInParent;
      final Optional<NodeType> typeHint;
      final ImmutableList<Object> results;

      Interpolation(
          int indexInParent,
          Optional<NodeType> typeHint,
          ImmutableList<Object> results) {
        this.indexInParent = indexInParent;
        this.typeHint = typeHint;
        this.results = results;
      }
    }

    @Override
    protected ProcessingStatus previsit(
        BaseNode node,
        @Nullable SList<AbstractRewritingPass.Parent> pathFromRoot) {

      if (node.getVariant().isTemplateEnd()) {
        this.templateScopes.remove(templateScopes.size() - 1);
      }
      TemplateScope templateScope = templateScopes.getLast();
      // If we have elided something, e.g. due to %%if(false), we still need to
      // descend into TemplateDirectives so we can find the end of scope above.
      if (node.getNodeType() != NodeType.TemplateDirectives
          && templateScope.elide) {
        return ProcessingStatus.REMOVE;
      }
      if (node.getVariant().isTemplateStart()) {
        Locals<Object> locals = new Locals<>(templateScope.locals);
        templateScope = new TemplateScope(locals);
        templateScopes.add(templateScope);
      }

      switch (node.getNodeType()) {
        case CompilationUnit: {
          // Produce an output for out.
          // Doing this clone and recursing out-of-band means that a template
          // loop or conditional around a compilation unit can attach zero or
          // more compilation units to the output.
          CompilationUnitNode clone = (CompilationUnitNode) node.deepClone();
          visitChildren(clone, null);
          out.add(clone);
          return ProcessingStatus.BREAK;
        }
        case ImportDeclaration: {
          // Remove the opt-in declaration so that the template output is
          // not itself a template.
          SingleStaticImportDeclarationNode d = node.firstChildWithType(
              SingleStaticImportDeclarationNode.class);
          if (d != null && isOptInDeclaration(d)) {
            return ProcessingStatus.REMOVE;
          }
          break;
        }
        case TemplateDirective: {
          TemplateDirectiveNode d = (TemplateDirectiveNode) node;
          switch (d.getVariant()) {
            // Post-processing of template local and hoisted template
            // declarations will work.
            case BlockStart: {
              // Pre-declare locals so that uses in initializers don't escape to
              // outer scope.
              for (BaseNode child : d.getChildren()) {
                if (child instanceof TemplateLocalNode) {
                  TemplateLocalNode local = (TemplateLocalNode) child;
                  Optional<IdentifierNode> ident = local
                      .finder(IdentifierNode.class)
                      .exclude(NodeType.VariableInitializer)
                      .findOne();
                  if (ident.isPresent()) {
                    templateScope.locals.declare(
                        Name.root(ident.get().getValue(), Name.Type.LOCAL),
                        Functions.identity());
                  }
                }
              }
              return ProcessingStatus.CONTINUE;
            }

            // We will deal with evaluating the expression in post process
            // so we know that template interpolations in the expression have
            // finished.
            case Else:
            case IfStart:

            // Pop already handled.
            case End:

            // Handled in post.
            case LoopStart:
              return ProcessingStatus.CONTINUE;

            case TemplateStart:
              templateScope.elide = true;
              return ProcessingStatus.REMOVE;
          }
          throw new AssertionError(d);
        }

        default:
          break;
      }
      return ProcessingStatus.CONTINUE;
    }

    @Override
    protected ProcessingStatus postvisit(
        BaseNode node,
        @Nullable SList<AbstractRewritingPass.Parent> pathFromRoot) {
      // After we've processed all the children, we should have all the
      // interpolation results, and so are ready to do replacements.
      if (parentToInterpolations.containsKey(node)) {
        int pos = 0;
        int n = node.getNChildren();
        ImmutableList.Builder<ForceFitState.FitPart> parts =
            ImmutableList.builder();
        for (Interpolation interp : parentToInterpolations.removeAll(node)) {
          Preconditions.checkState(interp.indexInParent >= pos);
          for (; pos < interp.indexInParent; ++pos) {
            // This fixed node might actually be a template interpolation that
            // failed to produce a result.  That's OK.
            parts.add(ForceFitState.FitPart.fixedNode(node.getChild(pos)));
          }
          for (Object value : interp.results) {
            parts.add(ForceFitState.FitPart.interpolatedValue(
                interp.typeHint, value));
          }
          ++pos;
        }
        for (; pos < n; ++pos) {
          parts.add(ForceFitState.FitPart.fixedNode(node.getChild(pos)));
        }

        ForceFitState state = new ForceFitState(parts.build());
        ParSer fitter = PTree.complete(node.getVariant().getParSer())
            .getParSer();
        ForceFitState after = fitter.forceFit(state);
        if (DEBUG_INTERP) {
          System.err.println("Interpolate into " + node.getVariant());
          System.err.println("\tfitter=" + fitter);
          System.err.println("\tstate parts=" + state.parts);
          System.err.println("\tafter=" + after.fits);
        }
        if (after.fits.isEmpty()) {
          error(node, node.getVariant() + " does not fit " + state.parts);
          return ProcessingStatus.BREAK;
        } else {
          ForceFitState.PartialFit bestFit = Iterables.getFirst(
              after.fits, null);
          Iterator<BaseNode> insertions =
              SList.forwardIterable(bestFit.resolutions).iterator();

          ImmutableList.Builder<BaseNode> fixed = ImmutableList.builder();
          for (ForceFitState.FitPart part : after.parts) {
            if (part instanceof ForceFitState.FixedNode) {
              fixed.add(((ForceFitState.FixedNode) part).child);
            } else {
              fixed.add(insertions.next());
            }
          }
          Preconditions.checkState(!insertions.hasNext());
          ((BaseInnerNode) node).replaceChildren(fixed.build());
        }
      }

      // Now that we have replaced interpolations, we can process nodes.
      TemplateScope templateScope = templateScopes.getLast();
      switch (node.getNodeType()) {
        case TemplateDirective: {
          TemplateDirectiveNode directive = (TemplateDirectiveNode) node;
          TemplateDirectiveNode.Variant v = directive.getVariant();
          switch (v) {
            case BlockStart:
              // Done.
              return ProcessingStatus.REMOVE;
            case Else:
            case IfStart:
              ExpressionNode condition = directive.firstChildWithType(
                  ExpressionNode.class);
              if (condition == null
                  && v != TemplateDirectiveNode.Variant.Else) {
                error(directive, "Missing %%if condition");
              } else {
                Completion<Object> conditionResult = interpreter.interpret(
                    condition, templateScope.locals);
                switch (context.toBoolean(conditionResult.value)) {
                  case FALSE:
                    templateScope.elide = true;
                    return ProcessingStatus.REMOVE;
                  case OTHER:
                    error(
                        condition,
                        "Expected boolean result from %%if condition, not "
                        + conditionResult);
                    return ProcessingStatus.CONTINUE;
                  case TRUE:
                    return ProcessingStatus.REMOVE;
                }
              }
              return ProcessingStatus.REMOVE;
            case End:
              return ProcessingStatus.REMOVE;
            case LoopStart: {
              IdentifierNode elementNameNode =
                  node.firstChildWithType(IdentifierNode.class);
              ExpressionNode seriesExprNode =
                  node.firstChildWithType(ExpressionNode.class);
              // Skip the body.  Below we extract and clone it
              templateScope.elide = true;
              if (elementNameNode == null || seriesExprNode == null) {
                error(node, "Malformed %%for loop");
                return ProcessingStatus.BREAK;
              }
              Name elementVarName = Name.root(
                  elementNameNode.getValue(), Name.Type.LOCAL);
              Locals<Object> loopLocals = new Locals<>(templateScope.locals);
              loopLocals.declare(elementVarName, Functions.identity());
              Completion<Object> seriesResult = interpreter.interpret(
                  seriesExprNode, loopLocals);
              if (!context.completedNormallyWithoutError(seriesResult)) {
                return ProcessingStatus.BREAK;
              }
              @SuppressWarnings("synthetic-access")
              TemplatePseudoRootNode bodyOfDirective =
                  getBodyOfDirective(pathFromRoot);
              ImmutableList.Builder<BaseNode> replacements =
                  ImmutableList.builder();
              this.templateScopes.add(new TemplateScope(loopLocals));
              interpreter.forEach(seriesExprNode, null, seriesResult.value,
                  new Function<Object, Completion<Object>>() {

                    @Override
                    public Completion<Object> apply(Object element) {
                      TemplatePseudoRootNode copy = bodyOfDirective.deepClone();
                      loopLocals.set(elementVarName, element);
                      @SuppressWarnings("synthetic-access")
                      ProcessingStatus result = visit(copy, null);
                      Preconditions.checkState(
                          result.replacements.size() == 1
                          && copy == result.replacements.get(0));
                      replacements.addAll(copy.getChildren());
                      return Completion.normal(context.nullValue());
                    }

                  },
                  context.runtimeType(seriesResult.value));
              this.templateScopes.removeLast();
              return ProcessingStatus.replace(replacements.build());
            }
            case TemplateStart:
              // TODO: store in scope
              return ProcessingStatus.REMOVE;
          }
          throw new AssertionError(v);
        }
        case TemplateInterpolation: {
          if (DEBUG_INTERP) {
            System.err.println("Entering " + node.getSourcePosition());
          }
          TemplateInterpolationNode interp = (TemplateInterpolationNode) node;
          NodeTypeHintNode typeHintNode = interp.firstChildWithType(
              NodeTypeHintNode.class);
          TemplateComprehensionNode comprehension = interp.firstChildWithType(
              TemplateComprehensionNode.class);
          if (comprehension == null) {
            error(interp, "Missing experssion to interpolate");
            return ProcessingStatus.BREAK;
          }

          Optional<NodeType> typeHint = Optional.absent();
          if (typeHintNode != null) {
            IdentifierNode hintIdent = typeHintNode.firstChildWithType(
                IdentifierNode.class);
            NodeType typeHintNodeType = null;
            if (hintIdent != null) {
              try {
                typeHintNodeType = NodeType.valueOf(hintIdent.getValue());
              } catch (@SuppressWarnings("unused")
                       IllegalArgumentException ex) {
                // Check for nullity below.
              }
            }
            if (typeHintNodeType == null) {
              // TODO: maybe Levenshtein distance over node types.
              error(typeHintNode, "Bad node type hint");
              return ProcessingStatus.BREAK;
            }
            typeHint = Optional.of(typeHintNodeType);
          }

          ImmutableList<ExpressionNode> nodeExprs;
          TemplateLoopNode loop = null;
          TemplateConditionNode cond = null;
          {
            ImmutableList.Builder<ExpressionNode> b = ImmutableList.builder();
            for (int i = 0, n = comprehension.getNChildren(); i < n; ++i) {
              BaseNode child = comprehension.getChild(i);
              if (child instanceof ExpressionNode) {
                b.add((ExpressionNode) child);
              } else if (child instanceof TemplateLoopNode) {
                Preconditions.checkState(loop == null);
                loop = (TemplateLoopNode) child;
              } else if (child instanceof TemplateConditionNode) {
                Preconditions.checkState(cond == null);
                cond = (TemplateConditionNode) child;
              } else {
                error(
                    child, "Can not handle " + child.getVariant()
                    + " in " + interp.getNodeType());
                return ProcessingStatus.BREAK;
              }
            }
            nodeExprs = b.build();
          }
          if (nodeExprs.isEmpty()) {
            error(comprehension, "Missing expression to interpolate");
            return ProcessingStatus.BREAK;
          }
          IdentifierNode elementVarNode =
              loop != null ? loop.firstChildWithType(IdentifierNode.class)
              : null;
          ExpressionNode iterableExpr =
              loop != null ? loop.firstChildWithType(ExpressionNode.class)
              : null;
          if ((elementVarNode == null) != (iterableExpr == null)) {
            error(loop, "Incomplete template comprehension");
            return ProcessingStatus.BREAK;
          }
          ExpressionNode condExpr = cond != null
              ? cond.firstChildWithType(ExpressionNode.class)
              : null;
          ImmutableList.Builder<Object> results = ImmutableList.builder();
          boolean hadError = false;
          if (iterableExpr == null) {
            for (ExpressionNode nodeExpr : nodeExprs) {
              Completion<Object> result = interpreter.interpret(
                  nodeExpr, templateScope.locals);
              if (context.completedNormallyWithoutError(result)) {
                results.add(result.value);
              } else {
                error(
                    nodeExpr,
                    "Failed to compute interpolation result: " + result);
                hadError = true;
              }
            }
          } else {
            Locals<Object> loopLocals = new Locals<>(templateScope.locals);
            Completion<Object> iterableResult = interpreter.interpret(
                iterableExpr, loopLocals);
            if (!context.completedNormallyWithoutError(iterableResult)) {
              hadError = true;
            } else {
              StaticType iterableType =
                  context.runtimeType(iterableResult.value);
              Completion<Object> loopResult = interpreter.forEach(
                  loop,
                  null,
                  iterableResult.value,
                  new Function<Object, Completion<Object>>() {

                    @Override
                    @SuppressWarnings("synthetic-access")
                    public Completion<Object> apply(Object element) {
                      boolean include = true;
                      if (condExpr != null) {
                        Completion<Object> condResult = interpreter.interpret(
                            condExpr, loopLocals);
                        if (context.completedNormallyWithoutError(condResult)) {
                          switch (context.toBoolean(condResult.value)) {
                            case FALSE:
                              include = false;
                              break;
                            case OTHER:
                              error(condExpr,
                                    "Non-boolean result: " + condResult.value);
                              return Completion.normal(context.errorValue());
                            case TRUE:
                              break;
                          }
                        } else {
                          return condResult;
                        }
                      }
                      if (include) {
                        results.add(element);
                      }
                      return Completion.normal(context.nullValue());
                    }

                  },
                  iterableType);
              if (!context.completedNormallyWithoutError(loopResult)) {
                hadError = true;
              }
            }
          }

          if (!hadError) {
            @SuppressWarnings("synthetic-access")
            ImmutableList<Object> flatResults = flatten(results.build());
            if (DEBUG_INTERP) {
              System.err.println("\tflatResults=" + flatResults);
            }
            parentToInterpolations.put(
                pathFromRoot.x.parent,
                new Interpolation(
                    pathFromRoot.x.indexInParent,
                    typeHint,
                    flatResults));
          } else {
            if (DEBUG_INTERP) {
              System.err.println("\tHAS ERROR");
            }
          }
          return ProcessingStatus.BREAK;
        }
        case TemplateLocal: {
          TemplateLocalNode local = (TemplateLocalNode) node;
          // TODO
          break;
        }
        case TemplateDirectives: {
          // When a TemplateDirective has output we need to extract its output
          // out of the directives node.
          TemplateDirectivesNode ds = (TemplateDirectivesNode) node;
          ImmutableList.Builder<BaseNode> b = ImmutableList.builder();
          for (int i = 0; i < ds.getNChildren();) {
            BaseNode child = ds.getChild(i);
            if (child instanceof TemplateDirectiveNode) {
              ++i;
            } else {
              ds.remove(i);
              b.add(child);
            }
          }
          if (ds.getNChildren() != 0) {
            b.add(ds);
          }
          return ProcessingStatus.replace(b.build());
        }
        default:
      }
      return ProcessingStatus.CONTINUE;
    }

  }


  private static ImmutableList<Object> flatten(ImmutableList<Object> ls) {
    for (int i = 0, n = ls.size(); i < n; ++i) {
      Object el = ls.get(i);
      if (el instanceof Iterable<?>) {
        ImmutableList.Builder<Object> b = ImmutableList.builder();
        b.addAll(ls.subList(0, i));
        do {
          el = ls.get(i);
          if (el instanceof Iterable<?>) {
            b.addAll((Iterable<?>) el);
          } else {
            b.add(el);
          }
        } while (++i < n);
        return b.build();
      }
    }
    return ls;
  }

  private static TemplatePseudoRootNode getBodyOfDirective(
      SList<AbstractRewritingPass.Parent> pathFromRootToStart) {
    ImmutableList.Builder<BaseNode> body = ImmutableList.builder();

    // Each TemplateDirective occurs inside a TemplateDirective.

    int nStarts = 1;

    // First, consider later Directive nodes in the same Directives node
    {
      SList<AbstractRewritingPass.Parent> siblings = pathFromRootToStart;
      TemplateDirectivesNode ds = (TemplateDirectivesNode) siblings.x.parent;
      int start = siblings.x.indexInParent + 1;
      int n = ds.getNChildren();
      int end = n;
      for (int i = start; i < n; ++i) {
        TemplateDirectiveNode d = (TemplateDirectiveNode) ds.getChild(i);
        TemplateDirectiveNode.Variant v = d.getVariant();
        if (v.isTemplateEnd()) {
          --nStarts;
          if (nStarts == 0) {
            end = i;
            break;
          }
        }
        if (v.isTemplateStart()) { ++nStarts; }
      }
      if (start < end) {
        TemplateDirectivesNode clone = ds.shallowClone();
        clone.replaceChildren(ds.getChildren().subList(start, end));
        body.add(clone);
      }
      if (end < n) {
        return TemplatePseudoRootNode.Variant.CompilationUnit.buildNode(
            body.build());
      }
    }

    // Next, add whole siblings of the containing TemplateDirectivesNode.
    SList<AbstractRewritingPass.Parent> grandparent = pathFromRootToStart.prev;
    BaseNode container = grandparent.x.parent;
    for (int i = grandparent.x.indexInParent + 1, n = container.getNChildren();
        i < n; ++i) {
      BaseNode directivesSibling = container.getChild(i);
      if (directivesSibling instanceof TemplateDirectivesNode) {
        // Scan the children.  If the template block ends inside the directives
        // then add any directive nodes prior to the end.
        TemplateDirectivesNode ds = (TemplateDirectivesNode) directivesSibling;
        for (int j = 0, m = ds.getNChildren(); j < m; ++j) {
          TemplateDirectiveNode d = (TemplateDirectiveNode) ds.getChild(j);
          TemplateDirectiveNode.Variant v = d.getVariant();
          if (v.isTemplateEnd()) {
            --nStarts;
            if (nStarts == 0) {
              if (j != 0) {
                TemplateDirectivesNode clone = ds.shallowClone();
                clone.replaceChildren(ds.getChildren().subList(0, j));
                body.add(clone);
              }
              return TemplatePseudoRootNode.Variant.CompilationUnit.buildNode(
                  body.build());
            }
          }
          if (v.isTemplateStart()) { ++nStarts; }
        }
      }
      body.add(directivesSibling);
    }
    throw new AssertionError(
        "Unclosed directive nStart=" + nStarts + " in " + body.build());
  }
}
