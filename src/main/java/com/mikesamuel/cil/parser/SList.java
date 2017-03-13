package com.mikesamuel.cil.parser;

import java.util.Iterator;
import java.util.NoSuchElementException;

import javax.annotation.Nullable;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

/** A singly-linked list built in reverse. */
public final class SList<T> {
  /** The content. */
  public final T x;
  /** The previous element in the list. */
  public final @Nullable SList<T> prev;

  private SList(T x, @Nullable SList<T> prev) {
    this.x = x;
    this.prev = prev;
  }

  /** Iterates in reverse order. */
  public static <T> Iterable<T> reverseIterable(
      @Nullable final SList<? extends T> c) {
    return new Iterable<T>() {
      @Override
      public Iterator<T> iterator() {
        return new Iterator<T>() {
          SList<? extends T> ch = c;

          @Override
          public boolean hasNext() {
            return ch != null;
          }

          @Override
          public T next() {
            if (ch == null) { throw new NoSuchElementException(); }
            T next = ch.x;
            ch = ch.prev;
            return next;
          }
        };
      }

      @Override
      public String toString() {
        return Iterables.toString(this);
      }
    };
  }

  /** Iterates from farthest back to the current. */
  public static <T> Iterable<T> forwardIterable(
      @Nullable SList<? extends T> c) {
    ImmutableList.Builder<T> b = ImmutableList.builder();
    for (SList<? extends T> ch = c; ch != null; ch = ch.prev) {
      b.add(ch.x);
    }
    return b.build().reverse();
  }

  /**
   * The slist with next following prev.
   */
  public static <T> SList<T> append(@Nullable SList<T> prev, T next) {
    return new SList<>(next, prev);
  }

  /** An slist of the same length as ls with the elements in reverse order. */
  public static <T> SList<T> reverse(@Nullable SList<T> ls) {
    SList<T> rev = null;
    for (SList<T> rest = ls; rest != null; rest = rest.prev) {
      rev = append(rev, rest.x);
    }
    return rev;
  }

  /**
   * The slist that has all the elements of prev followed by
   * {@link #reverse reverse}{@code (next)}.
   */
  public static <T> SList<T> revAppendAll(
      @Nullable SList<T> prev, @Nullable SList<T> next) {
    SList<T> out = prev;
    for (SList<T> c = next; c != null; c = c.prev) {
      out = append(out, c.x);
    }
    return out;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((prev == null) ? 0 : prev.hashCode());
    result = prime * result + ((x == null) ? 0 : x.hashCode());
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
    SList<?> other = (SList<?>) obj;
    if (prev == null) {
      if (other.prev != null) {
        return false;
      }
    } else if (!prev.equals(other.prev)) {
      return false;
    }
    if (x == null) {
      if (other.x != null) {
        return false;
      }
    } else if (!x.equals(other.x)) {
      return false;
    }
    return true;
  }
}
