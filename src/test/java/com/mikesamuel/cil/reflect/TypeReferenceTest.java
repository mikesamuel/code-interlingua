package com.mikesamuel.cil.reflect;

import java.lang.reflect.Field;

import org.junit.Test;

import com.mikesamuel.cil.ast.meta.StaticType;

import junit.framework.TestCase;

@SuppressWarnings("javadoc")
public final class TypeReferenceTest extends TestCase {

  @Test
  public static void testNameEquivalence() throws Exception {
    int nPrimitives = 0;
    for (Field f : TypeReference.class.getDeclaredFields()) {
      if (f.getName().startsWith("T_")) {
        TypeReference tr = (TypeReference) f.get(null);
        Field other;
        try {
          other = StaticType.class.getDeclaredField(f.getName());
        } catch (@SuppressWarnings("unused") NoSuchFieldException ex) {
          continue;
        }
        StaticType st = (StaticType) other.get(null);
        assertEquals(f.getName(), tr.name, st.typeSpecification.rawName.toString());
        ++nPrimitives;
      }
    }
    assertTrue(nPrimitives >= 9);
  }
}
