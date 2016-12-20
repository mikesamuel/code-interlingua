package com.mikesamuel.cil.ast.meta;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.ImmutableList;
import com.mikesamuel.cil.ast.meta.Name;
import com.mikesamuel.cil.ast.meta.TypeInfoResolver;
import com.mikesamuel.cil.ast.meta.TypeNameResolver;
import com.mikesamuel.cil.ast.meta.Name.Type;

import junit.framework.TestCase;

@SuppressWarnings("javadoc")
public final class TypeNameResolverTest extends TestCase {
  TypeNameResolver r;

  @Before
  @Override
  public void setUp() throws Exception {
    super.setUp();
    ClassLoader cl = getClass().getClassLoader();
    if (cl == null) {
      cl = ClassLoader.getSystemClassLoader();
    }
    TypeInfoResolver tir = TypeInfoResolver.Resolvers.forClassLoader(cl);
    r = TypeNameResolver.Resolvers.canonicalizer(tir);
  }

  @After
  @Override
  public void tearDown() throws Exception {
    r = null;
    super.tearDown();
  }

  private static Name ambiguousName(String qualifiedName) {
    Name ambiguousName = Name.DEFAULT_PACKAGE;
    for (String part : qualifiedName.split("[.]")) {
      ambiguousName = ambiguousName.child(part, Type.AMBIGUOUS);
    }
    return ambiguousName;
  }

  private ImmutableList<Name> lookupTypeName(String qualifiedName) {
    return r.lookupTypeName(ambiguousName(qualifiedName));
  }


  @Test
  public void testNoSuchType() {
    assertEquals(
        ImmutableList.of(),
        lookupTypeName("java.bogus.NoSuchType"));
  }

  @Test
  public void testSimpleType() {
    assertEquals(
        ImmutableList.of(
            Name.DEFAULT_PACKAGE
            .child("java", Type.PACKAGE)
            .child("lang", Type.PACKAGE)
            .child("Object", Type.CLASS)
            ),
        lookupTypeName("java.lang.Object"));
  }

  @Test
  public void testCanonicalInnerClass() {
    assertEquals(
        ImmutableList.of(
            Name.DEFAULT_PACKAGE
            .child("java", Type.PACKAGE)
            .child("util", Type.PACKAGE)
            .child("Map", Type.CLASS)
            .child("Entry", Type.CLASS)
            ),
        lookupTypeName("java.util.Map.Entry"));
  }

  @Test
  public void testNoncanonicalInnerClass() {
    assertEquals(
        ImmutableList.of(
            Name.DEFAULT_PACKAGE
            .child("java", Type.PACKAGE)
            .child("util", Type.PACKAGE)
            .child("Map", Type.CLASS)
            .child("Entry", Type.CLASS)
            ),
        // Inner class referred to via outer type's super type.
        lookupTypeName("java.util.HashMap.Entry"));
  }

  @Test
  public void testAnonymousEnumConstant() {
    assertEquals(
        ImmutableList.of(
            Name.DEFAULT_PACKAGE
            .child("com", Type.PACKAGE)
            .child("mikesamuel", Type.PACKAGE)
            .child("cil", Type.PACKAGE)
            .child("ast", Type.PACKAGE)
            .child("meta", Type.PACKAGE)
            .child("TypeNameResolverTest", Type.CLASS)
            .child("E", Type.CLASS)
            .child("1", Type.CLASS)
            ),
        lookupTypeName(
            "com.mikesamuel.cil.ast.meta.TypeNameResolverTest.E.1"));
  }

  @Test
  public static void testDisambiguateAll() {
    assertEquals(
        ImmutableList.of(
            Name.DEFAULT_PACKAGE
            .child("java", Type.PACKAGE)
            .child("lang", Type.PACKAGE)
            .child("Object", Type.CLASS),

            Name.DEFAULT_PACKAGE
            .child("java", Type.PACKAGE)
            .child("lang", Type.CLASS)
            .child("Object", Type.CLASS),

            Name.DEFAULT_PACKAGE
            .child("java", Type.CLASS)
            .child("lang", Type.CLASS)
            .child("Object", Type.CLASS)
            ),
        TypeNameResolver.Resolvers.disambiguateClasses(
            ambiguousName("java.lang.Object"),
            true, false));
  }

  @Test
  public static void testDisambiguateOuterOnly() {
    assertEquals(
        ImmutableList.of(
            Name.DEFAULT_PACKAGE
            .child("java", Type.CLASS),

            Name.DEFAULT_PACKAGE
            .child("java", Type.PACKAGE)
            .child("lang", Type.CLASS),

            Name.DEFAULT_PACKAGE
            .child("java", Type.PACKAGE)
            .child("lang", Type.PACKAGE)
            .child("Object", Type.CLASS)
            ),
        TypeNameResolver.Resolvers.disambiguateClasses(
            ambiguousName("java.lang.Object"),
            false, true));
  }

  @Test
  public void testAmbiguousInnerClasses() {
    assertEquals(
        ImmutableList.of(
            Name.DEFAULT_PACKAGE
            .child("com", Type.PACKAGE)
            .child("mikesamuel", Type.PACKAGE)
            .child("cil", Type.PACKAGE)
            .child("ast", Type.PACKAGE)
            .child("meta", Type.PACKAGE)
            .child("TypeNameResolverTest", Type.CLASS)
            .child("Sub", Type.CLASS)
            .child("I", Type.CLASS)
            // I in Base is masked.
            ),
        lookupTypeName(Sub.class.getCanonicalName() + ".I"));
    // TODO: Should I in Base be masked if Sub is private and we are not in
    // the scope of TypeNameResolverTest?
  }


  enum E {
    A,
    B() {
      @Override public void foo() {
        System.err.println("override");
      }
    },
    ;

    public void foo() {
      // Default
    }
  }


  static class Base {
    interface I {
      // Reflected over by test code
    }
  }

  static class Sub extends Base {
    interface I {
      // Reflected over by test code
    }
  }
}
