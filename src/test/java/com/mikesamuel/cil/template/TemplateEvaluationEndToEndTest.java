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

import org.junit.Before;
import org.junit.Test;

import static com.google.common.base.Charsets.UTF_8;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.io.Files;
import com.google.common.truth.Truth;
import com.mikesamuel.cil.ast.CompilationUnitNode;
import com.mikesamuel.cil.ast.passes.PassTestHelpers;
import com.mikesamuel.cil.expr.DataBundle;
import com.mikesamuel.cil.parser.Input;
import com.mikesamuel.cil.parser.Unparse.UnparseVerificationException;

import junit.framework.TestCase;

@SuppressWarnings("javadoc")
public final class TemplateEvaluationEndToEndTest extends TestCase {

  private ImmutableList<File> testRoots;

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    if (testRoots == null) {
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
            return;
          }
          ImmutableList.Builder<File> testDirs = ImmutableList.builder();
          for (File f : testsDirFiles) {
            if (f.isDirectory()) {
              testDirs.add(f);
            }
          }
          this.testRoots = testDirs.build();
          assertFalse(testsDir.getPath(), testRoots.isEmpty());
          return;
        }
      }
      fail("Missing test root");
    }
  }

  @Test
  public void testEndToEnd() throws Exception {
    if (true) { return; }  // TODO: Implement TemplateBundle processing
    for (File testRoot : testRoots) {
      Set<File> javaFiles = Sets.newTreeSet();
      Map<String, File> prefixToInput = Maps.newTreeMap();
      Map<String, File> prefixToOutput = Maps.newTreeMap();
      Map<String, File> prefixToLog = Maps.newTreeMap();

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

      assertFalse(javaFiles.isEmpty());

      Logger logger = Logger.getAnonymousLogger();

      TemplateBundle bundle = new TemplateBundle(logger);
      for (File javaFile : javaFiles) {
        bundle.addCompilationUnit(Input.builder()
            .source(javaFile.getName())
            .code(Files.toString(javaFile, UTF_8))
            .allowNonStandardProductions(true)
            .build());
      }

      // Below we redirect all logging to a per-prefix output file.
      logger.setUseParentHandlers(false);

      for (String prefix : prefixToInput.keySet()) {
        File input = prefixToInput.get(prefix);
        File output = prefixToOutput.get(prefix);
        @Nullable File log = prefixToLog.get(prefix);

        File logOutputFile = File.createTempFile(prefix, ".log");
        DataBundle inputObj = DataBundle.fromJsonFile(
            input.getPath(), Files.asCharSource(input, UTF_8));

        ImmutableList<CompilationUnitNode> got;

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

        Truth.assertWithMessage(prefix + " logged at " + logOutputFile)
            .that(canonCompilationUnit(Files.toString(gotFile, UTF_8)))
            .isEqualTo(canonCompilationUnit(Files.toString(output, UTF_8)));
        if (log != null) {
          Truth.assertWithMessage(prefix + " log")
              .that(Files.toString(logOutputFile, UTF_8))
              .isEqualTo(Files.toString(log, UTF_8));
        }
      }
    }

  }

  private static String canonCompilationUnit(String code)
  throws UnparseVerificationException {
    return PassTestHelpers.normalizeCompilationUnitSource(
        new String[][] { { code } });
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