package com.mikesamuel.cil.expr;

import javax.annotation.Nullable;

/**
 * A completion record that unifies statement and expression results.
 */
public final class Completion<VALUE> {
  /** A kind-specific value. */
  public final @Nullable VALUE value;
  /** A label for break or continue statements. */
  public final @Nullable String label;
  /** Specifies how to find the next instruction to execute. */
  public final Kind kind;

  private Completion(@Nullable VALUE value, @Nullable String label, Kind kind) {
    this.value = value;
    this.label = label;
    this.kind = kind;
  }

  /** A normal completion that steps to the next instruction. */
  public static <VALUE> Completion<VALUE> normal(@Nullable VALUE v) {
    return new Completion<>(v, null, Kind.NORMAL);
  }

  /** A completion that breaks out of a statement. */
  public static <VALUE> Completion<VALUE> breakTo(@Nullable String label) {
    return new Completion<>(null, label, Kind.BREAK);
  }

  /** A completion that continues a loop. */
  public static <VALUE> Completion<VALUE> continueTo(@Nullable String label) {
    return new Completion<>(null, label, Kind.CONTINUE);
  }

  /** A completion that unwinds the stack until a protected region is exited. */
  public static <VALUE> Completion<VALUE> abrupt(@Nullable VALUE v) {
    return new Completion<>(v, null, Kind.THROW);
  }

  /** A completion whose value is used in place of the current call. */
  public static <VALUE> Completion<VALUE> returnValue(@Nullable VALUE v) {
    return new Completion<>(v, null, Kind.RETURN);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("(Completion");
    boolean needsValue = value != null;
    switch (kind) {
      case BREAK:
      case CONTINUE:
        break;
      case NORMAL:
      case RETURN:
      case THROW:
        needsValue = true;
        break;
    }
    if (needsValue) {
      sb.append(' ').append(value);
    }
    if (label != null) {
      sb.append(" label=").append(label);
    }
    return sb.append(')').toString();
  }

  /**
   * The kind of completion.
   */
  public enum Kind {
    /** Completes normally with the associated value. */
    NORMAL,
    /**
     * Breaks out of the statement with the associated label,
     * or the innermost loop or switch if the label is null.
     */
    BREAK,
    /**
     * Continues the statement with the associated label,
     * or the innermost loop if the label is null.
     */
    CONTINUE,
    /**
     * Returns from the enclosing method with the associated value.
     */
    RETURN,
    /**
     * Abruptly suspends normal execution.
     */
    THROW,
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((kind == null) ? 0 : kind.hashCode());
    result = prime * result + ((label == null) ? 0 : label.hashCode());
    result = prime * result + ((value == null) ? 0 : value.hashCode());
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
    Completion<?> other = (Completion<?>) obj;
    if (kind != other.kind) {
      return false;
    }
    if (label == null) {
      if (other.label != null) {
        return false;
      }
    } else if (!label.equals(other.label)) {
      return false;
    }
    if (value == null) {
      if (other.value != null) {
        return false;
      }
    } else if (!value.equals(other.value)) {
      return false;
    }
    return true;
  }
}
