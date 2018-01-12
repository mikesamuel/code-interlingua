package com.mikesamuel.cil.ast.passes.synth;

import java.util.logging.Logger;

import com.google.common.collect.ImmutableList;
import com.mikesamuel.cil.ast.j8.J8FileNode;
import com.mikesamuel.cil.ast.meta.MemberInfoPool;
import com.mikesamuel.cil.ast.meta.StaticType.TypePool;
import com.mikesamuel.cil.ast.passes.AbstractPass;
import com.mikesamuel.cil.ast.passes.MethodVariantPool;
import com.mikesamuel.cil.ast.passes.TypeNodeFactory;

/**
 * A pass that runs after the {@link com.mikesamuel.cil.ast.passes.TypingPass}
 * to add synthetic methods to classes.
 *
 * <p>
 * This pass adds three types of synthetic members.
 *
 * <h2>Synthetic Bridge Methods</h2>
 * <p>When a type declares a method that overrides or implements a super-type
 * mehod, but they have different method descriptors, we add a bridge method.
 * For example, in
 * {@code
 * interface I<T> {
 *   T f(T x);
 * }
 * class C implements I<String> {
 *   // Since <T> binds to String above f(String) implements f(T)
 *   public String f(String x) {...}
 * }
 * }
 * <p>The method {@code f} in {@code class C} has method descriptor
 * {@code (Ljava/lang/String;)Ljava/lang/String;} which does not match
 * (ignoring return type) the method descriptor for the corresponding method
 * in {@code interface I}: {@code (Ljava/lang/Object;)Ljava/lang/Object;}.</p>
 *
 * <p>We add a synthetic bridge method that will be reached when a call has
 * the static receiver type of I as in ({@code (new C()).f("")}).
 * The bridge method forwards to the user-defined implementation.</p>
 * {@code
 *   public Object f(Object x) { return f((String) x); }
 * }
 *
 * <h2>Private accessors</h2>
 * <p>JLS Example 6.6-5 explains {@code private}:</p>
 *
 * <blockquote>
 * A private class member or constructor is accessible only within the body of
 * the top level class (§7.6) that encloses the declaration of the member or
 * constructor
 * </blockquote>
 *
 * <p>JVM Spec 4.7.8 notes:</p>
 *
 * <blockquote>
 * The Synthetic attribute was introduced in JDK release 1.1 to support nested
 * classes and interfaces.
 * </blockquote>
 *
 * <p>We turn accesses to private members that are allowed between classes
 * defined in the same top-level class into accesses to package-private
 * methods or constructors.</p>
 *
 * {@code
 * public class Outer {
 *   private int x;
 *
 *   private void clear() { x = 0; }
 *
 *   public Inner inner() {
 *     return new Inner();  // Access to private constructor
 *   }
 *
 *   public class Inner {
 *     private Inner() {}
 *
 *     public void incr() {
 *       ++x;  // Private field set
 *     }
 *     public int x() {
 *       return x;
 *     }  // Private field read
 *     public void clear() {
 *       Outer.this.clear();  // Method call
 *     }
 *   }
 * }
 * }
 *
 * <p>In the above case we add the following and rewrite uses:</p>
 * {@code
 * public class Outer {
 *   void access$set$x(int newX) { this.x = x; }
 *   int access$get$x() { return this.x; }
 *   void access$clear() { clear(); }
 *   ...
 *
 *   public class Inner {
 *     Inner(boolean ign) { this(); }
 *     ...
 *   }
 * }
 * }
 *
 * <h2>Super method accesses</h2>
 * <p>
 * Super calls can be statically resolved to a particular method declaration.
 * Javac compiles super method accesses to the <i>invokespecial</i> instruction
 * instead of <i>invokestatic</i>, because <i>invokespecial</i> does not
 * participate in virtual dispatch but still carries {@code this}, but that's
 * not a distinction we need to preserve.
 * </p>
 *
 * <p>
 * If the destination of a super-call is in a user-defined class, then we can
 * rewrite it.
 * </p>
 * {@code
 * class Super {
 *   int f() { return 1; }
 * }
 * class Sub extends Super {
 *   int f() { return 2; }
 *   int g() { return super.f(); }
 * }
 * }
 * <p>We can rewrite the above to:</p>
 * {@code
 * class Super {
 *   int f() { return super$f(); }  // FORWARDS
 *   final int super$f() { return 1; }  // ADDED
 * }
 * class Sub extends Super {
 *   int f() { return 2; }
 *   int g() { return super$f(); }  // MODIFIED
 * }
 * }
 * <p>This requires adding synthetic {@code default} methods to interfaces
 * in some circumstances.</p>
 * <p>Super field accesses can be handled by casting the object reference
 * to the declaring type.</p>
 */
public final class SyntheticMemberPass
extends AbstractPass<ImmutableList<J8FileNode>> {
  final MemberInfoPool memberInfoPool;
  final MethodVariantPool methodVariantPool;
  final TypePool typePool;
  final TypeNodeFactory factory;

  protected SyntheticMemberPass(
      Logger logger,
      MethodVariantPool methodVariantPool,
      MemberInfoPool memberInfoPool) {
    super(logger);
    this.memberInfoPool = memberInfoPool;
    this.methodVariantPool = methodVariantPool;
    this.typePool = memberInfoPool.typePool;
    this.factory = new TypeNodeFactory(logger, typePool);
  }

  @Override
  public ImmutableList<J8FileNode> run(Iterable<? extends J8FileNode> files) {
    ImmutableList<J8FileNode> fs = ImmutableList.copyOf(files);
    Common c = new Common(logger, typePool, memberInfoPool, factory, fs);
    new AnalyzeMiniPass(c).run(fs);
    new BridgeBuilderMiniPass(c).addBridgeMethods();
    new AccessorMiniPass(c).addPrivateAccessors();
    new SuperAccessorsMiniPass(c).addSuperAccessors();
    new RewriteUsesMiniPass(c).run(fs);
    return fs;
  }
}