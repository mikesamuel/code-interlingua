package com.mikesamuel.cil.template;

import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
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
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.mikesamuel.cil.ast.BaseNode;
import com.mikesamuel.cil.ast.j8.CompilationUnitNode;
import com.mikesamuel.cil.ast.j8.ExpressionNode;
import com.mikesamuel.cil.ast.j8.IdentifierNode;
import com.mikesamuel.cil.ast.j8.J8BaseInnerNode;
import com.mikesamuel.cil.ast.j8.J8BaseNode;
import com.mikesamuel.cil.ast.j8.J8NodeType;
import com.mikesamuel.cil.ast.j8.J8NodeVariant;
import com.mikesamuel.cil.ast.j8.NodeTypeHintNode;
import com.mikesamuel.cil.ast.j8.SingleStaticImportDeclarationNode;
import com.mikesamuel.cil.ast.j8.TemplateBodyNode;
import com.mikesamuel.cil.ast.j8.TemplateComprehensionNode;
import com.mikesamuel.cil.ast.j8.TemplateConditionNode;
import com.mikesamuel.cil.ast.j8.TemplateDirectiveNode;
import com.mikesamuel.cil.ast.j8.TemplateDirectivesNode;
import com.mikesamuel.cil.ast.j8.TemplateFormalsNode;
import com.mikesamuel.cil.ast.j8.TemplateHeaderNode;
import com.mikesamuel.cil.ast.j8.TemplateInterpolationNode;
import com.mikesamuel.cil.ast.j8.TemplateLocalNode;
import com.mikesamuel.cil.ast.j8.TemplateLoopNode;
import com.mikesamuel.cil.ast.j8.TemplatePseudoRootNode;
import com.mikesamuel.cil.ast.j8.VariableDeclaratorIdNode;
import com.mikesamuel.cil.ast.j8.VariableDeclaratorNode;
import com.mikesamuel.cil.ast.j8.VariableInitializerNode;
import com.mikesamuel.cil.ast.meta.Name;
import com.mikesamuel.cil.ast.meta.StaticType;
import com.mikesamuel.cil.ast.meta.StaticType.TypePool;
import com.mikesamuel.cil.ast.passes.AbstractRewritingPass;
import com.mikesamuel.cil.expr.Completion;
import com.mikesamuel.cil.expr.InterpretationContext;
import com.mikesamuel.cil.expr.InterpretationContextImpl;
import com.mikesamuel.cil.expr.Interpreter;
import com.mikesamuel.cil.expr.Locals;
import com.mikesamuel.cil.parser.ForceFitState;
import com.mikesamuel.cil.parser.ParSer;
import com.mikesamuel.cil.parser.SList;
import com.mikesamuel.cil.ptree.PTree;
import com.mikesamuel.cil.util.LogUtils;

/**
 * A pass that walks the parse tree to interpret template instructions and
 * produce a list of pure Java compilation units.
 */
final class TemplateProcessingPass
extends AbstractRewritingPass {

  /**
   * An interpretation context that binds names to each property in the data
   * bundle, allows e
   */
  final class TemplateBundleInterpretationContext
  extends InterpretationContextImpl {

    public TemplateBundleInterpretationContext(
        Logger logger, ClassLoader loader, TypePool typePool) {
      super(logger, loader, typePool);
    }

    @Override
    public Object getFieldDynamic(String key, @Nullable Object container) {
      if (container instanceof DataBundle) {
        return ((DataBundle) container).getOrDefault(key, null);
      }
      return super.getFieldDynamic(key, container);
    }

    @Override @SuppressWarnings("synthetic-access")
    public Object invokeDynamic(
        String methodName, Object receiver, List<? extends Object> actuals) {
      if (receiver instanceof DataBundle && methodName.startsWith("is")
          && methodName.length() > 2 && actuals.isEmpty()) {
        // Alias x.isFoo() to Boolean.TRUE.equals(x.foo)
        String fieldName = Character.toLowerCase(methodName.charAt(2))
            + methodName.substring(3);
        return Boolean.TRUE.equals(
            ((DataBundle) receiver).getOrDefault(fieldName, Boolean.FALSE));
      }
      // Search for template functions.
      if (receiver == null) {  // TODO: Only do this for bare references.
        TemplateInfo fnInfo = null;
        for (int i = templateScopes.size(); --i >= 0;) {
          TemplateScope scope = templateScopes.get(i);
          fnInfo = scope.templateInfo.get(methodName);
          if (fnInfo != null) {
            break;
          }
        }
        if (fnInfo != null) {
          ImmutableList<IdentifierNode> formalNameNodes =
              fnInfo.formals.finder(IdentifierNode.class)
              .allowNonStandard(true).find();
          int nFormals = formalNameNodes.size();
          if (nFormals == actuals.size()) {
            Locals<Object> callLocals = new Locals<>(
                templateScopes.getLast().locals);
            for (int i = 0; i < nFormals; ++i) {
              IdentifierNode formalNameNode = formalNameNodes.get(i);
              Name formalName = Name.root(
                  formalNameNode.getValue(), Name.Type.LOCAL);
              if (callLocals.hasOwn(formalName)) {
                error(formalNameNode,
                      "Duplicate formal name " + formalName.identifier);
              } else {
                callLocals.declare(formalName, Functions.identity());
                callLocals.set(formalName, actuals.get(i));
              }
            }

            TemplateBodyNode bodyClone = fnInfo.body.deepClone();

            templateScopes.add(new TemplateScope(callLocals));
            visitChildren(bodyClone, null);
            templateScopes.removeLast();

            Collection<Interpolation> interps =
                parentToInterpolations.get(bodyClone);

            Object result = null;
            boolean computedResult = false;
            if (!interps.isEmpty()) {
              if (interps.size() == bodyClone.getNChildren()) {
                // Pass through unchanged if there is no non-interpolated
                // content.  This is very convenient for recursive template
                // invocations.
                ImmutableList.Builder<Object> b = ImmutableList.builder();
                for (Interpolation interp : interps) {
                  b.addAll(interp.results);
                }
                parentToInterpolations.removeAll(bodyClone);
                result = b.build();
                computedResult = true;
              } else if (finishInterpolation(bodyClone)) {
                return context.errorValue();
              }
            }

            // Strip off (TemplateBody ...) envelope.
            if (!computedResult) {
              result = bodyClone.getChildren();
              computedResult = true;
            }
            Preconditions.checkState(computedResult);

            Optional<J8NodeType> nodeTypeHint = Optional.absent();
            if (fnInfo.nodeTypeHint != null) {
              Optional<IdentifierNode> nodeTypeHintIdent = fnInfo.nodeTypeHint
                  .finder(IdentifierNode.class).allowNonStandard(true)
                  .findOne();
              if (nodeTypeHintIdent.isPresent()) {
                try {
                  nodeTypeHint = Optional.of(J8NodeType.valueOf(
                      nodeTypeHintIdent.get().getValue()));
                } catch (@SuppressWarnings("unused")
                         IllegalArgumentException ex) {
                  error(
                      fnInfo.nodeTypeHint,
                      "Bad node type hint "
                      + nodeTypeHintIdent.get().getValue());
                  return context.errorValue();
                }
              } else {
                error(fnInfo.nodeTypeHint, "Malformed node type hint");
                return context.errorValue();
              }
            }
            return coerceEagerly(result, nodeTypeHint);
          }
        }
      }
      Function<List<? extends Object>, Object> builtin =
          Builtins.getBuiltin(methodName);
      if (builtin != null) {
        return builtin.apply(actuals);
      }
      return super.invokeDynamic(methodName, receiver, actuals);
    }

  }

  final Interpreter<Object> interpreter;
  final InterpretationContext<Object> context;
  final ImmutableList.Builder<CompilationUnitNode> out;
  private final LinkedList<TemplateScope> templateScopes =
      Lists.newLinkedList();


  public TemplateProcessingPass(
      Logger logger, TypePool typePool, ClassLoader loader, DataBundle input,
      ImmutableList.Builder<CompilationUnitNode> out) {
    super(logger);

    this.context = new TemplateBundleInterpretationContext(
        logger, loader, typePool);
    context.setThisValue(null, input);
    this.interpreter = new Interpreter<>(context);
    Locals<Object> fileLocals = new Locals<>();

    this.out = out;
    this.templateScopes.add(new TemplateScope(fileLocals));
  }


  static final class TemplateScope {
    final Locals<Object> locals;
    final Map<String, TemplateInfo> templateInfo = Maps.newLinkedHashMap();
    boolean elide;

    TemplateScope(Locals<Object> locals) {
      this.locals = locals;
    }
  }

  private final Multimap<J8BaseNode, Interpolation> parentToInterpolations
    = Multimaps.newMultimap(
        new IdentityHashMap<J8BaseNode, Collection<Interpolation>>(),
        new Supplier<List<Interpolation>>() {

          @Override
          public List<Interpolation> get() {
            return new ArrayList<>();
          }

        });


  static final class Interpolation {
    final int indexInParent;
    final ImmutableList<Object> results;

    Interpolation(
        int indexInParent,
        ImmutableList<Object> results) {
      this.indexInParent = indexInParent;
      this.results = results;
    }
  }

  @Override
  protected ProcessingStatus previsit(
      J8BaseNode node,
      @Nullable SList<AbstractRewritingPass.Parent> pathFromRoot) {
    if (node.getVariant().isTemplateEnd()) {
      this.templateScopes.remove(templateScopes.size() - 1);
    }
    TemplateScope templateScope = templateScopes.getLast();
    // If we have elided something, e.g. due to %%if(false), we still need to
    // descend into TemplateDirectives so we can find the end of scope above.
    if (node.getNodeType() != J8NodeType.TemplateDirectives
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
        if (d != null && TemplateBundle.isOptInDeclaration(d)) {
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
            for (J8BaseNode child : d.getChildren()) {
              if (child instanceof TemplateLocalNode) {
                TemplateLocalNode local = (TemplateLocalNode) child;
                Optional<IdentifierNode> ident = local
                    .finder(IdentifierNode.class)
                    .exclude(J8NodeType.VariableInitializer)
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
          case Function:
          case LoopStart:
            return ProcessingStatus.CONTINUE;
        }
        throw new AssertionError(d);
      }

      case TemplateBody:
        // Do not descend into a body until it is called
        return ProcessingStatus.BREAK;

      default:
        break;
    }
    return ProcessingStatus.CONTINUE;
  }

  /**
   * Look at the result of interpolations of children and try to fit them
   * around the existing node's structure.
   *
   * @return true to abort further processing due to an error.
   */
  private boolean finishInterpolation(J8BaseNode node) {
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
          parts.add(ForceFitState.FitPart.interpolatedValue(value));
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
      if (TemplateBundle.DEBUG_INTERP) {
        System.err.println("Interpolate into " + node.getVariant());
        System.err.println("\tfitter=" + fitter);
        System.err.println("\tstate parts=" + state.parts);
        System.err.println("\tafter=" + after.fits);
      }
      if (after.fits.isEmpty()) {
        error(node, node.getVariant() + " does not fit " + state.parts);
        return true;
      } else {
        ForceFitState.PartialFit bestFit = Iterables.getFirst(
            after.fits, null);
        Iterator<BaseNode<?, ?, ?>> insertions =
            SList.forwardIterable(bestFit.resolutions).iterator();

        ImmutableList.Builder<J8BaseNode> fixed = ImmutableList.builder();
        for (ForceFitState.FitPart part : after.parts) {
          if (part instanceof ForceFitState.FixedNode) {
            fixed.add((J8BaseNode) ((ForceFitState.FixedNode) part).child);
          } else {
            fixed.add((J8BaseNode) insertions.next());
          }
        }
        Preconditions.checkState(!insertions.hasNext());
        ((J8BaseInnerNode) node).replaceChildren(fixed.build());
      }
    }
    return false;
  }

  @Override
  protected ProcessingStatus postvisit(
      J8BaseNode node,
      @Nullable SList<AbstractRewritingPass.Parent> pathFromRoot) {
    // After we've processed all the children, we should have all the
    // interpolation results, and so are ready to do replacements.
    if (finishInterpolation(node)) {
      return ProcessingStatus.BREAK;
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
          case Function: {
            // Store in scope.
            TemplateInfo templateInfo = TemplateInfo.from(logger, directive);
            if (templateInfo != null) {
              templateScope.templateInfo.put(templateInfo.name, templateInfo);
            }
            return ProcessingStatus.REMOVE;
          }
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
            TemplatePseudoRootNode bodyOfDirective = getBodyOfDirective(
                pathFromRoot);
            ImmutableList.Builder<J8BaseNode> replacements =
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
        }
        throw new AssertionError(v);
      }
      case TemplateInterpolation: {
        if (TemplateBundle.DEBUG_INTERP) {
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

        Optional<J8NodeType> typeHint;
        if (typeHintNode != null) {
          IdentifierNode hintIdent = typeHintNode.firstChildWithType(
              IdentifierNode.class);
          J8NodeType typeHintNodeType = null;
          if (hintIdent != null) {
            try {
              typeHintNodeType = J8NodeType.valueOf(hintIdent.getValue());
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
        } else {
          typeHint = Optional.absent();
        }

        ImmutableList<ExpressionNode> nodeExprs;
        TemplateLoopNode loop = null;
        TemplateConditionNode cond = null;
        {
          ImmutableList.Builder<ExpressionNode> b = ImmutableList.builder();
          for (int i = 0, n = comprehension.getNChildren(); i < n; ++i) {
            J8BaseNode child = comprehension.getChild(i);
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
            if (!context.completedNormallyWithoutError(result)) {
              error(
                  nodeExpr,
                  "Failed to compute interpolation result: " + result);
              hadError = true;
            } else {
              Object value = coerceEagerly(result.value, typeHint);
              if (value == null) {
                error(nodeExpr, "Cannot interpolate null");
                hadError = true;
              } else {
                results.add(value);
              }
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
                      results.add(coerceEagerly(element, typeHint));
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
          ImmutableList<Object> flatResults = flatten(results.build());
          if (TemplateBundle.DEBUG_INTERP) {
            System.err.println("\tflatResults=" + flatResults);
          }
          parentToInterpolations.put(
              pathFromRoot.x.parent,
              new Interpolation(
                  pathFromRoot.x.indexInParent,
                  flatResults));
        } else {
          if (TemplateBundle.DEBUG_INTERP) {
            System.err.println("\tHAS ERROR");
          }
        }
        // This will be removed when post-processing reaches the container.
        return ProcessingStatus.BREAK;
      }
      case TemplateLocal: {
        TemplateLocalNode local = (TemplateLocalNode) node;
        VariableDeclaratorNode decl = local.firstChildWithType(
            VariableDeclaratorNode.class);
        if (decl != null) {
          VariableDeclaratorIdNode declId = decl.firstChildWithType(
              VariableDeclaratorIdNode.class);
          VariableInitializerNode initializer = decl.firstChildWithType(
              VariableInitializerNode.class);
          if (declId != null) {
            IdentifierNode ident = declId.firstChildWithType(
                IdentifierNode.class);
            if (ident != null) {
              Name name = Name.root(ident.getValue(), Name.Type.LOCAL);
              if (!templateScope.locals.hasOwn(name)) {
                // Redeclare in case it was not declared in the previsit which
                // may occur when the local name is itself the result of an
                // interpolation.
                templateScope.locals.declare(name, Functions.identity());
              }
              if (initializer != null) {
                Completion<Object> result = interpreter.interpret(
                    initializer, templateScope.locals);
                if (context.completedNormallyWithoutError(result)) {
                  templateScope.locals.set(name, result.value);
                } else {
                  break;
                }
              }
              return ProcessingStatus.REMOVE;
            }
          }
        }
        error(local, "Malformed local");
        break;
      }
      case TemplateDirectives: {
        // When a TemplateDirective has output we need to extract its output
        // out of the directives node.
        TemplateDirectivesNode ds = (TemplateDirectivesNode) node;
        ImmutableList.Builder<J8BaseNode> b = ImmutableList.builder();
        for (int i = 0; i < ds.getNChildren();) {
          J8BaseNode child = ds.getChild(i);
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


  static ImmutableList<Object> flatten(ImmutableList<Object> ls) {
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

  static Object coerceEagerly(
      Object exprResult, Optional<J8NodeType> nodeTypeHint) {
    if (!nodeTypeHint.isPresent()) { return exprResult; }
    J8NodeType nt = nodeTypeHint.get();
    Object coerced;
    if (exprResult instanceof Iterable<?>) {
      Iterable<?> uncoerced = (Iterable<?>) exprResult;
      ImmutableList.Builder<Object> b = ImmutableList.builder();
      coerceAllEagerly(uncoerced, nt, b);
      ImmutableList<Object> allCoerced = b.build();
      coerced = allCoerced;
      // If not all coerced successfully, then try to force fit to a node of the
      // given type.
      for (Object o : allCoerced) {
        if (!(o instanceof J8BaseNode)
            || ((J8BaseNode) o).getNodeType() != nt) {
          // TODO: Limit this to node types that have a single non-intermediate
          // variant to avoid ambiguity, and look through intermediates.
          // TODO: Move this into Intermediates and memoize
          for (Enum<? extends J8NodeVariant> variantEnum :
                   nt.getVariantType().getEnumConstants()) {
            J8NodeVariant variant = (J8NodeVariant) variantEnum;
            ParSer fitter = PTree.complete(variant).getParSer();
            ForceFitState unfit = new ForceFitState(Iterables.transform(
                uncoerced,
                new Function<Object, ForceFitState.FitPart>() {

                  @Override
                  public ForceFitState.FitPart apply(Object x) {
                    return ForceFitState.FitPart.interpolatedValue(x);
                  }

                }));
            ForceFitState fit = fitter.forceFit(unfit);
            if (!fit.fits.isEmpty()) {
              ImmutableList.Builder<J8BaseNode> children =
                  ImmutableList.builder();
              for (BaseNode<?, ?, ?> child : SList.forwardIterable(
                       Iterables.getFirst(fit.fits, null).resolutions)) {
                children.add((J8BaseNode) child);
              }
              coerced = variant.buildNode(children.build());
            }
          }
          break;
        }
      }
    } else {
      coerced = J8NodeType.GRAMMAR.tryToCoerce(exprResult, nt);
    }
    return coerced;
  }

  private static void coerceAllEagerly(
      Iterable<?> ls, J8NodeType nt, ImmutableList.Builder<Object> out) {
    for (Object element : ls) {
      if (element instanceof Iterable) {
        coerceAllEagerly((Iterable<?>) element, nt, out);
      } else {
        Object coerced = J8NodeType.GRAMMAR.tryToCoerce(element, nt);
        if (coerced != null) {
          out.add(coerced);
        }
      }
    }
  }


  static TemplatePseudoRootNode getBodyOfDirective(
      SList<AbstractRewritingPass.Parent> pathFromRootToStart) {
    ImmutableList.Builder<J8BaseNode> body = ImmutableList.builder();

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
    J8BaseNode container = grandparent.x.parent;
    for (int i = grandparent.x.indexInParent + 1, n = container.getNChildren();
        i < n; ++i) {
      J8BaseNode directivesSibling = container.getChild(i);
      if (directivesSibling instanceof TemplateDirectivesNode) {
        // Scan the children looking for the end and adding whole siblings to
        // the body.
        TemplateDirectivesNode ds = (TemplateDirectivesNode) directivesSibling;
        for (int j = 0, m = ds.getNChildren(); j < m; ++j) {
          TemplateDirectiveNode d = (TemplateDirectiveNode) ds.getChild(j);
          TemplateDirectiveNode.Variant v = d.getVariant();
          if (v.isTemplateEnd()) {
            --nStarts;
            if (nStarts == 0) {
              // If the template block ends inside the directives
              // then add any directive nodes prior to the end.
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


final class TemplateInfo {
  final TemplateDirectiveNode decl;
  final String name;
  final @Nullable TemplateFormalsNode formals;
  final @Nullable NodeTypeHintNode nodeTypeHint;
  final TemplateBodyNode body;

  TemplateInfo(
      TemplateDirectiveNode decl,
      String name,
      @Nullable TemplateFormalsNode formals,
      @Nullable NodeTypeHintNode nodeTypeHint,
      TemplateBodyNode body) {
    this.decl = decl;
    this.name = name;
    this.formals = formals;
    this.nodeTypeHint = nodeTypeHint;
    this.body = body;
  }

  static TemplateInfo from(Logger logger, TemplateDirectiveNode d) {
    TemplateHeaderNode hdr = d.firstChildWithType(TemplateHeaderNode.class);
    if (hdr == null) {
      LogUtils.log(
          logger, Level.SEVERE, d, "Missing header for template function",
          null);
      return null;
    }
    IdentifierNode ident = hdr.firstChildWithType(IdentifierNode.class);
    TemplateFormalsNode formals =
        hdr.firstChildWithType(TemplateFormalsNode.class);
    NodeTypeHintNode nodeTypeHint =
        hdr.firstChildWithType(NodeTypeHintNode.class);
    TemplateBodyNode body = d.firstChildWithType(TemplateBodyNode.class);
    if (ident == null) {
      LogUtils.log(
          logger, Level.SEVERE, d, "Missing name for template function",
          null);
      return null;
    } else if (body == null) {
      LogUtils.log(
          logger, Level.SEVERE, d, "Missing body for template function",
          null);
      return null;
    } else if (nodeTypeHint == null) {
      LogUtils.log(
          logger, Level.SEVERE, d, "Missing node type for template declaration",
          null);
      return null;
    } else {
      return new TemplateInfo(d, ident.getValue(), formals, nodeTypeHint, body);
    }
  }
}