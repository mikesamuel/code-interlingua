package com.mikesamuel.cil.ast.passes;

import org.junit.Test;

import com.google.common.collect.ImmutableList;
import com.mikesamuel.cil.ast.passes.Name.Type;

import junit.framework.TestCase;

@SuppressWarnings("javadoc")
public final class TypeNameResolverTest extends TestCase {

  @Test
  public void testForClassLoader() {
    ClassLoader cl = getClass().getClassLoader();
    if (cl == null) {
      cl = ClassLoader.getSystemClassLoader();
    }
    TypeNameResolver r = TypeNameResolver.Resolvers.forClassLoader(cl);
    assertEquals(
        ImmutableList.of(),
        ImmutableList.copyOf(r.lookupTypeName("java.bogus.NoSuchType")));
    assertEquals(
        ImmutableList.of(
            Name.DEFAULT_PACKAGE
            .child("java", Type.PACKAGE)
            .child("lang", Type.PACKAGE)
            .child("Object", Type.CLASS)
            ),
        ImmutableList.copyOf(r.lookupTypeName("java.lang.Object")));
    assertEquals(
        ImmutableList.of(
            Name.DEFAULT_PACKAGE
            .child("java", Type.PACKAGE)
            .child("util", Type.PACKAGE)
            .child("Map", Type.CLASS)
            .child("Entry", Type.CLASS)
            ),
        ImmutableList.copyOf(r.lookupTypeName("java.util.Map$Entry")));
    assertEquals(
        ImmutableList.of(
            Name.DEFAULT_PACKAGE
            .child("java", Type.PACKAGE)
            .child("util", Type.PACKAGE)
            .child("Map", Type.CLASS)
            .child("Entry", Type.CLASS)
            ),
        // Inner class referred to via outer type's super type.
        ImmutableList.copyOf(r.lookupTypeName("java.util.HashMap$Entry")));
    assertEquals(
        ImmutableList.of(
            Name.DEFAULT_PACKAGE
            .child("java", Type.PACKAGE)
            .child("util", Type.PACKAGE)
            .child("Map", Type.CLASS)
            .child("Entry", Type.CLASS)
            ),
        ImmutableList.copyOf(r.lookupTypeName("java.util.Map.Entry")));
    assertEquals(
        ImmutableList.of(
            Name.DEFAULT_PACKAGE
            .child("com", Type.PACKAGE)
            .child("mikesamuel", Type.PACKAGE)
            .child("cil", Type.PACKAGE)
            .child("ast", Type.PACKAGE)
            .child("passes", Type.PACKAGE)
            .child("TypeNameResolverTest", Type.CLASS)
            .child("E", Type.CLASS)
            .child("1", Type.CLASS)
            ),
        ImmutableList.copyOf(
            r.lookupTypeName(
                "com.mikesamuel.cil.ast.passes.TypeNameResolverTest$E$1")));
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
}
