package com.mikesamuel.cil.event;

import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.mikesamuel.cil.ast.NodeType;
import com.mikesamuel.cil.ast.NodeVariant;
import com.mikesamuel.cil.parser.SourcePosition;
import com.mikesamuel.cil.parser.Unparse;

/**
 * An event in a series which describes a pre-order traversal of a parse-tree.
 * <p>
 * Some events are "ephemeral" which means they are used for bookkeeping by the
 * parser during a parse and should not be seen by the tree-builder or emitted
 * by a tree flattener.
 */
public abstract class Event {

  /**
   * An enum that can be used to switch on the kind of event and which provides
   * field access to encourage event handling patterns that make explicit which
   * kinds of events are being handled in the default case, and so that adding
   * a new kind of event flags code that has not been updated.
   */
  public enum Kind {
    /** @see {Event#content} */
    CONTENT,
    /** @see {Event#delayedCheck} */
    DELAYED_CHECK,
    /** @see {Event#ignorable} */
    IGNORABLE,
    /** @see {Event#leftRecursionSuffixEnd} */
    LR_END,
    /** @see {Event#leftRecursionSuffixStart} */
    LR_START,
    /** @see {Event#pop} */
    POP,
    /** @see {Event#positionMark} */
    POSITION_MARK,
    /** @see {Event#push} */
    PUSH,
    /** @see {Event#token} */
    TOKEN,
  }


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
   * An event fired when a prefix of the input matches a lexical token whose
   * content is semantically significant.
   * <p>
   * These events correspond to the content of leaf nodes in the AST.
   */
  public static Content content(String s, int index) {
    return new Content(s, index);
  }

  /**
   * An event fired when a prefix of the input matches a literal string in the
   * grammar.
   * <p>
   * Since the string appears directly in the grammar, its content is not
   * semantically significant and serves only to control which variant was
   * token.
   */
  public static Token token(String s, int index) {
    return new Token(s, index);
  }

  /**
   * Text that matches an ignorable token like a JavaDoc comment.
   */
  public static Ignorable ignorable(String s, int index) {
    return new Ignorable(s, index);
  }


  /**
   * An ephemeral event that indicates that we are starting to look for a
   * left-recursive call so that we can grow a seed.
   * There should be all/only pushes and pops between an
   * {@link #leftRecursionSuffixStart} and the {@link #leftRecursionSuffixEnd}
   * that brackets it, which can be pushed back to before the start of the seed.
   */
  public static LRStart leftRecursionSuffixStart() {
    return LRStart.INSTANCE;
  }

  /**
   * An ephemeral event that indicates that the corresponding preceding
   * {@link #leftRecursionSuffixStart} is finished.
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

  /** Allows switching on events. */
  public abstract Kind getKind();

  /**
   * True iff non-ignorable tokens were consumed to produce this event, thus
   * limiting LR search.
   */
  public final int nCharsConsumed() { return getContent().length(); }

  /** If this consumes characters, the content consumed. */
  @SuppressWarnings("static-method")  // Overridable
  public String getContent() {
    return "";
  }

  /** The index of the content in the input or -1 if there is no content. */
  @SuppressWarnings("static-method")  // Overridable
  public int getContentIndex() {
    return -1;
  }

  /** For {@link Kind#POSITION_MARK} */
  public SourcePosition getSourcePosition() {
    throw new UnsupportedOperationException(getKind().name());
  }

  /** For {@link Kind#PUSH} */
  public NodeVariant getNodeVariant() {
    throw new UnsupportedOperationException(getKind().name());
  }

  /** For {@link Kind#PUSH} and {@link Kind#LR_END} */
  public NodeType getNodeType() {
    throw new UnsupportedOperationException(getKind().name());
  }

  /** For {@link Kind#DELAYED_CHECK} */
  public Predicate<Unparse.Suffix> getDelayedCheck() {
    throw new UnsupportedOperationException(getKind().name());
  }

  /**
   * An event that happens when we leave a node after visiting its content and
   * children.
   */
  static final class Pop extends Event {
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
    public boolean equals(Object o) {
      return o != null && this.getClass() == o.getClass();
    }

    @Override
    public int hashCode() {
      return getClass().hashCode();
    }

    @Override
    public Kind getKind() {
      return Kind.POP;
    }
  }

  /**
   * An event fired on entering a node before visiting its content or children.
   */
  static final class Push extends Event {
    /** Variant of the node entered. */
    public final NodeVariant variant;

    Push(NodeVariant variant) {
      this.variant = variant;
    }

    @Override
    public Kind getKind() {
      return Kind.PUSH;
    }

    @Override
    public NodeVariant getNodeVariant() {
      return variant;
    }

    @Override
    public NodeType getNodeType() {
      return variant.getNodeType();
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
  static final class Content extends Event {
    /** The leaf value. */
    public final String content;
    /** Character index into the input of the start of the token. */
    public final int index;

    Content(String content, int index) {
      this.content = content;
      this.index = index;
    }

    @Override
    public Kind getKind() {
      return Kind.CONTENT;
    }

    @Override
    public String getContent() {
      return content;
    }

    @Override
    public int getContentIndex() {
      return index;
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
  static final class Token extends Event {
    /** The token text. */
    public final String content;
    /** Character index into the input of the start of the token. */
    public final int index;

    Token(String content, int index) {
      this.content = content;
      this.index = index;
    }

    @Override
    public Kind getKind() {
      return Kind.TOKEN;
    }

    @Override
    public String getContent() {
      return content;
    }

    @Override
    public int getContentIndex() {
      return index;
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
   * Text that matches an ignorable token like a JavaDoc comment.
   */
  static final class Ignorable extends Event {
    /** The ignorable text. */
    public final String ignorableContent;
    /** Character index into the input of the start of the content. */
    public final int index;

    Ignorable(String content, int index) {
      this.ignorableContent = content;
      this.index = index;
    }

    @Override
    public String getContent() {
      return ignorableContent;
    }

    @Override
    public int getContentIndex() {
      return index;
    }

    @Override
    public Kind getKind() {
      return Kind.IGNORABLE;
    }

    @Override
    public int hashCode() {
      final int prime = 31;
      int result = 1;
      result = prime * result
          + ((ignorableContent == null) ? 0 : ignorableContent.hashCode());
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
      Ignorable other = (Ignorable) obj;
      if (ignorableContent == null) {
        if (other.ignorableContent != null) {
          return false;
        }
      } else if (!ignorableContent.equals(other.ignorableContent)) {
        return false;
      }
      return true;
    }

    @Override
    public String toString() {
      return "[`" + ignorableContent + "`]";
    }
  }

  /**
   * An ephemeral event that indicates that what follows is a repetition of a
   * suffix that is used to grow the seed.
   */
  static final class LRStart extends Event {
    static final LRStart INSTANCE = new LRStart();

    private LRStart() {
      // singleton
    }

    @Override
    public Kind getKind() {
      return Kind.LR_START;
    }

    @Override
    public String toString() {
      return "LRStart";
    }
  }

  /**
   * An ephemeral event that indicates that the corresponding preceding
   * {@link #leftRecursionSuffixStart} is finished.
   */
  static final class LREnd extends Event {
    /**
     * The production that was reached left-recursively.
     */
    public final NodeType nodeType;

    LREnd(NodeType nodeType) {
      this.nodeType = nodeType;
    }

    @Override
    public Kind getKind() {
      return Kind.LR_END;
    }

    @Override
    public NodeType getNodeType() {
      return nodeType;
    }

    @Override
    public String toString() {
      return "(LREnd " + nodeType + ")";
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
  static final class SourcePositionMark extends Event {
    /** Best guess at the source position before the start of the next token. */
    public final SourcePosition pos;

    SourcePositionMark(SourcePosition pos) {
      this.pos = pos;
    }

    @Override
    public SourcePosition getSourcePosition() {
      return pos;
    }

    @Override
    public Kind getKind() {
      return Kind.POSITION_MARK;
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
  static final class DelayedCheck extends Event {
    /** Can be applied to the sub-list of events following the delayed check. */
    public final Predicate<Unparse.Suffix> p;

    DelayedCheck(Predicate<Unparse.Suffix> p) {
      this.p = p;
    }

    @Override
    public Kind getKind() {
      return Kind.DELAYED_CHECK;
    }

    @Override
    public Predicate<Unparse.Suffix> getDelayedCheck() {
      return p;
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
