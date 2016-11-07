package com.mikesamuel.cil.parser;

import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.base.Joiner;

/**
 * Describes prefixes of a language that could be matched during parse.
 */
public final class Lookahead1 {

  private final Pattern p;

  private Lookahead1(Pattern p) {
    this.p = p;
  }

  /**
   * True if the lookahead could match at the given state's current cursor.
   */
  public boolean canFollow(ParseState state) {
    Matcher m = state.matcherAtStart(p);
    return m.find();
  }

  /**
   * A lookahead that allows any character in the given character set.
   */
  public static Lookahead1 of(String... characterSets) {
    Arrays.sort(characterSets);
    String rex = "^[" + Joiner.on("").join(characterSets) + "]";
    return new Lookahead1(Pattern.compile(rex));
  }
}
