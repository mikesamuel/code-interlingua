package com.mikesamuel.cil.expr;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

/**
 * This class is compiled when tests are compiled, but the source is also
 * available as a resource, so the interpreted sequence of events can be
 * compared to that produced by the javac-compiled version.
 */
public class TestStatements {
  /** Appends a bunch of stuff that exercises statement execution. */
  public static List<String> f(String s) {
    boolean t = true;
    Boolean f = false;
    int n = 3;

    List<String> ls = new ArrayList<>();
    ls.add(s);  // Simple expression statement
    ;
    if (t) {
      ls.add("if(t)");
    }
    if (f) {
      ls.add("if(f)");
    }
    // Hanging else
    if (t)
      if (f)
        ls.add("if(f)2");
      else
        ls.add("if(t)if(f)else");
    // class declarations interrupt blocks in the AST.  Test that execution
    // resumes on the other side.
    class C {
      public String get() { return "C.get"; }
    }
    // TODO: Fix how we compute binary names for inner-nomynous-classes.
//  ls.add((new C()).get());

    for (int i = 0; i < n; ++i) {
      ls.add("for i=" + i);
    }

    for (;;) {
      ls.add("bare for");
      break;
    }

    do {
      ls.add("done before check");
    } while (f);

    int j = 0;
    while (j != 4) {
      ls.add("while j=" + j++);
    }

    while (f) {
      ls.add("not whiling away");
    }

    j = 0;
    do {
      ls.add("do " + j);
    } while (++j < 2);

    j = 0;
    labeled_loop:
    while (true) {
      labeled_switch: switch (j++) {
      case 0: ls.add("case zero"); break;
      case 7: ls.add("case seven"); break labeled_loop;
      case 5: ls.add("case five"); continue;
      case 1: ls.add("case one"); continue labeled_loop;
      default: ls.add("default"); break labeled_switch;
      case 2:
        ls.add("case two");
        //$FALL-THROUGH$
      case 3:
        ls.add("case three or fallthrough");
      }
    }

    j = 0;
    tloop:
    while (j != 10) {
      ++j;
      if (j % 2 == 0) { continue tloop; }
      ls.add("while(true) j=" + j);
    }

    String[] strs = new String[] { "s0", "s1", null, "s2", };
    for (String str : strs) {
      ls.add("iterator loop : " + str);
    }

    int lsSize = 0;
    for (@SuppressWarnings("unused") Object x : ls) {
      ++lsSize;
    }
    ls.add("size=" + lsSize);

    labelled_block: {
      ls.add("before");
      if (t) break labelled_block;
      ls.add("unreached");
    }

    labelled_block_2: {
      ls.add("before");
      {
        if (t) break labelled_block_2;
        ls.add("unreached");
      }
      ls.add("unreached");
    }

    try {
      ls.add("try");
    } finally {
      ls.add("finally");
    }

    try (BufferedReader r = new BufferedReader(new StringReader("resource"))) {
      ls.add(r.readLine());
    } catch (@SuppressWarnings("unused") IOException ex) {
      // Should not occur with StringReader
      ls.add("caught IOException");
    }

    try (Bracket a = new Bracket("a", ls);
         Bracket b = new Bracket("b", ls);
         AutoCloseable c = new Bracket("c", ls);
         Bracket d = null) {
      ls.add("trying");
    } catch (@SuppressWarnings("unused") Exception ex) {
      ls.add("caught Exception");
    } finally {
      ls.add("finally after resources");
    }

    return ls;
  }

  /** Adds enter and exit messages on construct / close respectively. */
  public static final class Bracket implements AutoCloseable {
    final String message;
    final List<? super String> out;

    /** */
    public Bracket(String message, List<? super String> out) {
      this.message = message;
      this.out = out;

      this.out.add("enter " + message);
    }

    @Override
    public void close() {
      this.out.add("exit " + message);
    }
  }

  /** */
  public static void main(String... argv) {
    System.err.println(f("foo"));
  }
}
