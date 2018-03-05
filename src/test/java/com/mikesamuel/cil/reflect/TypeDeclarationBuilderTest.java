package com.mikesamuel.cil.reflect;

import java.lang.reflect.Modifier;

import org.junit.Test;

import com.google.common.base.Preconditions;

import junit.framework.TestCase;

@SuppressWarnings("javadoc")
public final class TypeDeclarationBuilderTest extends TestCase {

  @Test
  public static void testDeclarationBuilders() {
    final class Builder extends TypeDeclarationBuilder {
      Builder(int modifiers, String name) {
        super(modifiers, name);
      }

      TypeMetadata stored;

      @Override
      void store(TypeMetadata md) {
        Preconditions.checkState(stored == null);
        stored = Preconditions.checkNotNull(md);
      }
    }
    Builder builder = new Builder(Modifier.PUBLIC, "/C");
    builder
      .ctor(Modifier.PUBLIC, "/C.<init>(0)", "()V")
        .adapter(new InvocationAdapter() {
          @Override
          public String toString() {
            return "(new /C)";
          }

          @Override
          public Object apply(Object instance, Object... args) {
            return null;
          }
        })
        .endMember()
      .field(Modifier.PUBLIC | Modifier.FINAL, "/C.x")
        .type().tInt()
        .adapter(new FieldAdapter() {
          @Override
          public String toString() {
            return "(=> 42)";
          }

          @Override
          public Object get(Object instance) throws NullPointerException {
            if (instance == null) { throw new NullPointerException(); }
            return 42;
          }

          @Override
          public Object set(Object instance, Object newValue)
              throws NullPointerException, UnsupportedOperationException {
            if (instance == null) { throw new NullPointerException(); }
            throw new UnsupportedOperationException();
          }
        })
        .endMember()
      .field(Modifier.PUBLIC | Modifier.STATIC | Modifier.FINAL, "/C.ARR")
        .type().array(1).tObject("/java/lang/String")
        .endMember()
      .method(Modifier.PUBLIC, "/C.f(0)", "(II[[ZLjava/util/List;)Ljava/lang/String;")
        .returns().tObject("/java/lang/String")
        .param("/C.f(0)<T>")
        .thrown().tObject("/java/lang/IllegalArgumentException")
        .formal().tInt()
        .formal().tInt()
        .formal().array(1).array(1).tBoolean()
        .formal().p().tObject("/java/lang/String").tObject("/java/util/List")
        .endMember()
      .superType().tObject("/java/lang/Object")
      .superType().p().tObject("/C").tObject("/java/lang/Comparable")
      .store();

    TypeMetadata md = builder.stored;
    assertNotNull(md);
    assertEquals(Modifier.PUBLIC, md.modifiers);
    assertEquals("/C", md.name);
    assertEquals(
        "[/java/lang/Object, /java/lang/Comparable</C>]",
        md.superTypes.toString());
    assertEquals(1, md.constructors.size());
    assertEquals(2, md.fields.size());
    assertEquals(1, md.methods.size());
    assertEquals(0, md.typeParameters.size());

    ConstructorMetadata ctor = md.constructors.get(0);
    assertEquals(Modifier.PUBLIC, ctor.modifiers);
    assertEquals("/C.<init>(0)", ctor.name);
    assertEquals("()V", ctor.descriptor);
    assertEquals("(new /C)", ctor.adapter.get().toString());
    assertTrue(ctor.formals.isEmpty());
    assertTrue(ctor.thrown.isEmpty());

    FieldMetadata f0 = md.fields.get(0);
    assertEquals("/C.x", f0.name);
    assertEquals(Modifier.PUBLIC | Modifier.FINAL, f0.modifiers);
    assertEquals("/java/lang/Integer.TYPE", f0.type.toString());
    assertEquals("(=> 42)", f0.adapter.get().toString());

    FieldMetadata f1 = md.fields.get(1);
    assertEquals("/C.ARR", f1.name);
    assertEquals(
        Modifier.PUBLIC | Modifier.FINAL | Modifier.STATIC, f1.modifiers);
    assertEquals("/java/lang/String[]", f1.type.toString());
    assertFalse(f1.adapter.isPresent());

    MethodMetadata f = md.methods.get(0);
    assertEquals(Modifier.PUBLIC, f.modifiers);
    assertEquals("/C.f(0)", f.name);
    assertEquals("(II[[ZLjava/util/List;)Ljava/lang/String;", f.descriptor);
    assertFalse(f.adapter.isPresent());
    assertEquals(
        "[/java/lang/Integer.TYPE, /java/lang/Integer.TYPE,"
        + " /java/lang/Boolean.TYPE[][], /java/util/List</java/lang/String>]",
        f.formals.toString());
    assertEquals("[/java/lang/IllegalArgumentException]", f.thrown.toString());
  }
}
