package com.mikesamuel.cil.format.java;

import org.junit.Test;

import com.mikesamuel.cil.format.java.Java8TokenClassifier;

import junit.framework.TestCase;

@SuppressWarnings("javadoc")
public class Java8TokenClassifierTest extends TestCase {

  @Test
  public static void testClassifyPunctuation() {
    assertEquals(
        Java8TokenClassifier.Classification.PUNCTUATION,
        Java8TokenClassifier.classify("."));
    assertEquals(
        Java8TokenClassifier.Classification.PUNCTUATION,
        Java8TokenClassifier.classify("..."));
    assertEquals(
        Java8TokenClassifier.Classification.PUNCTUATION,
        Java8TokenClassifier.classify("-"));
    assertEquals(
        Java8TokenClassifier.Classification.PUNCTUATION,
        Java8TokenClassifier.classify("--"));
    assertEquals(
        Java8TokenClassifier.Classification.PUNCTUATION,
        Java8TokenClassifier.classify("+"));
    assertEquals(
        Java8TokenClassifier.Classification.PUNCTUATION,
        Java8TokenClassifier.classify("++"));
  }

  @Test
  public static void testClassifyNumber() {
    assertEquals(
        Java8TokenClassifier.Classification.NUMBER_LITERAL,
        Java8TokenClassifier.classify(".5"));
    assertEquals(
        Java8TokenClassifier.Classification.NUMBER_LITERAL,
        Java8TokenClassifier.classify("0xa"));
    assertEquals(
        Java8TokenClassifier.Classification.NUMBER_LITERAL,
        Java8TokenClassifier.classify("0.5"));
    assertEquals(
        Java8TokenClassifier.Classification.NUMBER_LITERAL,
        Java8TokenClassifier.classify("0.5e1"));
    assertEquals(
        Java8TokenClassifier.Classification.NUMBER_LITERAL,
        Java8TokenClassifier.classify("-0.5D"));
    assertEquals(
        Java8TokenClassifier.Classification.NUMBER_LITERAL,
        Java8TokenClassifier.classify("+1"));
    assertEquals(
        Java8TokenClassifier.Classification.NUMBER_LITERAL,
        Java8TokenClassifier.classify("4.2"));
  }

  @Test
  public static void testClassifyIdentifierChars() {
    assertEquals(
        Java8TokenClassifier.Classification.IDENTIFIER_CHARS,
        Java8TokenClassifier.classify("a"));
    assertEquals(
        Java8TokenClassifier.Classification.IDENTIFIER_CHARS,
        Java8TokenClassifier.classify("A"));
    assertEquals(
        Java8TokenClassifier.Classification.IDENTIFIER_CHARS,
        Java8TokenClassifier.classify("_"));
    assertEquals(
        Java8TokenClassifier.Classification.IDENTIFIER_CHARS,
        Java8TokenClassifier.classify("aaa"));
    assertEquals(
        Java8TokenClassifier.Classification.IDENTIFIER_CHARS,
        Java8TokenClassifier.classify("assert"));
  }
}
