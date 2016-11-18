package com.mikesamuel.cil.format;

import java.util.List;
import java.util.Map;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

/**
 * A gross structure handler that is suitable for C-like languages whose
 * gross structure is based on bracketed blocks like <code>{...}</code>
 * and <code>(...)</code>.
 */
public class CStyleGrossStructurer<C> implements Layout<C> {

  final TokenBreaker<C> tokenBreaker;

  /** */
  public CStyleGrossStructurer(TokenBreaker<C> tokenBreaker) {
    this.tokenBreaker = tokenBreaker;
  }

  private static final boolean DEBUG = false;

  @Override
  public GrossStructure layout(
      Iterable<? extends Formatter.DecoratedToken<C>> tokens,
      int softColumnLimit) {
    return layout(ImmutableList.copyOf(tokens), softColumnLimit);
  }

  GrossStructure layout(
      ImmutableList<Formatter.DecoratedToken<C>> tokens,
      int softColumnLimit) {
    List<Layout.AbstractGrossStructure> structure = Lists.newArrayList();
    Formatter.DecoratedToken<C> last = null;
    Layout.Break lastBreak = null;
    for (Formatter.DecoratedToken<C> token : tokens) {
      if (last != null) {
        TokenBreak space = tokenBreaker.breakBetween(
            last.content, last.context, token.content, token.context);
        TokenBreak newline = tokenBreaker.lineBetween(
            last.content, last.context, token.content, token.context);
        Layout.Break breakBeforeToken = new Layout.Break(space, newline);
        if (DEBUG) {
          System.err.println("last=" + last + ", token=" + token +
              ", space=" + space + ", newline=" + newline + ", brk="
              + breakBeforeToken);
        }
        structure.add(breakBeforeToken);
        if (lastBreak != null) {
          lastBreak.nextBreak = breakBeforeToken;
        }
        lastBreak = breakBeforeToken;
      }
      Layout.OneToken oneToken = new Layout.OneToken(token.content);
      structure.add(oneToken);
      if (lastBreak != null) {
        lastBreak.nextToken = oneToken;
      }
      last = token;
    }

    Layout.BlockGrossStructure root = Layout.nest(structure);
    if (DEBUG) {
      System.err.println("structure=" + structure);
      System.err.println("root     =" + root);
    }
    Layout.optimize(root, softColumnLimit);
    return root;
  }

  enum Orientation {
    ONE_LINE,
    MULTILINE,
  }

  /**
   * State used to try different layout strategies to see which minimizes
   * the overall line count.
   */
  private static final class Layout {
    static BlockGrossStructure nest(List<AbstractGrossStructure> gs) {
      ImmutableList.Builder<AbstractGrossStructure> children =
          ImmutableList.builder();
      for (int end = 0; end < gs.size();) {
        end = nest(gs, end, gs.size(), children);
      }
      return new BlockGrossStructure(0, children.build());
    }

    private static int nest(
        List<AbstractGrossStructure> gs, int left, int right,
        ImmutableList.Builder<AbstractGrossStructure> children) {
      for (int i = left; i < right; ++i) {
        AbstractGrossStructure g = gs.get(i);
        if (g instanceof OneToken) {
          OneToken t = (OneToken) g;
          if (t.content.length() == 1) {
            char ch0 = t.content.charAt(0);
            switch (ch0) {
              case '(': case '[': case '{':
                int indent = ch0 == '(' ? 4 : 2;
                ImmutableList.Builder<AbstractGrossStructure> nested =
                    ImmutableList.builder();
                int end = nest(gs, i + 1, right, nested);
                children.add(t);  // Open bracket.
                children.add(new BlockGrossStructure(indent, nested.build()));
                if (end < right) {
                  children.add(gs.get(end));  // Close bracket.
                }
                i = end;
                continue;
              case '}': case ']': case ')':
                // Return control to parent to handle siblings.
                return i;
            }
          }
        }
        children.add(g);
      }
      return right;
    }

    /**
     * Pick the best ONE_LINE or MULTILINE orientations given the blocks
     * reachable from root.
     */
    static void optimize(BlockGrossStructure root, int softColumnLimit) {
      List<BlockGrossStructure> blocks = Lists.newArrayList();
      root.addAllBlocks(blocks);
      for (int i = 0, n = blocks.size(); i < n; ++i) {
        BlockGrossStructure block = blocks.get(i);
        block.setOrientation(Orientation.ONE_LINE);
      }

      Map<GrossStructure, Orientation> bestOrientations =
          Maps.newIdentityHashMap();
      int bestLineCount = Integer.MAX_VALUE;

      opt_loop:
      while (true) {
        PositioningTokenSink pts = new PositioningTokenSink();
        boolean pass;
        try {
          root.appendTokens(pts, softColumnLimit);
          pass = true;
        } catch (@SuppressWarnings("unused") OneLineFailure f) {
          pass = false;
        }

        if (DEBUG) {
          System.err.println(
              "Orientations: "
              + Iterables.transform(
                  blocks, new Function<BlockGrossStructure, Orientation>() {
                    @Override
                    public Orientation apply(BlockGrossStructure b) {
                      return b.getOrientation();
                    }
                  }));
          System.err.println("\tpass=" + pass);
        }

        if (pass) {
          int lineCount = pts.lineNumber();
          if (DEBUG) {
            System.err.println(
                "\tlineCount=" + lineCount
                + ", bestLineCount=" + bestLineCount);
          }
          if (lineCount <= bestLineCount) {
            for (int i = 0, n = blocks.size(); i < n; ++i) {
              BlockGrossStructure b = blocks.get(i);
              bestOrientations.put(b, b.getOrientation());
            }
            bestLineCount = lineCount;
          }
        }


        // This is doing an increment where the orientations are
        // a sequence of binary digits.
        for (int i = blocks.size(); --i >= 0;) {
          if (blocks.get(i).flipOrientation() == Orientation.ONE_LINE) {
            // We flipped a zero to a 1 in our left-ward walk, so we're done
            // propagating the carry.
            continue opt_loop;
          }
        }
        // If control reaches here, then we overflowed so we're done.
        break;
      }

      for (BlockGrossStructure block : blocks) {
        block.discardCachedOptimizationState();
        block.setOrientation(bestOrientations.get(block));
      }
    }


    static abstract class AbstractGrossStructure implements GrossStructure {
      BlockGrossStructure parent;

      abstract Orientation getOrientation();
    }

    static final class Break extends AbstractGrossStructure {
      final TokenBreak space;
      final TokenBreak line;
      // We store enough state so that we can lookahead and break if the
      // next two tokens would exceed the limit but there SHOULD NOT be a
      // between the two following tokens.
      OneToken nextToken;
      Break nextBreak;

      Break(TokenBreak space, TokenBreak line) {
        this.space = space;
        this.line = line;
      }

      @Override
      Orientation getOrientation() {
        return parent.getOrientation();
      }

      @Override
      public void appendTokens(TokenSink sink, int softColumnLimit) {
        Orientation orientation = getOrientation();
        if (orientation != Orientation.ONE_LINE) {
          switch (line) {
            case MAY:
              if (sink.atStartOfLine()) {
                // We don't skip to the next line before writing at least one
                // line.
                break;
              }
              int columnAfterLookeahead = sink.column();
              if (nextToken != null) {
                switch (space) {
                  case MUST:
                  case SHOULD:
                    ++columnAfterLookeahead;
                    break;
                  case MAY:
                  case SHOULD_NOT:
                    break;
                }
                // TODO: Here and below, we assume that the token contains no
                // embedded newlines.  If it does, we should take that into
                // account.
                // TODO: Add a hint to decorated token so that we know whether
                // subsequent lines of a multiline token need to be indented,
                // like Javadoc comment tokens.
                columnAfterLookeahead += nextToken.content.length();
                if (nextBreak != null && nextBreak.nextToken != null) {
                  // See if we want to treat this as a preferred break point
                  // based on one break lookahead so that we break like
                  //   a, b, c,
                  //   d, e, f,
                  // instead of
                  //   a, b, c, d
                  //   , e, f,
                  switch (nextBreak.line) {
                    case MAY:
                    case MUST:
                    case SHOULD:
                      break;
                    case SHOULD_NOT:
                      switch (nextBreak.space) {
                        case MUST:
                        case SHOULD:
                          ++columnAfterLookeahead;
                          break;
                        case MAY:
                        case SHOULD_NOT:
                          break;
                      }
                      break;
                  }
                  columnAfterLookeahead += nextBreak.nextToken.content.length();
                }
              }

              if (columnAfterLookeahead > softColumnLimit) {
                sink.newline();
                return;
              }
              break;
            case MUST:
            case SHOULD:
              sink.newline();
              return;
            case SHOULD_NOT:
              break;
          }
        }
        switch (space) {
          case MAY:
          case SHOULD_NOT:
            return;
          case MUST:
          case SHOULD:
            sink.space();
            return;
        }
        throw new AssertionError(space);
      }

      @Override
      public String toString() {
        StringBuilder sb = new StringBuilder("(brk");
        appendTb("nl", line, sb);
        appendTb("spc", space, sb);
        return sb.append(')').toString();
      }

      private static void appendTb(
          String desc, TokenBreak tb, StringBuilder out) {
        String mod = null;
        switch (tb) {
          case MAY:
            mod = "?";
            break;
          case MUST:
            mod = "+";
            break;
          case SHOULD:
            mod = "";
            break;
          case SHOULD_NOT:
            return;
        }
        Preconditions.checkNotNull(mod);
        out.append(' ').append(desc).append(mod);
      }
    }

    static final class OneToken extends AbstractGrossStructure {
      final String content;

      OneToken(String content) {
        this.content = content;
      }

      @Override
      public void appendTokens(TokenSink sink, int softColumnLimit) {
        sink.prepareForToken();
        if (sink.column() + content.length() > softColumnLimit
            && getOrientation() == Orientation.ONE_LINE) {
          @SuppressWarnings("synthetic-access")
          RuntimeException nonLocalTransfer = ONE_LINE_FAILURE;
          throw nonLocalTransfer;
        }
        sink.append(content);
      }

      @Override
      Orientation getOrientation() {
        return parent.getOrientation();
      }

      @Override
      public String toString() {
        return "`" + content + "`";
      }
    }

    static final class BlockGrossStructure extends AbstractGrossStructure {
      /**
       * During optimization, the position after appending tokens.
       * Else null.
       */
      private PositioningTokenSink positionAfter;
      /**
       * An extra bit of state to make sure positionAfter != null means
       * that it is a valid cache of the operation being performed.
       */
      private int cacheSoftColumnLimit;
      /**
       * Set speculatively during optimization to find the combination that
       * minimizes the line count.
       */
      private Orientation orientation = Orientation.ONE_LINE;

      final ImmutableList<AbstractGrossStructure> children;
      private final int indent;

      BlockGrossStructure(
          int indent,
          ImmutableList<AbstractGrossStructure> children) {
        this.indent = indent;
        this.children = children;
        for (AbstractGrossStructure child : children) {
          Preconditions.checkState(child.parent == null);
          child.parent = this;
        }
      }

      @Override
      public void appendTokens(TokenSink sink, int softColumnLimit) {
        // We might skip some children if we're doing optimization and have
        // some cached state.
        int startIndex = 0;

        // Take into account cached optimization state where possible.
        if (sink instanceof PositioningTokenSink) {
          if (positionAfter != null
              && cacheSoftColumnLimit == softColumnLimit) {
            ((PositioningTokenSink) sink).resetTo(positionAfter);
            return;
          }
          // Look from the right for a child with a valid cache and resume
          // from there.
          for (int i = children.size(); --i >= 0;) {
            AbstractGrossStructure child = children.get(i);
            if (child instanceof BlockGrossStructure) {
              BlockGrossStructure blockChild = (BlockGrossStructure) child;
              if (blockChild.positionAfter != null
                  && softColumnLimit == blockChild.cacheSoftColumnLimit) {
                startIndex = i + 1;
                ((PositioningTokenSink) sink).resetTo(blockChild.positionAfter);
                break;
              }
            }
          }
        }

        // Indent as necessary.
        boolean alreadyIndented = startIndex != 0;
        boolean dedentAfter = false;
        if (indent != 0) {
          if (!alreadyIndented) {
            sink.indentBy(indent);
          }
          dedentAfter = true;
        }

        // Apply children.
        for (int i = startIndex, n = children.size(); i < n; ++i) {
          children.get(i).appendTokens(sink, softColumnLimit);
        }

        // Dedent as necessary.
        if (dedentAfter) {
          sink.dedent();
        }

        if (sink instanceof PositioningTokenSink) {
          this.positionAfter = new PositioningTokenSink(
              (PositioningTokenSink) sink);
          this.cacheSoftColumnLimit = softColumnLimit;
        }
      }

      @Override
      Orientation getOrientation() {
        return orientation;
      }

      void setOrientation(Orientation newOrientation) {
        Preconditions.checkNotNull(newOrientation);
        if (orientation != newOrientation) {
          this.orientation = newOrientation;
          if (this.positionAfter != null) {
            for (BlockGrossStructure b = this; b != null; b = b.parent) {
              b.positionAfter = null;
            }
          }
        }
      }

      Orientation flipOrientation() {
        Orientation old = orientation;
        setOrientation(
            orientation == Orientation.MULTILINE
            ? Orientation.ONE_LINE : Orientation.MULTILINE);
        return old;
      }

      void discardCachedOptimizationState() {
        this.positionAfter = null;
      }

      void addAllBlocks(List<? super BlockGrossStructure> blocks) {
        blocks.add(this);
        for (AbstractGrossStructure child : children) {
          if (child instanceof BlockGrossStructure) {
            ((BlockGrossStructure) child).addAllBlocks(blocks);
          }
        }
      }

      @Override
      public String toString() {
        return "(Block " + children + ")";
      }
    }

    /**
     * A token sink that does not actually accumulate characters, but is instead
     * used speculatively to pick the best assignment of ONE_LINE or MULTILINE
     * orientations to blocks.
     */
    private static final class PositioningTokenSink extends AbstractTokenSink {
      private int charInFile;

      PositioningTokenSink(PositioningTokenSink original) {
        super(original);
      }

      PositioningTokenSink() {
        super();
      }

      @Override
      protected void resetTo(AbstractTokenSink original) {
        super.resetTo(original);
        this.charInFile = original.charInFile();
      }

      @Override
      public int charInFile() {
        return charInFile;
      }

      @Override
      protected void appendTokenContent(String content) {
        charInFile += content.length();
      }

      @Override
      protected void appendSpaceChars(char ch, int count) {
        charInFile += count;
      }
    }
  }

  @SuppressWarnings("synthetic-access")
  private static final RuntimeException ONE_LINE_FAILURE = new OneLineFailure();
  private static final class OneLineFailure extends RuntimeException {
    private static final long serialVersionUID = 1L;

    {
      // Only used for transfer of control.
      this.setStackTrace(new StackTraceElement[0]);
    }
  }
}
