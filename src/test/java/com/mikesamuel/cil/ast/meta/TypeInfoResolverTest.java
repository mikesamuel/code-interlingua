package com.mikesamuel.cil.ast.meta;

import org.junit.Test;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.mikesamuel.cil.ast.meta.TypeSpecification.Variance;

import junit.framework.TestCase;

@SuppressWarnings("javadoc")
public final class TypeInfoResolverTest extends TestCase {

  @Test
  public void testClassbasedFieldAndMethodInfo() {
    TypeInfoResolver r = TypeInfoResolver.Resolvers.forClassLoader(
        getClass().getClassLoader());
    r.resolve(typ("java", "lang", "Integer").typeName);
  }

  @Test
  public void testSuperTypesOf() {
    TypeInfoResolver r = TypeInfoResolver.Resolvers.forClassLoader(
        getClass().getClassLoader());
    assertEquals(
        ImmutableList.of(),
        ImmutableList.copyOf(
            r.superTypesOf(typ("java", "lang", "Object"))));
    assertEquals(
        ImmutableList.of(
            typ("java", "lang", "Object"),
            typ("java", "io", "Serializable"),
            typ(typ("java", "lang", "Comparable"),
                is(typ("java", "lang", "String"))),
            typ("java", "lang", "CharSequence")
            ),
        ImmutableList.copyOf(
            r.superTypesOf(typ("java", "lang", "String"))));
    assertEquals(
        ImmutableList.of(
            typ("java", "lang", "Object"),
            typ(typ("java", "util", "Collection"),
                is(typ("java", "lang", "String")))),
        ImmutableList.copyOf(
            r.superTypesOf(
                typ(
                    typ("java", "util", "List"),
                    is(typ("java", "lang", "String"))))));
    assertEquals(
        Joiner.on('\n').join(
            typ("java", "lang", "Object"),
            typ(typ("java", "util", "Collection"),
                ext(typ("java", "lang", "String")))),
        Joiner.on('\n').join(
            r.superTypesOf(
                typ(
                    typ("java", "util", "List"),
                    ext(typ("java", "lang", "String"))))));
    assertEquals(
        Joiner.on('\n').join(
            typ("java", "lang", "Object"),
            typ(typ("java", "util", "Collection"),
                sup(typ("java", "lang", "String")))),
        Joiner.on('\n').join(
            r.superTypesOf(
                typ(
                    typ("java", "util", "List"),
                    sup(typ("java", "lang", "String"))))));
  }

  private static TypeSpecification typ(String... parts) {
    Name nm = Name.DEFAULT_PACKAGE;
    for (int i = 0, n = parts.length; i < n; ++i) {
      nm = nm.child(parts[i], i + 1 == n ? Name.Type.CLASS : Name.Type.PACKAGE);
    }
    return new TypeSpecification(nm);
  }

  private static TypeSpecification typ(
      TypeSpecification raw, TypeSpecification.TypeBinding... bs) {
    return new TypeSpecification(
        raw.typeName,
        ImmutableList.copyOf(bs),
        raw.nDims);
  }

  private static TypeSpecification.TypeBinding is(TypeSpecification bound) {
    return new TypeSpecification.TypeBinding(Variance.INVARIANT, bound);
  }

  private static TypeSpecification.TypeBinding ext(TypeSpecification bound) {
    return new TypeSpecification.TypeBinding(Variance.EXTENDS, bound);
  }

  private static TypeSpecification.TypeBinding sup(TypeSpecification bound) {
    return new TypeSpecification.TypeBinding(Variance.SUPER, bound);
  }
}
