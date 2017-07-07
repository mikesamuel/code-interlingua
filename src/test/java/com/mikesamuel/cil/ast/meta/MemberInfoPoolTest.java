package com.mikesamuel.cil.ast.meta;

import java.util.Set;

import org.junit.Before;
import org.junit.Test;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableSet;
import com.mikesamuel.cil.ast.meta.StaticType.TypePool;

import junit.framework.TestCase;

@SuppressWarnings("javadoc")
public final class MemberInfoPoolTest extends TestCase {

  MemberInfoPool ip;

  @Override
  @Before
  public void setUp() throws Exception {
    ClassLoader cl = MemberInfoPoolTest.class.getClassLoader();
    if (cl == null) { cl = ClassLoader.getSystemClassLoader(); }
    TypeInfoResolver r = TypeInfoResolver.Resolvers.forClassLoader(cl);
    TypePool tp = new TypePool(r);
    this.ip = new MemberInfoPool(tp);
   }

  @Test
  public void testFooToStringOverriddenBy() {
    Name fooToString = Name.DEFAULT_PACKAGE
        .child("com", Name.Type.PACKAGE)
        .child("mikesamuel", Name.Type.PACKAGE)
        .child("cil", Name.Type.PACKAGE)
        .child("ast", Name.Type.PACKAGE)
        .child("meta", Name.Type.PACKAGE)
        .child("MemberInfoPoolTest", Name.Type.CLASS)
        .child("Foo", Name.Type.CLASS)
        .method("toString", 1);

    Optional<CallableInfo> ciOpt = ip.typePool.r.resolveCallable(fooToString);
    assertTrue(ciOpt.isPresent());
    CallableInfo ci = ciOpt.get();

    Set<Name> overridden = ip.overriddenBy(ci);
    assertEquals(
        ImmutableSet.of(
            JavaLang.JAVA_LANG_OBJECT.rawName.method("toString", 1),
            JavaLang.JAVA_LANG_ENUM.rawName.method("toString", 1)
            ),
        overridden);
  }

  @Test
  public void testBarXToStringOverriddenBy() {
    Name barYToString = Name.DEFAULT_PACKAGE
        .child("com", Name.Type.PACKAGE)
        .child("mikesamuel", Name.Type.PACKAGE)
        .child("cil", Name.Type.PACKAGE)
        .child("ast", Name.Type.PACKAGE)
        .child("meta", Name.Type.PACKAGE)
        .child("MemberInfoPoolTest", Name.Type.CLASS)
        .child("Bar", Name.Type.CLASS)
        .child("1", Name.Type.CLASS)
        .method("toString", 1);

    Optional<CallableInfo> ciOpt = ip.typePool.r.resolveCallable(barYToString);
    assertTrue(ciOpt.isPresent());
    CallableInfo ci = ciOpt.get();

    Set<Name> overridden = ip.overriddenBy(ci);
    assertEquals(
        ImmutableSet.of(
            JavaLang.JAVA_LANG_OBJECT.rawName.method("toString", 1),
            JavaLang.JAVA_LANG_ENUM.rawName.method("toString", 1)
            ),
        overridden);
  }


  enum Foo {
    A, B, C;

    @Override
    public String toString() {
      return super.toString();
    }
  }

  enum Bar {
    X,
    Y() {
      @Override
      public String toString() {
        return "Y!";
      }
    },
    Z,
  }
}
