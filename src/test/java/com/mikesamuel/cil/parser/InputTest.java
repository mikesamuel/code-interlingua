package com.mikesamuel.cil.parser;

import org.junit.Test;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;

import junit.framework.TestCase;

@SuppressWarnings("javadoc")
public final class InputTest extends TestCase {

  // TODO: Move this to IgnorablesTest.

  @Test
  public static void testTokenBreaks() {
    String code = Joiner.on('\n').join(
        "",
        "package foo.bar.baz;",
        "",
        "  ",
        "// Foo Bar Baz",
        "",
        "/** A jdoc comment */class Foo",
        "extends/**/Bar{",
        "  java.lang .",
        "String s;",
        "}");

    ImmutableList<String> want = ImmutableList.of(
        "#\n",
        "!package",
        "# ",
        "!foo.bar.baz;",
        "#\n\n  \n// Foo Bar Baz\n\n/** A jdoc comment */",
        "!class",
        "# ",
        "!Foo",
        "#\n",
        "!extends",
        "#/**/",
        "!Bar{",
        "#\n  ",
        "!java.lang",
        "# ",
        "!.",
        "#\n",
        "!String",
        "# ",
        "!s;",
        "#\n",
        "!}");

    ImmutableList.Builder<String> gotBuilder = ImmutableList.builder();

    Input input = Input.fromCharSequence("test", code);
    StringBuilder token = new StringBuilder();
    int parsed = 0;
    int limit = input.content.length();
    while (parsed < limit) {
      int idx = input.indexAfterIgnorables(parsed);
      int end;
      if (idx != parsed) {
        if (token.length() != 0) {
          gotBuilder.add("!" + token);
          token.setLength(0);
        }
        gotBuilder.add("#" + input.content.substring(parsed, idx));
        end = idx;
      } else {
        token.append(input.content.charAt(idx));
        end = idx + 1;
      }
      parsed = end;
    }
    if (token.length() != 0) {
      gotBuilder.add("!" + token);
      token.setLength(0);
    }

    ImmutableList<String> got = gotBuilder.build();
    if (!want.equals(got)) {
      Joiner j = Joiner.on("\n======\n");
      assertEquals(j.join(want), j.join(got));
      assertEquals(want, got);
    }
  }

}
