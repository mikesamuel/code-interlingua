package com.mikesamuel.cil.ast.j8;

import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.mikesamuel.cil.ast.BaseNode;
import com.mikesamuel.cil.ast.Grammar;
import com.mikesamuel.cil.ast.NodeI;
import com.mikesamuel.cil.ast.NodeType;
import com.mikesamuel.cil.ast.NodeVariant;
import com.mikesamuel.cil.event.Event;
import com.mikesamuel.cil.parser.ForceFitState;
import com.mikesamuel.cil.parser.LeftRecursion;
import com.mikesamuel.cil.parser.MatchErrorReceiver;
import com.mikesamuel.cil.parser.MatchState;
import com.mikesamuel.cil.parser.ParSer;
import com.mikesamuel.cil.parser.ParseErrorReceiver;
import com.mikesamuel.cil.parser.ParseResult;
import com.mikesamuel.cil.parser.ParseState;
import com.mikesamuel.cil.parser.SList;
import com.mikesamuel.cil.parser.SerialErrorReceiver;
import com.mikesamuel.cil.parser.SerialState;

final class GrammarImpl implements Grammar<J8BaseNode, J8NodeType> {

  static final GrammarImpl INSTANCE = new GrammarImpl();

  private GrammarImpl() {
    // Singleton
  }

  @Override
  public J8BaseNode makePseudoRoot(Iterable<? extends J8BaseNode> subRoots) {
    int compilationUnitCount = 0;
    for (J8BaseNode node : subRoots) {
      J8NodeType nt = node.getNodeType();
      if (nt == J8NodeType.CompilationUnit) {
        ++compilationUnitCount;
      } else if (!nt.isNonStandard()) {
        throw new IllegalArgumentException(
            "Expected 1 root node.  "
            + Lists.transform(
                ImmutableList.copyOf(subRoots),
                new Function<NodeI<?, ?, ?>, NodeVariant<?, ?>>() {

                  @Override
                  public NodeVariant<?, ?> apply(NodeI<?, ?, ?> n) {
                    return n.getVariant();
                  }

                })
            + " cannot be coalesced into a template pseudo root");
      }
    }
    Preconditions.checkState(compilationUnitCount <= 1);
    // TODO: Check balancedness of start and end template instructions.

    return TemplatePseudoRootNode.Variant.CompilationUnit.buildNode(subRoots);
  }

  @Override
  public J8BaseNode cast(NodeI<?, ?, ?> node) throws ClassCastException {
    return (J8BaseNode) node;
  }

  @Override
  public J8NodeVariant cast(NodeVariant<?, ?> v) throws ClassCastException {
    return (J8NodeVariant) v;
  }

  @Override
  public J8NodeType cast(NodeType<?, ?> t) throws ClassCastException {
    return (J8NodeType) t;
  }

  @Override
  public Object tryToCoerce(Object value, J8NodeType nodeType) {
    return NodeCoercion.tryToCoerce(value, nodeType);
  }

  @Override
  public Optional<ParSer> parserForNonStandardReplacement(
      NodeType<?, ?> nodeType) {
    if (!(nodeType instanceof J8NodeType)
        // Don't inf. recurse
        || nodeType == J8NodeType.TemplateInterpolation) {
      return Optional.absent();
    }
    return Optional.of(new ParSer() {
      @SuppressWarnings("synthetic-access")
      @Override
      public ParseResult parse(
          ParseState state, LeftRecursion lr, ParseErrorReceiver err) {
        if (!shouldHandleTemplateInterpolation(state)) {
          return ParseResult.failure();
        }
        // Try parsing a template interpolation.
        ParseResult nonStandardResult =
            J8NodeType.TemplateInterpolation.getParSer()
            .parse(state, lr, err);
        switch (nonStandardResult.synopsis) {
          case FAILURE:
            break;
          case SUCCESS:
            ParseState afterInterpolation = nonStandardResult.next();
            Optional<NodeType<?, ?>> nodeTypeHint = lookbackForNodeTypeHint(
                afterInterpolation.output);
            boolean passed =
                nodeTypeHint.isPresent()
                ? nodeTypeHint.get() == nodeType
                : !(nodeType instanceof J8NodeType
                    && J8NodeTypeTables.NOINTERP.contains(
                        (J8NodeType) nodeType));
            if (passed) {
              return ParseResult.success(
                  afterInterpolation,
                  nonStandardResult.writeBack,
                  nonStandardResult.lrExclusionsTriggered);
            }
            break;
        }
        return ParseResult.failure(nonStandardResult.lrExclusionsTriggered);
      }

      @Override
      public Optional<SerialState> unparse(
          SerialState serialState, SerialErrorReceiver err) {
        throw new UnsupportedOperationException();
      }

      @Override
      public Optional<MatchState> match(
          MatchState state, MatchErrorReceiver err) {
        throw new UnsupportedOperationException();
      }

      @Override
      public ForceFitState forceFit(ForceFitState state) {
        throw new UnsupportedOperationException();
      }

      @Override
      public void appendShallowStructure(StringBuilder sb) {
        sb.append("NonStandardReplacement");
      }
    });
  }

  private static boolean shouldHandleTemplateInterpolation(ParseState state) {
    if (state.input.allowNonStandardProductions) {
      CharSequence content = state.input.content();
      int index = state.index;
      // Recognize template part prefixes "(%", "{%"
      if (index + 1 < content.length()
          && '%' == content.charAt(index + 1)) {
        char c0 = content.charAt(index);
        return c0 == '(' || c0 == '{';
      }
    }
    return false;
  }

  private static SList<Event> stepBack(SList<Event> start) {
    SList<Event> ls = start.prev;
    while (ls != null && ls.x.getKind() == Event.Kind.POSITION_MARK) {
      ls = ls.prev;
    }
    return ls;
  }

  static Optional<NodeType<?, ?>> lookbackForNodeTypeHint(SList<Event> output) {
    // Expect a sequence of events at the end like.
    //   (PUSH NodeTypeHint) ":" (PUSH Identifier) "MyNodeType" POP POP
    SList<Event> ls = output;
    // First we skip back over any pushes and tokens.
    while (ls != null) {
      switch (ls.x.getKind()) {
        case POSITION_MARK:
        case PUSH:
        case TOKEN:
          ls = ls.prev;
          continue;
        default:
          break;
      }
      break;
    }
    // Now we should see a run of pops of at least length 2.
    int nPopsSeen = 0;
    while (ls != null) {
      ls = stepBack(ls);
      if (ls != null && ls.x.getKind() == Event.Kind.POP) {
        ++nPopsSeen;
      } else {
        break;
      }
    }
    if (ls == null || nPopsSeen < 2 || ls.x.getKind() != Event.Kind.CONTENT) {
      return Optional.absent();
    }
    String content = ls.x.getContent();
    J8NodeType nodeType;
    try {
      nodeType = J8NodeType.valueOf(content);
    } catch (@SuppressWarnings("unused") IllegalArgumentException ex) {
      return Optional.absent();
    }
    ls = stepBack(ls);
    if (ls == null || ls.x.getKind() != Event.Kind.PUSH
        || ls.x.getNodeType() != J8NodeType.Identifier) {
      return Optional.absent();
    }
    ls = stepBack(ls);
    if (ls == null || ls.x.getKind() != Event.Kind.TOKEN) {
      return Optional.absent();
    }
    ls = stepBack(ls);
    if (ls == null || ls.x.getKind() != Event.Kind.PUSH
        || ls.x.getNodeType() != J8NodeType.NodeTypeHint) {
      return Optional.absent();
    }
    return Optional.of(nodeType);
  }


  @Override
  public Optional<ParSer> parserForNonStandardInterstitial(
      NodeType<?, ?> nodeType) {
    if (nodeType == J8NodeType.TemplateDirective
        || nodeType == J8NodeType.TemplateDirectives) {
      return Optional.absent();
    }
    return Optional.of(new ParSer() {

      @SuppressWarnings("synthetic-access")
      @Override
      public ParseResult parse(
          ParseState state, LeftRecursion lr, ParseErrorReceiver err) {
        if (shouldHandleTemplateDirective(state)) {
          // Handle directives at the front.
          ParseResult result = J8NodeType.TemplateDirectives.getParSer()
              .parse(state, lr, err);
          Preconditions.checkState(
              result.writeBack == ParseResult.NO_WRITE_BACK_RESTRICTION);
          Preconditions.checkState(
              result.lrExclusionsTriggered.isEmpty());
          return result;
        }
        return ParseResult.failure();
      }

      @Override
      public Optional<SerialState> unparse(
          SerialState serialState, SerialErrorReceiver err) {
        throw new UnsupportedOperationException();
      }

      @Override
      public Optional<MatchState> match(
          MatchState state, MatchErrorReceiver err) {
        throw new UnsupportedOperationException();
      }

      @Override
      public ForceFitState forceFit(ForceFitState state) {
        throw new UnsupportedOperationException();
      }

      @Override
      public void appendShallowStructure(StringBuilder sb) {
        sb.append("NonStandardInterstitial");
      }
    });
  }

  private static boolean shouldHandleTemplateDirective(ParseState state) {
    if (state.input.allowNonStandardProductions) {
      CharSequence content = state.input.content();
      int index = state.index;
      // Recognize template part prefix "%%"
      if (index + 1 < content.length()
          && '%' == content.charAt(index)
          && '%' == content.charAt(index + 1)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean isNonStandardReplacement(NodeType<?, ?> nodeType) {
    return nodeType == J8NodeType.TemplateInterpolation;
  }

  @Override
  public boolean isNonStandardInterstitial(NodeType<?, ?> nodeType) {
    return nodeType == J8NodeType.TemplateDirectives;
  }

  @Override
  public Optional<J8BaseNode> wrap(
      BaseNode<?, ?, ?> node, J8NodeType nodeType) {
    if (node instanceof J8BaseNode) {
      return Intermediates.wrap(
          (J8BaseNode) node, nodeType, Functions.constant(null));
    }
    return Optional.absent();
  }
}
