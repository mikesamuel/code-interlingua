package com.mikesamuel.cil.format;

import com.google.common.collect.ImmutableList;

/**
 * A token sink that collects tokens onto a StringBuilder.
 */
public class StringBuilderTokenSink extends AbstractTokenSink {
  private final ImmutableList.Builder<Integer> tokenIndices =
      ImmutableList.builder();
  private final StringBuilder sb = new StringBuilder();

  @Override
  protected void appendTokenContent(String content, String adjustedContent) {
    tokenIndices.add(sb.length());
    sb.append(adjustedContent);
  }

  @Override
  protected void appendSpaceChars(char ch, int count) {
    sb.ensureCapacity(sb.length() + count);
    for (int i = 0; i < count; ++i) {
      sb.append(ch);
    }
  }

  @Override
  public int charInFile() {
    return sb.length();
  }

  /**
   * The start indices of tokens in {@link #getCode}.
   */
  public ImmutableList<Integer> getTokenIndices() {
    return tokenIndices.build();
  }

  /** The tokens appended with any specified white-space. */
  public String getCode() {
    return sb.toString();
  }
}
