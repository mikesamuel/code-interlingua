package com.mikesamuel.cil.ptree;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Lists;
import com.mikesamuel.cil.ast.TokenStrings;
import com.mikesamuel.cil.event.Event;
import com.mikesamuel.cil.parser.SList;
import com.mikesamuel.cil.parser.Ignorables;
import com.mikesamuel.cil.parser.LeftRecursion;
import com.mikesamuel.cil.parser.MatchErrorReceiver;
import com.mikesamuel.cil.parser.MatchState;
import com.mikesamuel.cil.parser.ParSer;
import com.mikesamuel.cil.parser.ParseErrorReceiver;
import com.mikesamuel.cil.parser.ParseResult;
import com.mikesamuel.cil.parser.ParseState;
import com.mikesamuel.cil.parser.SerialErrorReceiver;
import com.mikesamuel.cil.parser.SerialState;

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

  /**
   * Looks back to find JavaDoc comments on the input.
   */
  @SuppressWarnings("synthetic-access")
  public static final ParSer JAVA_DOC_COMMENT = new JavaDocLookback();

  /**
   * True if the given content is a valid Java block comment.
   *
   * @param s post <code>&bsol;uXXXX</code> decoding.
   */
  public static boolean isBlockComment(String s) {
    return s.startsWith("/*") && s.indexOf("*/", 2) == s.length() - 2;
  }

  private static final class JavaDocLookback extends ParSer {

    @Override
    public ParseResult parse(
        ParseState state, LeftRecursion lr, ParseErrorReceiver err) {
      // Lookback on the queue for the last token parsed.
      // The end of that token to the current index are ignorable tokens.
      // Scan those for Javadoc comments.

      int lastTokenEnd = 0;
      for (SList<Event> c = state.output; c != null; c = c.prev) {
        Event e = c.x;
        int nc = e.nCharsConsumed();
        if (nc != 0) {
          lastTokenEnd = e.getContentIndex() + nc;
          break;
        }
      }

      if (lastTokenEnd < state.index) {
        // We can't scan left from state.index because we need to distinguish
        // cases like
        //      //  /** This is a line comment not a javadoc comment */
        //      /*  /** This is a non-javadoc comment */
        JavaDocCommentRecognizer r = new JavaDocCommentRecognizer();
        CharSequence content = state.input.content();
        int scanEnd = Ignorables.scanPastIgnorablesFrom(
            content, lastTokenEnd, r);
        Preconditions.checkState(scanEnd == state.index);
        if (r.rightmostJavadocCommentContent != null) {
          int nLeadingWhitespace = 0;
          for (int i = r.rightmostJavadocCommentIndex; --i >= lastTokenEnd;) {
            char ch = content.charAt(i);
            if (ch == ' ' || ch == '\t') {
              ++nLeadingWhitespace;
            } else {
              break;
            }
          }
          return ParseResult.success(
              state.appendOutput(
                  Event.ignorable(
                      stripLeadingWhitespace(
                          r.rightmostJavadocCommentContent,
                          nLeadingWhitespace),
                      r.rightmostJavadocCommentIndex)),
              // We looked back, we did not write back.
              ParseResult.NO_WRITE_BACK_RESTRICTION,
              ImmutableList.of());
        }
      }

      return ParseResult.failure();
    }

    @Override
    public Optional<SerialState> unparse(
        SerialState state, SerialErrorReceiver err) {
      // Don't require the ignorable, but step past it if it's present.
      SerialState afterComment = state;
      if (state.index < state.structure.size()) {
        Event e = state.structure.get(state.index);
        if (e.getKind() == Event.Kind.IGNORABLE) {
          afterComment = state.advanceWithCopy();
        }
      }

      return Optional.of(afterComment);
    }

    @Override
    public Optional<MatchState> match(
        MatchState state, MatchErrorReceiver err) {
      MatchState afterComment = state;
      if (state.index < state.events.size()) {
        Event e = state.events.get(state.index);
        if (e.getKind() == Event.Kind.IGNORABLE) {
          afterComment = state.advance();
        }
      }
      return Optional.of(afterComment);
    }

    static final class JavaDocCommentRecognizer
    implements Ignorables.CommentReceiver {
      int rightmostJavadocCommentIndex;
      String rightmostJavadocCommentContent;

      @Override
      public void comment(int startIndex, String content) {
        if (content.startsWith("/**")) {
          Preconditions.checkState(
              rightmostJavadocCommentContent == null
              || startIndex > rightmostJavadocCommentIndex);
          rightmostJavadocCommentContent = content;
          rightmostJavadocCommentIndex = startIndex;
        }
      }
    }

    private static String stripLeadingWhitespace(
        String multilineToken, int maxSpaceToStrip) {
      StringBuilder sb = null;
      int n = multilineToken.length();
      int pos = 0;
      for (int i = 0; i < n; ++i) {
        char ch = multilineToken.charAt(i);
        if (ch == '\n' || ch == '\r') {
          if (ch == '\r' && i + 1 < n && '\n' == multilineToken.charAt(i + 1)) {
            ++i;
          }
          if (sb == null) { sb = new StringBuilder(); }
          sb.append(multilineToken, pos, i + 1);
          pos = i + 1;
          for (int j = 0; j < maxSpaceToStrip && pos < n; ++j, ++pos) {
            ch = multilineToken.charAt(pos);
            if (ch != ' ' && ch != '\t') {
              break;
            }
          }
        }
      }
      return pos == 0
          ? multilineToken
          : Preconditions.checkNotNull(sb).append(
              multilineToken, pos, n).toString();
    }
  }
}
