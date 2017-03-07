package com.mikesamuel.cil.template;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.util.Map;
import java.util.Set;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

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
import com.mikesamuel.cil.util.MaxLogLevel;

import junit.framework.TestCase;
import junit.framework.TestSuite;

@SuppressWarnings("javadoc")
@RunWith(AllTests.class)
public final class TemplateEvaluationEndToEndTest extends TestCase {

  public static TestSuite suite() throws IOException {
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
          boolean foundOne = false;
          for (File f : testsDirFiles) {
            if (f.isDirectory()) {
              EndToEndTestCase.addToTestSuite(f, suite);
              foundOne = true;
            }
          }
          assertTrue(testsDir.getPath(), foundOne);
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

      assertFalse("No inputs", prefixToInput.keySet().isEmpty());
      assertEquals(
          "Need input files for all output files and vice-versa",
          prefixToInput.keySet(), prefixToOutput.keySet());
      {
        Set<String> logPrefixes = Sets.newTreeSet(prefixToLog.keySet());
        logPrefixes.removeAll(prefixToInput.keySet());
        assertTrue(
            "Log files without inputs: " + logPrefixes,
            logPrefixes.isEmpty());
      }

      assertFalse(testRoot.getPath(), javaFiles.isEmpty());

      MaxLogLevel maxLogLevel = new MaxLogLevel();

      Logger logger = Logger.getAnonymousLogger();
      logger.addHandler(maxLogLevel);
      TemplateBundle bundle = new TemplateBundle(logger);
      for (File javaFile : javaFiles) {
        Input inp = Input.builder()
            .source(testRoot.getName() + "/" + javaFile.getName())
            .code(Files.toString(javaFile, UTF_8))
            .allowNonStandardProductions(true)
            .build();
        bundle.addCompilationUnit(inp);
      }
      logger.removeHandler(maxLogLevel);
      assertFalse("fatal errors during parse", maxLogLevel.hasFatalErrors());

      for (String prefix : prefixToInput.keySet()) {
        File input = prefixToInput.get(prefix);
        File output = prefixToOutput.get(prefix);
        @Nullable File log = prefixToLog.get(prefix);

        String name = testRoot.getName() + "$" + prefix;
        suite.addTest(new EndToEndTestCase(
            name, prefix, bundle, input, output, log));
      }
    }

    final String prefix;
    final TemplateBundle bundle;
    final File input;
    final File expectedOutput;
    final File expectedLog;

    EndToEndTestCase(
        String name,
        String prefix,
        TemplateBundle bundle,
        File input,
        File output,
        File log) {
      super(name);
      this.prefix = prefix;
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
      MaxLogLevel maxLogLevel = new MaxLogLevel();
      try (Writer logOut = Files.asCharSink(logOutputFile, UTF_8)
          .openBufferedStream()) {
        Handler logHandler = new Handler() {
          Formatter formatter = new Formatter() {

            @Override
            public String format(LogRecord record) {
              StringBuilder sb = new StringBuilder();
              // Output in format
              //   Class:method:
              //   LEVEL: message
              // so that we don't complicate logging output with time-stamps
              // and stack trace frames which change frequently from build to
              // build and make it hard to compare log output against expected
              // log output.
              String sourceName = record.getSourceClassName();
              String methodName = record.getSourceMethodName();
              Level level = record.getLevel();
              String message = record.getMessage();
              if (sourceName != null || methodName != null) {
                sb.append("@ ");
                if (sourceName != null) {
                  sb.append(sourceName);
                }
                if (methodName != null) {
                  sb.append('.').append(methodName);
                }
                sb.append('\n');
              }
              sb.append(level).append(": ").append(message);
              return sb.toString();
            }

          };

          @Override
          public void publish(LogRecord record) {
            if (record.getLevel().intValue() >= Level.SEVERE.intValue()) {
              try {
                logOut.write(formatter.format(record));
                logOut.write('\n');
              } catch (IOException ex) {
                ex.printStackTrace();
              }
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
        logHandler.setLevel(Level.SEVERE);

        Logger logger = bundle.getLogger();
        // During test running we redirect all logging to an output file
        logger.setUseParentHandlers(false);
        logger.addHandler(logHandler);
        logger.addHandler(maxLogLevel);
        got = bundle.apply(inputObj);
        logger.removeHandler(logHandler);
        logger.removeHandler(maxLogLevel);
      }
      boolean hasFatalErrors = maxLogLevel.hasFatalErrors();

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
              ? !expectedLogContent.equals(logContent)
              : hasFatalErrors)) {
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
      } else {
        assertFalse(
            "fatal errors during template application",
            hasFatalErrors);
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