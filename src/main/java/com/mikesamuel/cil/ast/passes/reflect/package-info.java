/**
 * A pass that attaches information about type declarations to the type
 * declarations so that backends do not have to implement their own
 * reflective mechanisms.
 * <p>
 * This pass maps reflective operation like {@code instanceof} and calls
 * to reflective core classes like {@link java.lang.Class} and
 * {@link java.lang.reflect.Method} to operations on
 * a {@link com.mikesamuel.cil.reflect.ReflectionHub} which can map
 * to a reflective hub.
 *
 * <h3>Registering Class Metadata</h3>
 * <p>Class declarations are rewritten to, on class load, register class
 * metadata with a central repository.</p>
 * {@code
 * public class C {
 *   public int x;
 * }}
 * becomes
 * {@code
 * public class C {
 *   public int x;
 *
 *   static {
 *     MyBundle.requireLoaded();
 *   }
 * }
 *
 * public final class MyBundle {  // Name specified by pass configuration.
 *   private Declarations() {}
 *   static {
 *     com.mikesamuel.cil.reflect.TypeMetadataStore
 *         .type(java.lang.reflect.Modifier.PUBLIC, "/C")
 *         .superType("/java/lang/Object")
 *         .field(java.lang.reflect.Modifier.PUBLIC, "x").tInt("/java/lang/Integer#TYPE")
 *         .store();
 *   }
 * }}
 *
 * <h3>{@code Class} method calls</h3>
 * <p>Map method calls on reflective builtins like {@link java.lang.Class},
 * {@link java.lang.reflect.Method} to operations on {@code TypeMetadataStore}.
 * {@code Foo.class.getName()}
 * <p>becomes</p>
 * {@code com.mikesamuel.cil.reflect.TypeMetadataStore.Class.forName("/Foo").getName()}
 *
 * <h3>Annotations</h3>
 * Annotations can only be checked at runtime, and there are no clear analogues
 * in many backend languages to Java annotations, so we
 * <ul>
 *   <li>Rely on the above to turn runtime annotation checks like
 *      {@link java.lang.Class#isAnnotationPresent(Class)} into
 *      operations on the {@code TypeMetadataStore}.
 *   <li>Collect annotation declarations so we can infer default values.
 *   <li>Eliminate annotation declarations.
 *   <li>Extract annotation uses and turn them into side table sets.
 * </ul>
 * <pre>
 * public &#64;interface I {}
 * &#64;I
 * class C {}</pre>
 * <p>becomes</p>
 * {@code
 * class C {
 *   static {
 *     com.mikesamuel.cil.reflect.TypeMetadataStore
 *       .annotate("/C").with("/I")
 *       .store();
 *   }
 * }}
 *
 * <h3>Caveat</h3>
 * No support for proxy classes or {@code ClassLoader}s.
 */
@javax.annotation.ParametersAreNonnullByDefault
package com.mikesamuel.cil.ast.passes.reflect;
