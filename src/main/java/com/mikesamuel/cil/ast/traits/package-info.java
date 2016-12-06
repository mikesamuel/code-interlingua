/**
 * Interfaces that are implemented by specific node subclasses.
 * <p>
 * These interfaces expose several kinds of methods:
 * <ol>
 *   <li>getters and setters for metadata.
 *   See {@link com.mikesamuel.cil.ast.meta} and below.
 *   <li>methods with {@code default} implementations that use BaseNode
 *   convenience methods to decompose the subtree structure.
 * </ol>
 *
 * <p>
 * The code generator that generates a *Node class per grammar production also
 * generates getter/setter overrides, and can mix-in overrides for trait
 * methods.
 */
@javax.annotation.ParametersAreNonnullByDefault
package com.mikesamuel.cil.ast.traits;
