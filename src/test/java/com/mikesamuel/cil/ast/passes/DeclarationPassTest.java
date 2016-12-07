package com.mikesamuel.cil.ast.passes;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.junit.Test;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.collect.Lists;
import com.mikesamuel.cil.ast.BaseNode;
import com.mikesamuel.cil.ast.CompilationUnitNode;
import com.mikesamuel.cil.ast.Java8Comments;
import com.mikesamuel.cil.ast.NodeType;
import com.mikesamuel.cil.ast.Trees;
import com.mikesamuel.cil.ast.Trees.Decorator;
import com.mikesamuel.cil.ast.meta.TypeInfo;
import com.mikesamuel.cil.ast.traits.TypeDeclaration;
import com.mikesamuel.cil.event.Event;
import com.mikesamuel.cil.format.FormattedSource;
import com.mikesamuel.cil.parser.SList;
import com.mikesamuel.cil.parser.SerialErrorReceiver;
import com.mikesamuel.cil.parser.SerialState;
import com.mikesamuel.cil.parser.Unparse;
import com.mikesamuel.cil.parser.Unparse.UnparseVerificationException;
import com.mikesamuel.cil.parser.Unparse.Verified;

import junit.framework.TestCase;

@SuppressWarnings("javadoc")
public class DeclarationPassTest extends TestCase {

  private static final Decorator DECORATOR = new Decorator() {

    @Override
    public String decorate(BaseNode node) {
      if (node instanceof TypeDeclaration) {
        TypeDeclaration td = (TypeDeclaration) node;
        TypeInfo ti = td.getDeclaredTypeInfo();
        if (ti != null) {
          return Java8Comments.lineComment(
              (
                  Modifier.toString(ti.modifiers)
                  + (ti.isAnonymous ? " anonymous" : "")
                  + " " + ti.canonName
                  + (ti.superType.isPresent()
                      ? " extends " + ti.superType.get() : "")
                  + (ti.interfaces.isEmpty()
                      ? "" : " implements "
                          + Joiner.on(", ").join(ti.interfaces))
                  + (ti.outerClass.isPresent()
                      ? " in " + ti.outerClass.get() : "")
                  + (ti.innerClasses.isEmpty()
                      ? "" : " contains "
                          + Joiner.on(", ").join(ti.innerClasses))
              )
              .trim());
        }
      }
      return null;
    }

  };

  private static void assertDeclarations(
      String[][] expectedLines,
      String[][] inputLines,
      String... expectedErrors)
  throws UnparseVerificationException {
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

    List<CompilationUnitNode> cus = ClassNamingPassTest.parseCompilationUnits(
        inputLines
        );

    DeclarationPass dp = new DeclarationPass(logger);
    dp.run(cus);

    StringBuilder sb = new StringBuilder();
    for (CompilationUnitNode cu : cus) {
      Iterable<Event> skeleton = SList.forwardIterable(
          Trees.startUnparse(null, cu, DECORATOR));
      Optional<SerialState> serialized =
          NodeType.CompilationUnit.getParSer().unparse(
          new SerialState(skeleton),
          SerialErrorReceiver.DEV_NULL);
      assertTrue(
          skeleton.toString(),
          serialized.isPresent());
      Verified verified = Unparse.verify(
          SList.forwardIterable(serialized.get().output));
      FormattedSource fs = Unparse.format(verified);
      if (sb.length() != 0) {
        sb.append("\n\n");
      }
      sb.append(fs.code);
    }
    String got = sb.toString();

    sb.setLength(0);
    for (String[] linesForOneCu : expectedLines) {
      if (sb.length() != 0) {
        sb.append("\n\n");
      }
      sb.append(Joiner.on('\n').join(linesForOneCu));
    }
    String want = sb.toString();

    assertEquals(want, got);

    if (!logRecords.isEmpty()) {
      List<String> unsatisfied = new ArrayList<>();
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
        fail(
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
    }
  }

  @Test
  public static void testOneClass() throws Exception {
    assertDeclarations(
        new String[][] {
          {
            "package foo; // /foo/C extends /java/lang/Object",
            "class C {}",
          }
        },
        new String[][] {
          {
            "package foo;",
            "class C {}"
          },
        });
  }

  @Test
  public static void testOneInterface() throws Exception {
    assertDeclarations(
        new String[][] {
          {
            "package foo.bar", ";",  // TODO: why split line before ";"?
            "// /foo/bar/I extends /java/lang/Object"
            + " implements /java/lang/Runnable",
            "interface I extends Runnable {}",
          }
        },
        new String[][] {
          {
            "package foo.bar;",
            "interface I extends Runnable {}"
          },
        });
  }

  @Test
  public static void testInternalSubtypeRelationshipForward() throws Exception {
    assertDeclarations(
        new String[][] {
          {
            "package com.example;"
            + " // public /com/example/C extends /java/lang/Object",
            "public class C {}",
            "package com.example;"
            + " // public final /com/example/D extends /com/example/C",
            "public final class D extends C {}",
          }
        },
        new String[][] {
          {
            "package com.example;",
            "public class C {}"
          },
          {
            "package com.example;",
            "public final class D extends C {}"
          },
        });
  }


  @Test
  public static void testInternalSubtypeRelationshipBackward()
  throws Exception {
    assertDeclarations(
        new String[][] {
          {
            "package com.example;"
            + " // public final /com/example/D extends /com/example/C",
            "public final class D extends C {}",
            "package com.example;"
            + " // public /com/example/C extends /java/lang/Object",
            "public class C {}",
          }
        },
        new String[][] {
          {
            "package com.example;",
            "public final class D extends C {}"
          },
          {
            "package com.example;",
            "public class C {}"
          },
        });
  }

  @Test
  public static void testInnerTypes() throws Exception {
    assertDeclarations(
        new String[][] {
          {
            "package foo;"
            + " // /foo/C extends /java/lang/Object"
            + " contains /foo/C$I, /foo/C$J",
            "class C {"
            + " // /foo/C$I extends /java/lang/Object in /foo/C",
              "interface I {}"
            + " // /foo/C$J extends /java/lang/Object in /foo/C",
              "interface J {} }",
          }
        },
        new String[][] {
          {
            "package foo;",
            "class C {",
            "  interface I {}",
            "  interface J {}",
            "}",
          },
        });
  }

  @Test
  public static void testDuplicateTopLevelType() throws Exception {
    assertDeclarations(
        new String[][] {
          {
            "package foo;"
            + " // /foo/C extends /java/lang/Object",
            "class C {}",
          },
          {
            "package foo;"
            + " // /foo/C",
            "class C {}",
          }
        },
        new String[][] {
          {
            "//a.java",
            "package foo;",
            "class C {",
            "}",
          },
          {
            "//b.java",
            "package foo;",
            "class C {",
            "}",
          },
        },
        "Duplicate definition for /foo/C "
        );
  }

  @Test
  public static void testDuplicateInnerType() throws Exception {
    assertDeclarations(
        new String[][] {
          {
            "package foo;"
            + " // /foo/C extends /java/lang/Object"
            + " contains /foo/C$I",  // Only one mentioned as inner
            "class C {"
            + " // /foo/C$I extends /java/lang/Object in /foo/C",
              "interface I {}"
            // Actually resolved
            + " // /foo/C$I in /foo/C",  // Partially resolved
              "interface I {} }",
          }
        },
        new String[][] {
          {
            "//Foo.java",
            "package foo;",
            "class C {",
            "  interface I {}",
            "  interface I {}",
            "}",
          },
        },
        "//Foo.java:5+3-17: Duplicate definition for /foo/C$I"
        + " originally defined at //Foo.java:4+3-17");
  }

  @Test
  public static void testAnonymousTypeInMethod() throws Exception {

  }

  @Test
  public static void testAnonymousTypeInConstructor() throws Exception {

  }

  @Test
  public static void testAnonymousTypesInOverloadedMethods() throws Exception {

  }

  @Test
  public static void testConstructorWithTypeParameters() throws Exception {

  }

}
