package com.mikesamuel.cil.ast;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;

import org.junit.Test;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import com.mikesamuel.cil.parser.Input;

@SuppressWarnings("javadoc")
public final class SourceParsingSanityTest extends AbstractParSerTestCase {

  @Test
  public void testBaseNode() throws IOException {
    sanityCheckSourceFile("/com/mikesamuel/cil/ast/BaseNode.java");
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
