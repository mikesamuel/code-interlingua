package com.mikesamuel.cil.ast.j8;

import static com.mikesamuel.cil.event.Event.content;
import static com.mikesamuel.cil.event.Event.pop;
import static com.mikesamuel.cil.event.Event.push;
import static com.mikesamuel.cil.event.Event.token;

import org.junit.Test;

import com.mikesamuel.cil.ast.AbstractParSerTestCase;
import com.mikesamuel.cil.ptree.PTree;

@SuppressWarnings("javadoc")
public final class PackageOrTypeNameTest extends AbstractParSerTestCase {

  @Test
  public void testParseSimple() {
    this.assertParsePasses(
        J8NodeType.PackageOrTypeName,
        "foo",
        push(PackageOrTypeNameNode.Variant.NotAtContextFreeNames),
        push(ContextFreeNamesNode.Variant.ContextFreeNameDotContextFreeName),
        push(ContextFreeNameNode.Variant.Name),
        push(IdentifierNode.Variant.Builtin),
        content("foo", -1),
        pop(),
        pop(),
        pop(),
        pop()
        );
  }

  @Test
  public void testParseComplex() {
    this.assertParsePasses(
        J8NodeType.PackageOrTypeName,
        "foo.bar",
        /**/push(PackageOrTypeNameNode.Variant.NotAtContextFreeNames),
        /*..*/push(ContextFreeNamesNode.Variant.ContextFreeNameDotContextFreeName),
        /*....*/push(ContextFreeNameNode.Variant.Name),
        /*......*/push(IdentifierNode.Variant.Builtin),
        /*........*/content("foo", -1),
        /*......*/pop(),
        /*....*/pop(),
        /*....*/token(".", -1),
        /*....*/push(ContextFreeNameNode.Variant.Name),
        /*......*/push(IdentifierNode.Variant.Builtin),
        /*........*/content("bar", -1),
        /*......*/pop(),
        /*....*/pop(),
        /*..*/pop(),
        /**/pop()
        );
  }

  @Test
  public void testParseComplexer() {
    this.assertParsePasses(
        J8NodeType.PackageOrTypeName,
        "foo.bar.baz",
        /**/push(PackageOrTypeNameNode.Variant.NotAtContextFreeNames),
        /*..*/push(ContextFreeNamesNode.Variant.ContextFreeNameDotContextFreeName),
        /*....*/push(ContextFreeNameNode.Variant.Name),
        /*......*/push(IdentifierNode.Variant.Builtin),
        /*........*/content("foo", -1),
        /*......*/pop(),
        /*....*/pop(),
        /*....*/token(".", -1),
        /*....*/push(ContextFreeNameNode.Variant.Name),
        /*......*/push(IdentifierNode.Variant.Builtin),
        /*........*/content("bar", -1),
        /*......*/pop(),
        /*....*/pop(),
        /*....*/token(".", -1),
        /*....*/push(ContextFreeNameNode.Variant.Name),
        /*......*/push(IdentifierNode.Variant.Builtin),
        /*........*/content("baz", -1),
        /*.....*/pop(),
        /*....*/pop(),
        /*..*/pop(),
        /**/pop()
        );
  }

  @Test
  public void testKeyword() {
    this.assertParseFails(
        PTree.complete(J8NodeType.PackageOrTypeName),
        "Object.class");
  }

}
