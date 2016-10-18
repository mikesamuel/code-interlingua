package com.mikesamuel.cil.ptree;

import com.mikesamuel.cil.parser.ParSer;

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
  public static final ParSer CHARACTER_LITERAL = new PatternMatch(
      "'(?:\"|" + CHAR_NO_QUOTES + ")'", "'.'");

  /** 3.10.2 */
  public static final ParSer FLOATING_POINT_LITERAL;
  /** 3.10.1 */
  public static final ParSer INTEGER_LITERAL;

  static {
    String underscores = "_+";

    // Digit: one of
    //    0 1 2 3 4 5 6 7 8 9
    String digit = "[0-9]";
    String nonZeroDigit = "[1-9]";

    // DigitOrUnderscore:
    //    Digit
    //    _
    String digitOrUnderscore = "[0-9A-Fa-f_]";

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
    String digits = digit
        + "(?:" + digitsAndUnderscoresOpt + digit + ")?";
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
    String binaryDigits = binaryDigit
        + "(?:" + binaryDigitsAndUnderscoresOpt + binaryDigit + ")?";

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
    String hexDigits = hexDigit
        + "(?:" + hexDigitsAndUnderscoresOpt + hexDigit + ")?";
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
    String octalDigits = octalDigit
        + "(?:" + octalDigitsAndUnderscoresOpt + octalDigit + ")?";

    // OctalNumeral:
    //     0 OctalDigits
    //     0 Underscores OctalDigits
    String octalNumeral = "0" + underscores + "?" + octalDigits;

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

    FLOATING_POINT_LITERAL = new PatternMatch(floatingPointLiteral, "0.123");

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

    INTEGER_LITERAL = new PatternMatch(integerLiteral, "123");
  }

  private static final String JAVA_IDENTIFIER_PART =
      "[\\p{javaJavaIdentifierPart}]";
  private static final String JAVA_IDENTIFIER_START =
      "[\\p{javaJavaIdentifierStart}]";
  private static final String IDENTIFIER_CHARS_RE =
      "(?:" + JAVA_IDENTIFIER_START + JAVA_IDENTIFIER_PART + "*)";

  private static final String KEYWORD_OR_BOOLEAN_OR_NULL =
      ""
      + "abstract|continue|for|new|switch"
      + "|assert|default|if|package|synchronized"
      + "|boolean|do(?:uble)?|goto|private|this"
      + "|break|implements|protected|throws?"
      + "|byte|else|import|public"
      + "|case|enum|instanceof|return|transient"
      + "|catch|extends|int|short|try"
      + "|char|final(?:ly)?|interface|static|void"
      + "|class|long|strictfp|volatile"
      + "|const|float|native|super|while";

  /** Section 3.8 */
  public static final ParSer IDENTIFIER = new PatternMatch(
      // Identifier:
      //     IdentifierChars but not a Keyword or BooleanLiteral or NullLiteral
      "(?!=" + KEYWORD_OR_BOOLEAN_OR_NULL + "(?!" + JAVA_IDENTIFIER_PART + "))"
      + IDENTIFIER_CHARS_RE,
      "ident");

  /** Section 3.8 */
  public static final ParSer IDENTIFIER_CHARS = new PatternMatch(
      IDENTIFIER_CHARS_RE,
      "ident");

  /** 3.10.5 */
  public static final ParSer STRING_LITERAL = new PatternMatch(
      "\"(?:" + "" + ")*\"",
      "\"...\"");
}
