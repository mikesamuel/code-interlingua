package com.mikesamuel.cil.ast.traits;

/**
 * Trait for nodes that establish a new scope for {@link TypeDeclaration}s.
 */
public interface TypeScope {
  /**
   * Whether to allow forward references to classes.
   * <p>
   * In a class body or similar scope, one can reference classes declared later.
   * <pre>
   * class C {
   *   T forwardReference;  // ALLOWED
   *
   *   class T {}
   * }</pre>
   * but in statement blocks that is not allowed
   * <pre>
   * {
   *   T forward;  // DISALLOWED
   *   class T {}
   *   T backward;  // ALLOWED
   * }
   * </pre>
   */
  public default boolean allowForwardTypeReferences() {
    return true;
  }
}
