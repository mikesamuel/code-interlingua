package com.mikesamuel.cil.template;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.util.Map;
import java.util.Set;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import javax.annotation.Nullable;

import org.junit.runner.RunWith;
import org.junit.runners.AllTests;

import static com.google.common.base.Charsets.UTF_8;

import com.google.common.base.Objects;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.io.Files;
import com.google.common.truth.Truth;
import com.mikesamuel.cil.ast.CompilationUnitNode;
import com.mikesamuel.cil.ast.passes.PassTestHelpers;
import com.mikesamuel.cil.expr.DataBundle;
import com.mikesamuel.cil.parser.Input;
import com.mikesamuel.cil.parser.Unparse;
import com.mikesamuel.cil.parser.Unparse.UnparseVerificationException;

import junit.framework.TestCase;
import junit.framework.TestSuite;

@SuppressWarnings("javadoc")
@RunWith(AllTests.class)
public final class TemplateEvaluationEndToEndTest extends TestCase {

  public static TestSuite suite() throws IOException {
    ImmutableList<File> testRoots;
    TestSuite suite = new TestSuite();
    for (String prop : new String[] {
        "basedir",  // Set by mvn
        "user.dir",
        }) {
      String path = System.getProperty(prop);
      if (path != null) {
        File baseDir = new File(path);
        File testsDir = baseDir;
        for (String sub
            : new String[] { "src", "test", "resources", "template-tests" }) {
          testsDir = new File(testsDir, sub);
        }
        File[] testsDirFiles = testsDir.listFiles();
        if (testsDirFiles == null) {
          fail("No such dir " + testsDir);
        } else {
          ImmutableList.Builder<File> testDirs = ImmutableList.builder();
          for (File f : testsDirFiles) {
            if (f.isDirectory()) {
              testDirs.add(f);
            }
          }
          testRoots = testDirs.build();
          assertFalse(testsDir.getPath(), testRoots.isEmpty());

          for (File testRoot : testRoots) {
            EndToEndTestCase.addToTestSuite(testRoot, suite);
          }
          return suite;
        }
      }
    }
    fail("Missing test root");
    return null;
  }

  private static class EndToEndTestCase extends TestCase {

    static void addToTestSuite(File testRoot, TestSuite suite)
    throws IOException {
      final Set<File> javaFiles = Sets.newTreeSet();
      final Map<String, File> prefixToInput = Maps.newTreeMap();
      final Map<String, File> prefixToOutput = Maps.newTreeMap();
      final Map<String, File> prefixToLog = Maps.newTreeMap();
      for (File f : testRoot.listFiles()) {
        if (f.isFile()) {
          String name = f.getName();
          int dot = name.lastIndexOf('.');
          if (dot >= 0) {
            switch (name.substring(dot + 1)) {
              case "java":
                javaFiles.add(f);
                break;
              case "json":
                assertNull(prefixToInput.put(name.substring(0, dot), f));
                break;
              case "out":
                assertNull(prefixToOutput.put(name.substring(0, dot), f));
                break;
              case "log":
                assertNull(prefixToLog.put(name.substring(0, dot), f));
                break;
            }
          }
        }
      }

      assertEquals(
          "Need input files for all output files and vice-versa",
          prefixToInput.keySet(), prefixToOutput.keySet());
      {
        Set<String> logPrefixes = Sets.newTreeSet(prefixToLog.keySet());
        logPrefixes.removeAll(prefixToInput.keySet());
        if (!logPrefixes.isEmpty()) {
          fail("Log files without inputs: " + logPrefixes);
        }
      }

      assertFalse(testRoot.getPath(), javaFiles.isEmpty());

      Logger logger = Logger.getAnonymousLogger();
      TemplateBundle bundle = new TemplateBundle(logger);
      for (File javaFile : javaFiles) {
        bundle.addCompilationUnit(Input.builder()
            .source(javaFile.getName())
            .code(Files.toString(javaFile, UTF_8))
            .allowNonStandardProductions(true)
            .build());
      }

      // During test running we redirect all logging to a per-prefix output file
      logger.setUseParentHandlers(false);
      for (String prefix : prefixToInput.keySet()) {
        File input = prefixToInput.get(prefix);
        File output = prefixToOutput.get(prefix);
        @Nullable File log = prefixToLog.get(prefix);

        String name = testRoot.getName() + "$" + prefix;
        suite.addTest(new EndToEndTestCase(
            name, prefix, logger, bundle, input, output, log));
      }
    }

    final String prefix;
    final Logger logger;
    final TemplateBundle bundle;
    final File input;
    final File expectedOutput;
    final File expectedLog;

    EndToEndTestCase(
        String name,
        String prefix,
        Logger logger,
        TemplateBundle bundle,
        File input,
        File output,
        File log) {
      super(name);
      this.prefix = prefix;
      this.logger = logger;
      this.bundle = bundle;
      this.input = input;
      this.expectedOutput = output;
      this.expectedLog = log;
    }

    @Override
    public void runTest()
    throws IOException, Unparse.UnparseVerificationException {
      DataBundle inputObj = DataBundle.fromJsonFile(
          input.getPath(), Files.asCharSource(input, UTF_8));

      ImmutableList<CompilationUnitNode> got;

      File logOutputFile = File.createTempFile(getName(), ".log");
      try (Writer logOut = Files.asCharSink(logOutputFile, UTF_8)
          .openBufferedStream()) {
        Handler logHandler = new Handler() {
          SimpleFormatter formatter = new SimpleFormatter();

          @Override
          public void publish(LogRecord record) {
            try {
              logOut.write(formatter.format(record));
              logOut.write('\n');
            } catch (IOException ex) {
              ex.printStackTrace();
            }
          }

          @Override
          public void flush() {
            try {
              logOut.flush();
            } catch (IOException ex) {
              ex.printStackTrace();
            }
          }

          @Override
          public void close() {
            try {
              logOut.close();
            } catch (IOException ex) {
              ex.printStackTrace();
            }
          }

        };

        logger.addHandler(logHandler);
        got = bundle.apply(inputObj);
        logger.removeHandler(logHandler);
      }

      File gotFile = writeToTempFile(prefix, got);

      String gotFileContent = null;
      String expectedOutputContent = null;
      Unparse.UnparseVerificationException canonFailure = null;
      // Make a best effort to read and canonicalize the Java compilation units
      try {
        gotFileContent = canonCompilationUnit(
            Files.toString(expectedOutput, UTF_8));
        expectedOutputContent = canonCompilationUnit(
            Files.toString(gotFile, UTF_8));
      } catch (Unparse.UnparseVerificationException ex) {
        canonFailure = ex;
      }

      String expectedLogContent =
          expectedLog != null ? Files.toString(expectedLog, UTF_8) : null;
      String logContent = Files.toString(logOutputFile, UTF_8);

      // Dump some state if it looks like there's a problem.
      if (canonFailure != null
          || !Objects.equal(gotFileContent, expectedOutputContent)
          || (expectedLogContent != null
              && !expectedLogContent.equals(logContent))) {
        System.err.println("\n\n" + getName());
        System.err.println("\tOutput: " + gotFile);
        System.err.println("\tLog: " + logOutputFile);
        if (expectedLog == null) {
          System.err.println(logContent);
        }
      }

      if (expectedLog != null) {
        Truth.assertWithMessage(prefix + " log")
            .that(logContent)
            .isEqualTo(expectedLogContent);
      }

      if (canonFailure != null) {
        throw canonFailure;
      }

      Truth.assertWithMessage(prefix + " logged to " + logOutputFile)
          .that(gotFileContent)
          .isEqualTo(expectedOutputContent);
    }

    private static String canonCompilationUnit(String code)
    throws Unparse.UnparseVerificationException {
       Optional<String> canonStr = PassTestHelpers
           .normalizeCompilationUnitSource(new String[][] { { code } });
       return canonStr.isPresent() ? canonStr.get() : code;
    }

    private static File writeToTempFile(
        String prefix, Iterable<? extends CompilationUnitNode> cus)
    throws IOException, UnparseVerificationException {
      File f = File.createTempFile(prefix, ".out");
      try (Writer out = Files.asCharSink(f, UTF_8).openBufferedStream()) {
        boolean first = true;
        for (CompilationUnitNode cu : cus) {
          if (first) {
            first = false;
          } else {
            out.write(
                "\n\n"
                + "////////////////////////////////////////"
                + "////////////////////////////////////////"
                + "\n\n");
          }
          out.write(PassTestHelpers.serializeNodes(ImmutableList.of(cu), null));
        }
      }
      return f;
    }
  }
}