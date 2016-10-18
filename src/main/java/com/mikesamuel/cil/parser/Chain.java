package com.mikesamuel.cil.parser;

import java.util.Iterator;
import java.util.NoSuchElementException;

import com.google.common.collect.ImmutableList;

/** A singly-linked list built in reverse. */
public final class Chain<T> {
  /** The content. */
  public final T x;
  /** The previous element in the chain. */
  public final Chain<? extends T> prev;

  private Chain(T x, Chain<? extends T> prev) {
    this.x = x;
    this.prev = prev;
  }

  /** Iterates in reverse order. */
  public Iterable<T> reverse() {
    return new Iterable<T>() {
      @Override
      public Iterator<T> iterator() {
        return new Iterator<T>() {
          Chain<? extends T> ch = Chain.this;

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
    };
  }

  /** Iterates from farthest back to the current. */
  public Iterable<T> forward() {
    ImmutableList.Builder<T> b = ImmutableList.builder();
    for (Chain<? extends T> ch = this; ch != null; ch = ch.prev) {
      b.add(ch.x);
    }
    return b.build().reverse();
  }

  /**
   * The chain with next following prev.
   */
  public static <T> Chain<T> append(Chain<T> prev, T next) {
    return new Chain<>(next, prev);
  }

  /**
   * A chain that is equivalent to the given chain.
   */
  public static <T>
  Chain<T> copyOf(Chain<? extends T> output) {
    @SuppressWarnings("unchecked")  // Chain is immutable.
    Chain<T> notReallyACopy = (Chain<T>) output;
    return notReallyACopy;
  }
}
