package com.mikesamuel.cil.ast;

import com.google.common.collect.ImmutableList;

/**
 * An event in a series which describes a pre-order traversal of a parse-tree.
 */
public abstract class MatchEvent {

  /**
   * An event fired on entering a node before visiting its content or children.
   */
  public static PushMatchEvent push(NodeVariant v) {
    return new PushMatchEvent(v);
  }

  /**
   * An event that happens when we leave a node after visiting its content and
   * children.
   */
  public static PopMatchEvent pop() {
    return PopMatchEvent.INSTANCE;
  }

  /**
   * Fired for leaf nodes.
   */
  public static ContentMatchEvent content(String s) {
    return new ContentMatchEvent(s);
  }

  /**
   * Generated during parse for tokens matched so that our LR detection can
   * determine whether content has been consumed since a push of a variant.
   */
  public static TokenMatchEvent token(String s) {
    return new TokenMatchEvent(s);
  }

  /**
   * Indicates that a left-recursive use was found starting at the given
   * variant.
   * What follows is the "seed" as defined in Alessandro Warth's
   * "grow the seed" algorithm.
   */
  public static LRStart leftRecursionStart(NodeVariant variant) {
    return new LRStart(variant);
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
  public abstract boolean wasTextConsumed();


  /**
   * An event that happens when we leave a node after visiting its content and
   * children.
   */
  public static final class PopMatchEvent extends MatchEvent {
    private PopMatchEvent() {}

    static final PopMatchEvent INSTANCE = new PopMatchEvent();

    @Override
    public String toString() {
      return "pop";
    }

    @Override
    public boolean wasTextConsumed() {
      return false;
    }
  }

  /**
   * An event fired on entering a node before visiting its content or children.
   */
  public static final class PushMatchEvent extends MatchEvent {
    /** Variant of the node entered. */
    public final NodeVariant variant;

    PushMatchEvent(NodeVariant variant) {
      this.variant = variant;
    }

    @Override
    public boolean wasTextConsumed() {
      return false;
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
      PushMatchEvent other = (PushMatchEvent) obj;
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
      return "(push " + variant.getNodeType().name() + "." + variant + ")";
    }
  }

  /**
   * Fired for leaf nodes.
   */
  public static final class ContentMatchEvent extends MatchEvent {
    /** The leaf value. */
    public final String content;

    ContentMatchEvent(String content) {
      this.content = content;
    }

    @Override
    public boolean wasTextConsumed() {
      return !content.isEmpty();
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
      ContentMatchEvent other = (ContentMatchEvent) obj;
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
  public static final class TokenMatchEvent extends MatchEvent {
    /** The leaf value. */
    public final String content;

    TokenMatchEvent(String content) {
      this.content = content;
    }

    @Override
    public boolean wasTextConsumed() {
      return !content.isEmpty();
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
      TokenMatchEvent other = (TokenMatchEvent) obj;
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
    public final NodeVariant variant;

    LRStart(NodeVariant variant) {
      this.variant = variant;
    }

    @Override
    public boolean wasTextConsumed() {
      return false;
    }

    @Override
    public int hashCode() {
      return variant.hashCode();
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
      return variant == other.variant;
    }

    @Override
    public String toString() {
      return "(LRStart " + variant + ")";
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
    public boolean wasTextConsumed() {
      return false;
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
    private LREnd() {
      // Singleton
    }

    static final LREnd INSTANCE = new LREnd();

    @Override
    public String toString() {
      return "LREnd";
    }

    @Override
    public boolean wasTextConsumed() {
      return false;
    }
  }
}
