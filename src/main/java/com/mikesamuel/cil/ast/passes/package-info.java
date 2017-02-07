/**
 * Common AST passes.
 * <p>
 * The {@link com.mikesamuel.cil.ast.passes.CommonPassRunner} ties all the
 * common passes together.
 * <p>
 * In the order in which they should normally be run.
 * <ol>
 *   <li>
 *     {@link com.mikesamuel.cil.ast.passes.DefragmentTypesPass} collects all
 *     the type information for a declaration together:
 *     {@code int x[]} &rarr; {@code int[] x}.
 *
 *   <li>
 *     {@link com.mikesamuel.cil.ast.passes.DeclarationPass} looks at all type
 *     declarations disambiguates names in {@code extends} and
 *     {@code implements} clauses so that we can know, within a given class
 *     body, which field, method, and type parameter names are available, and
 *     in which class they are declared.
 *
 *   <li>
 *     {@link com.mikesamuel.cil.ast.passes.ExpressionScopePass} allocates
 *     scopes for all blocks and bodies and resolves names to locals and fields.
 *
 *   <li>
 *     {@link com.mikesamuel.cil.ast.passes.DismabiguationPass} replaces
 *     ambiguous names and {@link com.mikesamuel.cil.ast.ContextFreeNamesNode}s
 *     with specific alternative variants so that every identifier is known to
 *     be a package name, class or interface name, field name, method name,
 *     local variable name, type parameter name, or label.
 *
 *   <li>
 *     {@link com.mikesamuel.cil.ast.passes.ClassMemberPass} collects
 *     information about fields and methods, and fully resolves types.
 *
 *   <li>
 *     {@link com.mikesamuel.cil.ast.passes.TypingPass} stores with each
 *     expression node its static type.
 *     Adds any implicit casts, and boxing/unboxing conversions to
 *     the tree.
 *     Stores with method and constructors invocations the JVM descriptor
 *     of the callee.
 * </ol>
 */
@javax.annotation.ParametersAreNonnullByDefault
package com.mikesamuel.cil.ast.passes;
