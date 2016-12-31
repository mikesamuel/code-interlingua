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
  public void testThemAll() throws Exception {
    long totalTime = 0;
    int nFiles = 0;
    for (String line : Resources.readLines(
            Resources.getResource(getClass(), "/all-sources.txt"),
            Charsets.UTF_8)) {
      setUp();
      File source = new File(line.trim());
      long t0 = System.nanoTime();
      boolean ok = false;
      try {
        parseSanityCheck(
            CompilationUnitNode.Variant
            .PackageDeclarationImportDeclarationTypeDeclaration,
            Input.builder()
            .source(source.getPath())
            .code(Files.asCharSource(source, Charsets.UTF_8))
            .build(),
            Fuzz.IMPLIED_TOKENS);
        ok = true;
      } finally {
        long t1 = System.nanoTime();
        totalTime += t1 - t0;
        ++nFiles;
        if (!ok) {
          System.err.println("Failed parsing " + source
              + " after " + ((t1 - t0) / 1e6) + " ms");
          System.err.flush();
        }
      }
    }
    System.err.println("Parsed " + nFiles + " in " + (totalTime / 1e6) + " ms");
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
        Input.builder()
            .source(sourceFileRelPath)
            .code(Resources.asCharSource(resUrl, Charsets.UTF_8))
            .build(),
        Fuzz.IMPLIED_TOKENS);
  }
}

