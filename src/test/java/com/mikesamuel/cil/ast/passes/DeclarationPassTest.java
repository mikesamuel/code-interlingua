package com.mikesamuel.cil.ast.passes;

import java.lang.reflect.Modifier;
import java.util.List;
import java.util.logging.Logger;

import org.junit.Test;

import com.google.common.base.Joiner;
import com.google.common.base.Optional;
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
      String[]... inputLines)
  throws UnparseVerificationException {

    List<CompilationUnitNode> cus = ClassNamingPassTest.parseCompilationUnits(
        inputLines
        );

    DeclarationPass dp = new DeclarationPass(
        Logger.getLogger(DeclarationPassTest.class.getName()));
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
        sb.append("\n");
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

}
