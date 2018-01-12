package com.mikesamuel.cil.ast.passes;

import java.util.HashMap;
import java.util.Map;

import com.mikesamuel.cil.ast.meta.Name;

/**
 * Allocates method variants.
 *
 * @see Name#variant
 */
public final class MethodVariantPool {
  private final Map<Name, Integer> methodCounters = new HashMap<>();

  /** Allocates an unused variant. */
  public Name allocateVariant(Name containingTypeName, String methodName) {
    Name nameWithoutVariant = containingTypeName.method(methodName, 1);
    Integer ordinal = methodCounters.get(nameWithoutVariant);
    if (ordinal == null) { ordinal = 0; }
    ordinal += 1;
    methodCounters.put(nameWithoutVariant, ordinal);
    return containingTypeName.method(methodName, ordinal);
  }
}
