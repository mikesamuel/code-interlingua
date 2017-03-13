package com.mikesamuel.cil.parser;

import java.util.Arrays;
import java.util.List;

import com.google.common.collect.Lists;

/**
 * Handles
 * <a href="https://docs.oracle.com/javase/specs/jls/se8/html/jls-3.html#jls-3.2">Lexical Translation</a>
 * by decoding <code>&#x5c;uXXXX</code> escapes.
 * <p>
 * Also maps indices back so that we can be accurate about source positions.
 */
public final class DecodedContent implements CharSequence {
  private final String underlying;
  private final int[] indicesOfEscapedCharactersInDecoded;

  /**
   * @throws IllegalArgumentException if there is a '\\' 'u' not followed by
   *    4 hex digits.
   */
  DecodedContent(String underlying) throws IllegalArgumentException {
    this.underlying = underlying;
    int n = underlying.length();
    List<Integer> inDecoded = Lists.newArrayList();
    int delta = 0;
    for (int i = 0; i < n; ++i) {
      char ch = underlying.charAt(i);
      if (ch == '\\') {
        if (i + 1 < n && underlying.charAt(i + 1) == 'u') {
          inDecoded.add(i - delta);
          int decoded = decodeHex4(underlying, i + 2);
          i += 5;  // skip over the escape sequence when combined with ++i above
          delta += 5;
          if (decoded == '\\') {
            // \u005cu005c encodes \u005c
            // \u005c\u005c also encodes \u005c
            ++i;
          }
        } else {
          ++i;
        }
      }
    }
    this.indicesOfEscapedCharactersInDecoded = toIntArray(inDecoded);
  }


  private static int decodeHex4(String s, int i) {
    int n = s.length();
    if (i + 4 > n) {
      throw new IllegalArgumentException(
          "Expected 4 hex digits not `" + s.substring(i) + "`");
    }
    int decoded = 0;
    for (int j = i; j < i + 4; ++j) {
      char ch = s.charAt(j);
      int value;
      if ('0' <= ch && ch <= '9') {
        value = ch - '0';
      } else {
        ch |= 32;
        if ('a' <= ch && ch <= 'z') {
          value = ch + 10 - 'a';
        } else {
          throw new IllegalArgumentException(
              "Expected 4 hex digits not `" + s.substring(i, i + 4) + "`");
        }
      }
      decoded = (decoded << 4) | (value & 0xf);
    }
    return decoded;
  }


  @Override
  public int length() {
    return underlying.length() - indicesOfEscapedCharactersInDecoded.length * 5;
  }


  @Override
  public char charAt(int index) {
    int k = Arrays.binarySearch(
        this.indicesOfEscapedCharactersInDecoded, index);
    char ch;
    if (k < 0) {
      int nEscapesBefore = ~k;
      int indexInEncoded = index + 5 * nEscapesBefore;
      ch = underlying.charAt(indexInEncoded);
    } else {
      int indexInEncoded = index + 5 * k;
      ch = (char) decodeHex4(underlying, indexInEncoded + 2);
    }
    return ch;
  }

  /**
   * The index into the underlying content that corresponds to the given index.
   */
  public int indexInEncoded(int decodedIndex) {
    int k = Arrays.binarySearch(
        this.indicesOfEscapedCharactersInDecoded, decodedIndex);
    if (k < 0) {
      k = ~k;
    }
    return decodedIndex + k * 5;
  }

  /** A subsequence that extends from the given start to the end. */
  public final CharSequence subSequence(int start) {
    return subSequence(start, length());
  }

  @Override
  public CharSequence subSequence(final int start, final int end) {
    return new CharSequence() {

      @Override
      public int length() {
        return end - start;
      }

      @Override
      public char charAt(int index) {
        return DecodedContent.this.charAt(index + start);
      }

      @Override
      public CharSequence subSequence(int subStart, int subEnd) {
        return DecodedContent.this.subSequence(
            start + subStart, start + subEnd);
      }

      @Override
      public String toString() {
        return new StringBuilder().append(this).toString();
      }
    };
  }

  @Override
  public String toString() {
    return new StringBuilder().append(this).toString();
  }

  private static final int[] ZERO_INTS = new int[0];
  private static int[] toIntArray(List<Integer> ls) {
    int n = ls.size();
    if (n == 0) { return ZERO_INTS; }
    int[] arr = new int[n];
    for (int i = n; --i >= 0;) {
      arr[i] = ls.get(i);
    }
    return arr;
  }
}
