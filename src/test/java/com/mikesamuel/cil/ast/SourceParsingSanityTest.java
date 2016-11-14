package com.mikesamuel.cil.ast;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;

import org.junit.Test;

import com.google.common.base.Charsets;
import com.google.common.io.Files;
import com.google.common.io.Resources;
import com.mikesamuel.cil.parser.Input;

@SuppressWarnings("javadoc")
public final class SourceParsingSanityTest extends AbstractParSerTestCase {

  @Test
  public void testBaseNode() throws IOException {
    sanityCheckSourceFile("/com/mikesamuel/cil/ast/BaseNode.java");
  }

  @Test
  public void testLineStarts() throws IOException {
    sanityCheckSourceFile("/com/mikesamuel/cil/parser/LineStarts.java");
  }

  @Test
  public void testReference() throws IOException {
    sanityCheckSourceFile("/com/mikesamuel/cil/ptree/Reference.java");
  }

  @Test
  public void testTokens() throws IOException {
    sanityCheckSourceFile("/com/mikesamuel/cil/ptree/Tokens.java");
  }

  @Test
  public void testMatchEvent() throws IOException {
    sanityCheckSourceFile("/com/mikesamuel/cil/ast/MatchEvent.java");
  }

  @Test
  public void testThemAll() throws Exception {
    for (String line : Resources.readLines(
            Resources.getResource(getClass(), "/all-sources.txt"),
            Charsets.UTF_8)) {
      File source = new File(line.trim());
      System.err.println("Parsing " + source);
      long t0 = System.nanoTime();
      parseSanityCheck(
          CompilationUnitNode.Variant
          .PackageDeclarationImportDeclarationTypeDeclaration,
          new Input(
              source.getPath(),
              Files.asCharSource(source, Charsets.UTF_8)));
      long t1 = System.nanoTime();
      System.err.println(
          "Parsed " + source + " in "
          + String.format("%.2f", (t1 - t0) / 1.e6) + " ms");
      System.err.flush();
    }
  }

  /**
   * Our maven config treats source files as test resources.
   */
  void sanityCheckSourceFile(String sourceFileRelPath) throws IOException {
    URL resUrl = Resources.getResource(getClass(), sourceFileRelPath);
    if (resUrl == null) {
      throw new FileNotFoundException(sourceFileRelPath);
    }

    parseSanityCheck(
        CompilationUnitNode.Variant
        .PackageDeclarationImportDeclarationTypeDeclaration,
        new Input(
            sourceFileRelPath,
            Resources.asCharSource(resUrl, Charsets.UTF_8)));
  }
}

