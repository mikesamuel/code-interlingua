package com.mikesamuel.cil.ast;

import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.mikesamuel.cil.parser.SourcePosition;
import com.mikesamuel.cil.parser.Unparse;

/**
 * An event in a series which describes a pre-order traversal of a parse-tree.
 * <p>
 * Some events are "ephemeral" which means they are used for bookkeeping by the
 * parser during a parse and should not be seen by the tree-builder or emitted
 * by a tree flattener.
 */
public abstract class MatchEvent {

  /**
   * An event fired on entering a node before visiting its content or children.
   */
  public static Push push(NodeVariant v) {
    return new Push(v);
  }

  /**
   * An event that happens when we leave a node after visiting its content and
   * children.
   */
  public static Pop pop() {
    return Pop.INSTANCE;
  }

  /**
   * An event that happens when we leave a node after visiting its content and
   * children.
   */
  public static Pop pop(NodeVariant variant) {
    return new Pop(Optional.of(variant));
  }

  /**
   * Fired for leaf nodes.
   */
  public static Content content(String s, int index) {
    return new Content(s, index);
  }

  /**
   * Generated during parse for tokens matched so that our LR detection can
   * determine whether content has been consumed since a push of a variant.
   */
  public static Token token(String s, int index) {
    return new Token(s, index);
  }

  /**
   * An ephemeral event that indicates that we are starting to look for a
   * left-recursive call so that we can grow a seed.
   * There should be all/only pushes and pops between an
   * {@link LRStart} and the {@link LREnd} that brackets it, which can
   * be pushed back to before the start of the seed.
   */
  public static LRStart leftRecursionSuffixStart() {
    return LRStart.INSTANCE;
  }

  /**
   * An ephemeral event that indicates that the corresponding preceding
   * {@link LRStart} is finished.
   */
  public static LREnd leftRecursionSuffixEnd(NodeType nodeType) {
    return new LREnd(nodeType);
  }

  /**
   * Indicates a source position within a stream of events.
   * This is not generated during parse, but may be generated during unparse and
   * used to maintain source location mappings from the original input to
   * generated code so that debuggers may reverse the mapping when presenting
   * error messages.
   */
  public static SourcePositionMark positionMark(SourcePosition pos) {
    return new SourcePositionMark(pos);
  }

  /**
   * A delayed lookahead that can be applied to the unparsed events that follow
   * it.
   * Lookaheads leave no events during parse, so during unparse, we delay
   * checking the lookahead until the following content is available.
   */
  public static DelayedCheck delayedCheck(
      Predicate<Unparse.Suffix> suffixCheck) {
    return new DelayedCheck(suffixCheck);
  }

  /**
   * True iff non-ignorable tokens were consumed to produce this event, thus
   * limiting LR search.
   */
  public abstract int nCharsConsumed();


  /**
   * An event that happens when we leave a node after visiting its content and
   * children.
   */
  public static final class Pop extends MatchEvent {
    /**
     * The variant popped if known.
     * This is non-normative, but useful for debugging.
     */
    public final Optional<NodeVariant> variant;

    Pop(Optional<NodeVariant> variant) {
      this.variant = variant;
    }

    static final Pop INSTANCE = new Pop(Optional.absent());

    @Override
    public String toString() {
      return variant.isPresent() ? "(pop " + variant.get() + ")" : "pop";
    }

    @Override
    public int nCharsConsumed() {
      return 0;
    }

    @Override
    public boolean equals(Object o) {
      return o != null && this.getClass() == o.getClass();
    }

    @Override
    public int hashCode() {
      return getClass().hashCode();
    }
  }

  /**
   * An event fired on entering a node before visiting its content or children.
   */
  public static final class Push extends MatchEvent {
    /** Variant of the node entered. */
    public final NodeVariant variant;

    Push(NodeVariant variant) {
      this.variant = variant;
    }

    @Override
    public int nCharsConsumed() {
      return 0;
    }

    @Override
    public int hashCode() {
      final int prime = 31;
      int result = 1;
      result = prime * result + ((variant == null) ? 0 : variant.hashCode());
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
      Push other = (Push) obj;
      if (variant == null) {
        if (other.variant != null) {
          return false;
        }
      } else if (!variant.equals(other.variant)) {
        return false;
      }
      return true;
    }

    @Override
    public String toString() {
      return "(push " + variant + ")";
    }
  }

  /**
   * Fired for leaf nodes.
   */
  public static final class Content extends MatchEvent {
    /** The leaf value. */
    public final String content;
    /** Character index into the input of the start of the token. */
    public final int index;

    Content(String content, int index) {
      this.content = content;
      this.index = index;
    }

    @Override
    public int nCharsConsumed() {
      return content.length();
    }

    @Override
    public int hashCode() {
      final int prime = 31;
      int result = 1;
      result = prime * result + ((content == null) ? 0 : content.hashCode());
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
      Content other = (Content) obj;
      if (content == null) {
        if (other.content != null) {
          return false;
        }
      } else if (!content.equals(other.content)) {
        return false;
      }
      return true;
    }

    @Override
    public String toString() {
      return "(`" + content + "`)";
    }
  }

  /**
   * Added for tokens that do not contribute directly to tree content.
   */
  public static final class Token extends MatchEvent {
    /** The token text. */
    public final String content;
    /** Character index into the input of the start of the token. */
    public final int index;

    Token(String content, int index) {
      this.content = content;
      this.index = index;
    }

    @Override
    public int nCharsConsumed() {
      return content.length();
    }

    @Override
    public int hashCode() {
      final int prime = 31;
      int result = 1;
      result = prime * result + ((content == null) ? 0 : content.hashCode());
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
      Token other = (Token) obj;
      if (content == null) {
        if (other.content != null) {
          return false;
        }
      } else if (!content.equals(other.content)) {
        return false;
      }
      return true;
    }

    @Override
    public String toString() {
      return "`" + content + "`";
    }
  }

  /**
   * An ephemeral event that indicates that what follows is a repetition of a
   * suffix that is used to grow the seed.
   */
  public static final class LRStart extends MatchEvent {
    static final LRStart INSTANCE = new LRStart();

    private LRStart() {
      // singleton
    }

    @Override
    public String toString() {
      return "LRStart";
    }

    @Override
    public int nCharsConsumed() {
      return 0;
    }
  }

  /**
   * An ephemeral event that indicates that the corresponding preceding
   * {@link LRStart} is finished.
   */
  public static final class LREnd extends MatchEvent {
    /**
     * The production that was reached left-recursively.
     */
    public final NodeType nodeType;

    LREnd(NodeType nodeType) {
      this.nodeType = nodeType;
    }

    @Override
    public String toString() {
      return "(LREnd " + nodeType + ")";
    }

    @Override
    public int nCharsConsumed() {
      return 0;
    }

    @Override
    public int hashCode() {
      final int prime = 31;
      int result = 1;
      result = prime * result + ((nodeType == null) ? 0 : nodeType.hashCode());
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
      LREnd other = (LREnd) obj;
      if (nodeType != other.nodeType) {
        return false;
      }
      return true;
    }
  }

  /**
   * Indicates a source position within a stream of events.
   * This is not generated during parse, but may be generated during unparse and
   * used to maintain source location mappings from the original input to
   * generated code so that debuggers may reverse the mapping when presenting
   * error messages.
   */
  public static final class SourcePositionMark extends MatchEvent {
    /** Best guess at the source position before the start of the next token. */
    public final SourcePosition pos;

    SourcePositionMark(SourcePosition pos) {
      this.pos = pos;
    }

    @Override
    public int nCharsConsumed() {
      return 0;
    }

    @Override
    public String toString() {
      return "(@ " + pos + ")";
    }

    @Override
    public int hashCode() {
      final int prime = 31;
      int result = 1;
      result = prime * result + ((pos == null) ? 0 : pos.hashCode());
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
      SourcePositionMark other = (SourcePositionMark) obj;
      if (pos == null) {
        if (other.pos != null) {
          return false;
        }
      } else if (!pos.equals(other.pos)) {
        return false;
      }
      return true;
    }
  }

  /**
   * A delayed lookahead that can be applied to the unparsed events that follow
   * it.
   * Lookaheads leave no events during parse, so during unparse, we delay
   * checking the lookahead until the following content is available.
   */
  public static final class DelayedCheck extends MatchEvent {
    /** Can be applied to the sub-list of events following the delayed check. */
    public final Predicate<Unparse.Suffix> p;

    DelayedCheck(Predicate<Unparse.Suffix> p) {
      this.p = p;
    }

    @Override
    public int nCharsConsumed() {
      return 0;
    }

    @Override
    public int hashCode() {
      final int prime = 31;
      int result = 1;
      result = prime * result + ((p == null) ? 0 : p.hashCode());
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
      DelayedCheck other = (DelayedCheck) obj;
      if (p == null) {
        if (other.p != null) {
          return false;
        }
      } else if (!p.equals(other.p)) {
        return false;
      }
      return true;
    }

    @Override
    public String toString() {
      return "(delayed " + p + ")";
    }
  }
}
