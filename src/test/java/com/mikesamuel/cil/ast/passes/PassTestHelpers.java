package com.mikesamuel.cil.ast.passes;

import java.util.Iterator;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.junit.Assert;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.mikesamuel.cil.ast.CompilationUnitNode;
import com.mikesamuel.cil.ast.NodeType;
import com.mikesamuel.cil.ast.Trees;
import com.mikesamuel.cil.ast.Trees.Decorator;
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

class PassTestHelpers {

  static ImmutableList<CompilationUnitNode> parseCompilationUnits(
      String[]... linesPerFile) {
    ImmutableList.Builder<CompilationUnitNode> b = ImmutableList.builder();
    for (String[] lines : linesPerFile) {
      Input.Builder inputBuilder = Input.builder()
          .code(Joiner.on('\n').join(lines));
      if (lines.length != 0) {
        inputBuilder.source(lines[0]);
      }
      Input inp = inputBuilder.build();
      ParseResult result = PTree.complete(NodeType.CompilationUnit).getParSer()
          .parse(new ParseState(inp), new LeftRecursion(),
              ParseErrorReceiver.DEV_NULL);
      Assert.assertEquals(ParseResult.Synopsis.SUCCESS, result.synopsis);
      ParseState afterParse = result.next();
      CompilationUnitNode cu = (CompilationUnitNode)
          Trees.of(inp, afterParse.output);
      b.add(cu);
    }
    return b.build();
  }
  static void assertAnnotatedOutput(
      PassRunner passRunner,
      String[][] expectedLines,
      String[][] inputLines,
      Decorator decorator,
      String... expectedErrors)
  throws UnparseVerificationException {
    ImmutableList<CompilationUnitNode> cus = expectErrors(
        new LoggableOperation<ImmutableList<CompilationUnitNode>>() {
          @Override
          public ImmutableList<CompilationUnitNode> run(Logger logger) {
            return passRunner.runPasses(
                logger,
                parseCompilationUnits(inputLines));
          }
        },
        expectedErrors);

    String got = serializeCompilationUnits(cus, decorator);

    StringBuilder sb = new StringBuilder();
    for (String[] linesForOneCu : expectedLines) {
      if (sb.length() != 0) {
        sb.append("\n\n");
      }
      sb.append(Joiner.on('\n').join(linesForOneCu));
    }
    String want = sb.toString();

    Assert.assertEquals(want, got);
  }

  interface LoggableOperation<T> {
    T run(Logger logger);
  }

  static <T> T expectErrors(
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
      Assert.fail(
          "Expected errors " + unsatisfied + "\ngot " +
              Lists.transform(
                  logRecords,
                  new Function<LogRecord, String>() {
                    @Override
                    public String apply(LogRecord r) {
                      return r.getMessage();
                    }
                  }));
    }
    return result;
  }

  static String serializeCompilationUnits(
      Iterable<? extends CompilationUnitNode> cus, Decorator decorator)
  throws UnparseVerificationException {
    StringBuilder sb = new StringBuilder();
    for (CompilationUnitNode cu : cus) {
      Iterable<Event> skeleton = SList.forwardIterable(
          Trees.startUnparse(null, cu, decorator));
      Optional<SerialState> serialized =
          NodeType.CompilationUnit.getParSer().unparse(
          new SerialState(skeleton),
          SerialErrorReceiver.DEV_NULL);
      Assert.assertTrue(
          cu.toAsciiArt(""),
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


  interface PassRunner {
    ImmutableList<CompilationUnitNode> runPasses(
        Logger logger, ImmutableList<CompilationUnitNode> cus);
  }
}
