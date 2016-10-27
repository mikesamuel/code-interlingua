package com.mikesamuel.cil.ast;

import org.junit.Test;

import com.mikesamuel.cil.ptree.PTree;

import static com.mikesamuel.cil.ast.MatchEvent.content;
import static com.mikesamuel.cil.ast.MatchEvent.pop;
import static com.mikesamuel.cil.ast.MatchEvent.push;
import static com.mikesamuel.cil.ast.MatchEvent.token;

@SuppressWarnings("javadoc")
public final class PackageOrTypeNameTest extends AbstractParSerTestCase {

  @Test
  public void testParseSimple() {
    this.assertParsePasses(
        PTree.complete(NodeType.PackageOrTypeName),
        "foo",
        push(PackageOrTypeNameNode.Variant.Identifier),
        push(IdentifierNode.Variant.Builtin),
        content("foo", -1),
        pop(),
        pop()
        );
  }

  @Test
  public void testParseComplex() {
    this.assertParsePasses(
        PTree.complete(NodeType.PackageOrTypeName),
        "foo.bar",
        /**/push(PackageOrTypeNameNode.Variant.PackageOrTypeNameDotIdentifier),
        /*..*/push(PackageOrTypeNameNode.Variant.Identifier),
        /*....*/push(IdentifierNode.Variant.Builtin),
        /*......*/content("foo", -1),
        /*....*/pop(),
        /*..*/pop(),
        /*..*/token(".", -1),
        /*..*/push(IdentifierNode.Variant.Builtin),
        /*....*/content("bar", -1),
        /*..*/pop(),
        /**/pop()
        );
  }

  @Test
  public void testParseComplexer() {
    this.assertParsePasses(
        PTree.complete(NodeType.PackageOrTypeName),
        "foo.bar.baz",
        /**/push(PackageOrTypeNameNode.Variant.PackageOrTypeNameDotIdentifier),
        /*..*/push(PackageOrTypeNameNode.Variant.PackageOrTypeNameDotIdentifier),
        /*....*/push(PackageOrTypeNameNode.Variant.Identifier),
        /*......*/push(IdentifierNode.Variant.Builtin),
        /*........*/content("foo", -1),
        /*......*/pop(),
        /*....*/pop(),
        /*....*/token(".", -1),
        /*....*/push(IdentifierNode.Variant.Builtin),
        /*......*/content("bar", -1),
        /*....*/pop(),
        /*..*/pop(),
        /*..*/token(".", -1),
        /*..*/push(IdentifierNode.Variant.Builtin),
        /*....*/content("baz", -1),
        /*..*/pop(),
        /**/pop()
        );
  }

  @Test
  public void testKeyword() {
    this.assertParseFails(
        PTree.complete(NodeType.PackageOrTypeName),
        "Object.class");
  }

}
