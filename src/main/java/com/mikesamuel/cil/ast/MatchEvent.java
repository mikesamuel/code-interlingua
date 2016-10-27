package com.mikesamuel.cil.ast;

import com.google.common.collect.ImmutableList;

/**
 * An event in a series which describes a pre-order traversal of a parse-tree.
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
   * Indicates that a left-recursive use was found starting at the given
   * variant.
   * What follows is the "seed" as defined in Alessandro Warth's
   * "grow the seed" algorithm.
   */
  public static LRStart leftRecursionStart(NodeType nodeType) {
    return new LRStart(nodeType);
  }

  /**
   * Indicates that what follows is a repetition of the LR suffix that grows
   * the seed using the suffix from the given variant.
   */
  public static LRSuffix leftRecursionSuffix(
      Iterable<? extends NodeVariant> variant) {
    return new LRSuffix(variant);
  }

  /**
   * Indicates that the left-recursive use started by the corresponding
   * {@link #leftRecursionStart} was completed.
   */
  public static LREnd leftRecursionEnd() {
    return LREnd.INSTANCE;
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
    private Pop() {}

    static final Pop INSTANCE = new Pop();

    @Override
    public String toString() {
      return "pop";
    }

    @Override
    public int nCharsConsumed() {
      return 0;
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
   * Indicates that what follows is the start of a "seed" used to handle
   * a left-recursive invocation of a node of type NodeType.
   */
  public static final class LRStart extends MatchEvent {
    /** The variant at which the left-recursive cycle started. */
    public final NodeType nodeType;

    LRStart(NodeType nodeType) {
      this.nodeType = nodeType;
    }

    @Override
    public int nCharsConsumed() {
      return 0;
    }

    @Override
    public int hashCode() {
      return nodeType.hashCode();
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
      LRStart other = (LRStart) obj;
      return nodeType == other.nodeType;
    }

    @Override
    public String toString() {
      return "(LRStart " + nodeType + ")";
    }
  }

  /**
   * Indicates that a repetition of the LR tail occurred which means that the
   * markers between the LR push and the seed need to be duplicated.
   */
  public static final class LRSuffix extends MatchEvent {
    /** The variant that defines the suffix used. */
    public final ImmutableList<NodeVariant> variants;

    LRSuffix(Iterable<? extends NodeVariant> variants) {
      this.variants = ImmutableList.copyOf(variants);
    }


    @Override
    public String toString() {
      return "(LRSuffix " + variants + ")";
    }

    @Override
    public int nCharsConsumed() {
      return 0;
    }

    @Override
    public int hashCode() {
      final int prime = 31;
      int result = 1;
      result = prime * result + variants.hashCode();
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
      LRSuffix other = (LRSuffix) obj;
      if (!variants.equals(other.variants)) {
        return false;
      }
      return true;
    }
  }

  /**
   * Indicates that the corresponding preceding {@link LRStart} is finished.
   */
  public static final class LREnd extends MatchEvent {
    static final LREnd INSTANCE = new LREnd();

    private LREnd() {
      // singleton
    }

    @Override
    public String toString() {
      return "LREnd";
    }

    @Override
    public int nCharsConsumed() {
      return 0;
    }
  }
}
