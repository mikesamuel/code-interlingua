package com.mikesamuel.cil.ast.j8;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

import com.google.common.base.Charsets;
import com.google.common.collect.Lists;
import com.google.common.io.Files;
import com.google.common.io.Resources;
import com.mikesamuel.cil.ast.AbstractParSerTestCase;
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
    sanityCheckSourceFile("/com/mikesamuel/cil/ast/j8/Tokens.java");
  }

  @Test
  public void testThemAll() throws Throwable {
    long totalTime = 0;
    int nFiles = 0;
    List<File> sources = Lists.newArrayList();
    for (String line : Resources.readLines(
        Resources.getResource(getClass(), "/all-sources.txt"),
        Charsets.UTF_8)) {
      File source = new File(line.trim());
      sources.add(source);
    }

    if ("true".equalsIgnoreCase(System.getenv("TRAVIS"))) {
      // Don't cause Travis test running to time out.
      Collections.shuffle(sources);
      sources.subList(50, sources.size()).clear();
    }

    List<Throwable> failures = Lists.newArrayList();

    for (File source : sources) {
      setUp();
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
      } catch (StackOverflowError e) {
        System.err.println(source);
        e.printStackTrace();
        System.err.flush();
        failures.add(e);
      } catch (Exception ex) {
        failures.add(ex);
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

    if (!failures.isEmpty()) {
      throw failures.get(0);
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

