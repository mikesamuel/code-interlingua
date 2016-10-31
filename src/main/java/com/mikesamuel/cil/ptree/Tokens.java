package com.mikesamuel.cil.ptree;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Lists;
import com.mikesamuel.cil.ast.TokenStrings;

/**
 * Defines lexical structures.
 *
 * @see <a href="https://docs.oracle.com/javase/specs/jls/se7/html/jls-3.html">Lexical Structure</a>
 */
public final class Tokens {

  private static final String ESCAPE_SEQUENCE =
      "\\\\(?:[ntnfr\"'\\\\]|[0-3][0-7]{0,2}|[4-7][0-7]?)";

  private static final String INPUT_CHARACTER_NO_QUOTES = "[^\r\n\"\'\\\\]";

  private static final String CHAR_NO_QUOTES =
      "(?:" + INPUT_CHARACTER_NO_QUOTES + "|" + ESCAPE_SEQUENCE + ")";


  /** Matches a {@code '.'} style character literal. Section 3.10.4. */
  public static final PatternMatch CHARACTER_LITERAL = new PatternMatch(
      "'(?:\"|" + CHAR_NO_QUOTES + ")'",
      "'.'");

  /** 3.10.2 */
  public static final PatternMatch FLOATING_POINT_LITERAL;
  /** 3.10.1 */
  public static final PatternMatch INTEGER_LITERAL;

  static {
    String numMergeConflict = "(?![\\p{javaJavaIdentifierPart}])";

    String underscores = "_+";
    String underscoresOpt = "_*";

    // Digit: one of
    //    0 1 2 3 4 5 6 7 8 9
    String digit = "[0-9]";
    String nonZeroDigit = "[1-9]";

    // DigitOrUnderscore:
    //    Digit
    //    _
    String digitOrUnderscore = "[0-9_]";

    // DigitsAndUnderscores:
    //    DigitOrUnderscore
    //    DigitsAndUnderscores DigitOrUnderscore
    // A = (B | A C)
    // is equivalent to
    // A = B C*
    String digitsAndUnderscores =
        "(?:" + digitOrUnderscore + digitOrUnderscore + "*)";
    String digitsAndUnderscoresOpt = digitsAndUnderscores + "?";

    // Digits:
    //    Digit
    //    Digit DigitsAndUnderscoresopt Digit
    String digits =
        "(?:" + digit + "(?:" + digitsAndUnderscoresOpt + digit + ")?)";
    String digitsOpt = digits + "?";

    // DecimalNumeral:
    //    0
    //    NonZeroDigit Digitsopt
    //    NonZeroDigit Underscores Digits
    String decimalNumeral =
        "(?:0|" + nonZeroDigit
        + "(?:" + digitsOpt + "|" + underscores + digits + "))";

    // BinaryDigit: one of
    //    0 1
    String binaryDigit = "[01]";

    // BinaryDigitOrUnderscore:
    //    BinaryDigit
    //    _
    String binaryDigitOrUnderscore = "[01_]";

    // BinaryDigitsAndUnderscores:
    //    BinaryDigitOrUnderscore
    //    BinaryDigitsAndUnderscores BinaryDigitOrUnderscore
    // A = (B | A C)
    // is equivalent to
    // A = B C*
    String binaryDigitsAndUnderscores =
        "(?:" + binaryDigitOrUnderscore + binaryDigitOrUnderscore + "*)";
    String binaryDigitsAndUnderscoresOpt = binaryDigitsAndUnderscores + "?";

    // BinaryDigits:
    //    BinaryDigit
    //    BinaryDigit BinaryDigitsAndUnderscoresopt BinaryDigit
    String binaryDigits = "(?:" + binaryDigit
        + "(?:" + binaryDigitsAndUnderscoresOpt + binaryDigit + ")?)";

    // BinaryNumeral:
    //     0 b BinaryDigits
    //     0 B BinaryDigits
    String binaryNumeral = "0[Bb]" + binaryDigits;

    // HexDigit: one of
    //    0 1 2 3 4 5 6 7 8 9 a b c d e f A B C D E F
    String hexDigit = "[0-9A-Fa-f]";

    // HexDigitOrUnderscore:
    //    HexDigit
    //    _
    String hexDigitOrUnderscore = "[0-9A-Fa-f_]";

    // HexDigitsAndUnderscores:
    //    HexDigitOrUnderscore
    //    HexDigitsAndUnderscores HexDigitOrUnderscore
    // A = (B | A C)
    // is equivalent to
    // A = B C*
    String hexDigitsAndUnderscores =
        "(?:" + hexDigitOrUnderscore + hexDigitOrUnderscore + "*)";
    String hexDigitsAndUnderscoresOpt = hexDigitsAndUnderscores + "?";

    // HexDigits:
    //    HexDigit
    //    HexDigit HexDigitsAndUnderscoresopt HexDigit
    String hexDigits = "(?:" + hexDigit
        + "(?:" + hexDigitsAndUnderscoresOpt + hexDigit + ")?)";
    String hexDigitsOpt = hexDigits + "?";

    // HexNumeral:
    //     0 x HexDigits
    //     0 X HexDigits
    String hexNumeral = "0[Xx]" + hexDigits;

    // OctalDigit: one of
    //     0 1 2 3 4 5 6 7
    String octalDigit = "[0-7]";

    // OctalDigitOrUnderscore:
    //    OctalDigit
    //    _
    String octalDigitOrUnderscore = "[0-7_]";

    // OctalDigitsAndUnderscores:
    //    OctalDigitOrUnderscore
    //    OctalDigitsAndUnderscores OctalDigitOrUnderscore
    // A = (B | A C)
    // is equivalent to
    // A = B C*
    String octalDigitsAndUnderscores =
        "(?:" + octalDigitOrUnderscore + octalDigitOrUnderscore + "*)";
    String octalDigitsAndUnderscoresOpt = octalDigitsAndUnderscores + "?";

    // OctalDigits:
    //    OctalDigit
    //    OctalDigit OctalDigitsAndUnderscoresopt OctalDigit
    String octalDigits = "(?:" + octalDigit
        + "(?:" + octalDigitsAndUnderscoresOpt + octalDigit + ")?)";

    // OctalNumeral:
    //     0 OctalDigits
    //     0 Underscores OctalDigits
    String octalNumeral = "0" + underscoresOpt + octalDigits;

    // ExponentIndicator: one of
    //     e E
    String exponentIndicator = "[Ee]";

    // Sign: one of
    //     + -
    String sign = "[+\\-]";

    // SignedInteger:
    //     Signopt Digits
    String signedInteger = sign + "?" + digits;

    // ExponentPart:
    //    ExponentIndicator SignedInteger
    String exponentPart = exponentIndicator + signedInteger;
    String exponentPartOpt = "(?:" + exponentPart + ")?";

    // FloatTypeSuffix: one of
    //     f F d D
    String floatTypeSuffix = "[DdFf]";
    String floatTypeSuffixOpt = floatTypeSuffix + "?";

    // HexSignificand:
    //     HexNumeral
    //     HexNumeral .
    //     0 x HexDigitsopt . HexDigits
    //     0 X HexDigitsopt . HexDigits
    String hexSignificand = "(?:"
        + hexNumeral + "[.]?"
        + "|0[Xx]" + hexDigitsOpt + "[.]" + hexDigits + ")";

    // BinaryExponentIndicator:one of
    //     p P
    String binaryExponentIndicator = "[Pp]";

    // BinaryExponent:
    //     BinaryExponentIndicator SignedInteger
    String binaryExponent = binaryExponentIndicator + signedInteger;

    // HexadecimalFloatingPointLiteral:
    //     HexSignificand BinaryExponent FloatTypeSuffixopt
    String hexadecimalFloatingPointLiteral = "(?:"
        + hexSignificand + binaryExponent + floatTypeSuffixOpt + ")";


    // DecimalFloatingPointLiteral:
    //    Digits . Digitsopt ExponentPartopt FloatTypeSuffixopt
    //    . Digits ExponentPartopt FloatTypeSuffixopt
    //    Digits ExponentPart FloatTypeSuffixopt
    //    Digits ExponentPartopt FloatTypeSuffix
    String decimalFloatingPointLiteral = "(?:"
        + "[.]" + digits + exponentPartOpt + floatTypeSuffixOpt
        + "|" + digits
          + "(?:"
            + "[.]" + digitsOpt + exponentPartOpt + floatTypeSuffixOpt
            + "|" + exponentPart + floatTypeSuffixOpt
            + "|" + floatTypeSuffix + "))";

    // FloatingPointLiteral:
    //    DecimalFloatingPointLiteral
    //    HexadecimalFloatingPointLiteral
    String floatingPointLiteral = "(?:" + decimalFloatingPointLiteral + "|"
        + hexadecimalFloatingPointLiteral + ")";

    FLOATING_POINT_LITERAL = new PatternMatch(
        floatingPointLiteral + numMergeConflict,
        "0.123"
        );

    // IntegerTypeSuffix: one of
    //     l L
    String integerTypeSuffix = "[Ll]";
    String integerTypeSuffixOpt = integerTypeSuffix + "?";

    // DecimalIntegerLiteral:
    //     DecimalNumeral IntegerTypeSuffixopt
    String decimalIntegerLiteral = decimalNumeral + integerTypeSuffixOpt;

    // HexIntegerLiteral:
    //     HexNumeral IntegerTypeSuffixopt
    String hexIntegerLiteral = hexNumeral + integerTypeSuffixOpt;

    // OctalIntegerLiteral:
    //     OctalNumeral IntegerTypeSuffixopt
    String octalIntegerLiteral = octalNumeral + integerTypeSuffixOpt;

    // BinaryIntegerLiteral:
    //     BinaryNumeral IntegerTypeSuffixopt
    String binaryIntegerLiteral = binaryNumeral + integerTypeSuffixOpt;

    // IntegerLiteral:
    //     DecimalIntegerLiteral
    //     HexIntegerLiteral
    //     OctalIntegerLiteral
    //     BinaryIntegerLiteral
    String integerLiteral = "(?:"
      + decimalIntegerLiteral
      + "|" + hexIntegerLiteral
      + "|" + octalIntegerLiteral
      + "|" + binaryIntegerLiteral
      + ")";

    INTEGER_LITERAL = new PatternMatch(
        integerLiteral + numMergeConflict,
        "123");
  }

  private static final String JAVA_IDENTIFIER_PART =
      "[\\p{javaJavaIdentifierPart}]";
  private static final String JAVA_IDENTIFIER_START =
      "[\\p{javaJavaIdentifierStart}]";
  private static final String IDENTIFIER_CHARS_RE =
      "(?:" + JAVA_IDENTIFIER_START + JAVA_IDENTIFIER_PART + "*)";

  @VisibleForTesting
  static final String KEYWORD_OR_BOOLEAN_OR_NULL;

  private static final Comparator<String> AFTER_THEIR_PREFIXES =
      new Comparator<String>() {

        @Override
        public int compare(String a, String b) {
          int lengthDelta = a.length() - b.length();
          if (lengthDelta < 0) {
            if (b.startsWith(a)) {
              return 1;
            }
          } else if (lengthDelta != 0 && a.startsWith(b)) {
            return -1;
          }
          return a.compareTo(b);
        }

      };

  static {
    List<String> words = Lists.newArrayList(TokenStrings.RESERVED);

    // Sort "finally" before "final" and "instanceof" before "in" so that
    // matches that aren't right anchored match the longest keyword.
    Collections.sort(words, AFTER_THEIR_PREFIXES);

    KEYWORD_OR_BOOLEAN_OR_NULL = Joiner.on('|').join(words);
  }

  private static final ImmutableSortedSet<String> puncStrs =
      ImmutableSortedSet.<String>naturalOrder()
      .addAll(TokenStrings.PUNCTUATION)
      .build();

  /**
   * The punctuation strings that appear in the grammar that have the given
   * string as a prefix.
   * <p>
   * Since the parser is scannerless, we don't first break input into maximal
   * tokens, which means that we might find a token {@code >} in {@code a >> b}
   * if the parser checks for greater than before shift operators.
   */
  public static final ImmutableCollection<String> punctuationSuffixes(
      String puncStr) {
    StringBuilder nextSameLength = new StringBuilder(puncStr);
    int lastIndex = nextSameLength.length() - 1;
    char lastChar = nextSameLength.charAt(lastIndex);
    Preconditions.checkState(lastChar < Character.MAX_VALUE);
    nextSameLength.setCharAt(lastIndex, (char) (lastChar + 1));

    return puncStrs.subSet(
        puncStr,
        false,  // false -> exclusive
        nextSameLength.toString(),
        false  // false -> exclusive
        );
  }

  /** Section 3.8 */
  public static final PatternMatch IDENTIFIER = new PatternMatch(
      // Identifier:
      //     IdentifierChars but not a Keyword or BooleanLiteral or NullLiteral
      "(?!(?:" + KEYWORD_OR_BOOLEAN_OR_NULL + ")"
      + "(?!" + JAVA_IDENTIFIER_PART + "))"
      + IDENTIFIER_CHARS_RE,
      "ident");

  /** Section 3.8 */
  public static final PatternMatch IDENTIFIER_CHARS = new PatternMatch(
      IDENTIFIER_CHARS_RE,
      "ident");

  /** 3.10.5 */
  public static final PatternMatch STRING_LITERAL = new PatternMatch(
      "\"(?:'|" + CHAR_NO_QUOTES + ")*\"",
      "\"...\"");

}
