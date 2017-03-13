package com.mikesamuel.cil.ast.passes;

import java.util.Iterator;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import javax.annotation.Nullable;

import org.junit.Assert;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.mikesamuel.cil.ast.BaseNode;
import com.mikesamuel.cil.ast.NodeI;
import com.mikesamuel.cil.ast.Trees;
import com.mikesamuel.cil.ast.Trees.Decorator;
import com.mikesamuel.cil.ast.j8.J8NodeType;
import com.mikesamuel.cil.ast.j8.traits.FileNode;
import com.mikesamuel.cil.event.Event;
import com.mikesamuel.cil.format.FormattedSource;
import com.mikesamuel.cil.parser.Input;
import com.mikesamuel.cil.parser.LeftRecursion;
import com.mikesamuel.cil.parser.ParseErrorReceiver;
import com.mikesamuel.cil.parser.ParseResult;
import com.mikesamuel.cil.parser.ParseState;
import com.mikesamuel.cil.parser.SList;
import com.mikesamuel.cil.parser.SerialErrorReceiver;
import com.mikesamuel.cil.parser.SerialState;
import com.mikesamuel.cil.parser.Unparse;
import com.mikesamuel.cil.parser.Unparse.UnparseVerificationException;
import com.mikesamuel.cil.parser.Unparse.Verified;
import com.mikesamuel.cil.ptree.PTree;

/** Utilities for testing AST processing passes. */
public final class PassTestHelpers {

  /**
   * Parses inputs to compilation units.
   *
   * @param linesPerFile {@code linesPerFile[f][i]} is line i (zero-indexed)
   *   in the f-th file.
   */
  public static ImmutableList<FileNode> parseCompilationUnits(
      String[]... linesPerFile) {
    return maybeParseCompilationUnits(linesPerFile).get();
  }

  /**
   * Parses inputs to compilation units.
   *
   * @param linesPerFile {@code linesPerFile[f][i]} is line i (zero-indexed)
   *   in the f-th file.
   * @return absent if there was a parse failure.
   */
  public static Optional<ImmutableList<FileNode>> maybeParseCompilationUnits(
      String[]... linesPerFile) {
    ImmutableList.Builder<FileNode> b = ImmutableList.builder();
    for (String[] lines : linesPerFile) {
      Input.Builder inputBuilder = Input.builder()
          .code(Joiner.on('\n').join(lines));
      if (lines.length != 0) {
        inputBuilder.source(lines[0]);
      }
      Input inp = inputBuilder.build();
      ParseResult result =
          PTree.complete(J8NodeType.CompilationUnit).getParSer()
          .parse(new ParseState(inp), new LeftRecursion(),
              ParseErrorReceiver.DEV_NULL);
      if (ParseResult.Synopsis.SUCCESS != result.synopsis) {
        return Optional.absent();
      }
      ParseState afterParse = result.next();
      b.add((FileNode)
          Trees.forGrammar(J8NodeType.GRAMMAR)
          .of(inp, afterParse.output));
    }
    return Optional.of(b.build());
  }

  /**
   * Parses inputs to compilation units, applies the pass runner to those inputs
   * to process them, requires log messages to match the expected errors,
   * require the processed and decorated inputs to match the expected outputs.
   */
  public static void assertAnnotatedOutput(
      PassRunner passRunner,
      String[][] expectedLines,
      String[][] inputLines,
      Decorator decorator,
      String... expectedErrors)
  throws UnparseVerificationException {
    ImmutableList<FileNode> files = expectErrors(
        new LoggableOperation<ImmutableList<FileNode>>() {
          @Override
          public ImmutableList<FileNode> run(Logger logger) {
            return passRunner.runPasses(
                logger,
                parseCompilationUnits(inputLines));
          }
        },
        expectedErrors);

    String got = serializeNodes(files, decorator);

    String want = joinExpectedLines(expectedLines);

    Assert.assertEquals(want, got);
  }

  static String joinExpectedLines(String[][] expectedLines) {
    StringBuilder sb = new StringBuilder();
    for (String[] linesForOneCu : expectedLines) {
      if (sb.length() != 0) {
        sb.append("\n\n");
      }
      sb.append(Joiner.on('\n').join(linesForOneCu));
    }
    return sb.toString();
  }

  /**
   * An operation can be performed in the context of a logger.
   */
  public interface LoggableOperation<T> {
    /** Perform the operation logging any messages to the given logger. */
    T run(Logger logger);
  }

  /**
   * Performs an operation in the context of a logger and checks that
   * all warning and severe entries match the given expected errors.
   */
  public static <T> T expectErrors(
      LoggableOperation<T> op, String... expectedErrors) {

    Logger logger = Logger.getAnonymousLogger();
    logger.setUseParentHandlers(false);
    List<LogRecord> logRecords = Lists.newArrayList();
    logger.addHandler(new Handler() {

      @Override
      public void publish(LogRecord record) {
        logRecords.add(record);
      }

      @Override
      public void flush() {
        // Ok
      }

      @Override
      public void close() {
        // Ok
      }
    });
    logger.setLevel(Level.WARNING);

    T result = op.run(logger);

    Function<LogRecord, String> getMessage = new Function<LogRecord, String>() {
      @Override
      public String apply(LogRecord r) {
        return r.getMessage();
      }
    };
    ImmutableList<String> allMessages = ImmutableList.copyOf(Lists.transform(
          logRecords, getMessage));

    List<String> unsatisfied = Lists.newArrayList();
    for (String expectedError : expectedErrors) {
      Iterator<LogRecord> it = logRecords.iterator();
      boolean found = false;
      while (it.hasNext()) {
        LogRecord r = it.next();
        if (r.getMessage().contains(expectedError)) {
          it.remove();
          found = true;
          break;
        }
      }
      if (!found) {
        unsatisfied.add(expectedError);
      }
    }
    if (!(logRecords.isEmpty() && unsatisfied.isEmpty())) {
      Joiner tj = Joiner.on("\n\t");
      Joiner nj = Joiner.on('\n');
      List<String> got = Lists.transform(
          logRecords,
          new Function<LogRecord, String>() {
            @Override
            public String apply(LogRecord r) {
              return r.getMessage();
            }
          });
      String wantStr = unsatisfied.isEmpty()
          ? "" : "\n\t" + tj.join(unsatisfied) + "\n";
      String gotStr = got.isEmpty()
          ? "" : "\n\t" + tj.join(got) + "\n";
      String msg = "Expected errors [" + wantStr + "] got [" + gotStr + "]";
      Assert.assertEquals(
          nj.join(allMessages), nj.join(unsatisfied), nj.join(got));
      Assert.fail(msg);
    }
    return result;
  }

  /**
   * Serialize the given nodes and decorate with the given decorator.
   */
  public static String serializeNodes(
      Iterable<? extends NodeI<?, ?, ?>> nodes, @Nullable Decorator decorator)
  throws UnparseVerificationException {
    StringBuilder sb = new StringBuilder();
    for (NodeI<?, ?, ?> node : nodes) {
      Iterable<Event> skeleton = SList.forwardIterable(
          Trees.startUnparse(null, (BaseNode<?, ?, ?>) node, decorator));
      Optional<SerialState> serialized =
          node.getNodeType().getParSer().unparse(
          new SerialState(skeleton),
          SerialErrorReceiver.DEV_NULL);
      Assert.assertTrue(
          node.toAsciiArt(""),
          serialized.isPresent());
      Verified verified = Unparse.verify(
          SList.forwardIterable(serialized.get().output));
      FormattedSource fs = Unparse.format(verified);
      if (sb.length() != 0) {
        sb.append("\n\n");
      }
      sb.append(fs.code);
    }
    return sb.toString();
  }

  /**
   * Parses and serializes lines of compilation units to smooth over minor
   * indentation and formatting issues when comparing expected output to
   * actual output.
   */
  public static Optional<String> normalizeCompilationUnitSource(
      String[][] linesPerFile)
  throws UnparseVerificationException {
    Optional<ImmutableList<FileNode>> files =
        maybeParseCompilationUnits(linesPerFile);
    return files.isPresent()
        ? Optional.of(serializeNodes(files.get(), null))
        : Optional.absent();
  }

  /**
   * Runs processing passes on a group of compilation units.
   */
  public interface PassRunner {
    /**
     * @return the processed compilation units.
     */
    ImmutableList<FileNode> runPasses(
        Logger logger, ImmutableList<FileNode> cus);
  }
}
