package com.mikesamuel.cil.ast.passes.flatten;

import java.util.logging.Logger;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.mikesamuel.cil.ast.j8.J8BaseNode;
import com.mikesamuel.cil.ast.j8.J8FileNode;
import com.mikesamuel.cil.ast.meta.MetadataBridge;
import com.mikesamuel.cil.ast.meta.StaticType.TypePool;
import com.mikesamuel.cil.ast.meta.TypeInfoResolver;
import com.mikesamuel.cil.ast.passes.AbstractPass;

/**
 * Converts a hierarchy of nested types into one without.
 * <p>
 * This performs the transforms described below, then strips all type
 * metadata from the AST so that the common passes can be rerun with the
 * new declarations.
 * </p>
 *
 * <p>
 * We need to coordinate several sets of changes, and some changes require
 * other compensating changes elsewhere.  Instead of trying to do everything
 * in one pass, we start by fixing up large scale structures and move to
 * the smaller:
 * </p>
 * <ol>
 *   <li>Operate on type declarations.</li>
 *   <li>Operate on type members.</li>
 *   <li>Operate on statements and expressions.</li>
 * </ol>
 * <p>We try to add new constructs before modifying code to use them.</p>
 *
 * <h2>Scan identifiers</h2>
 * <p>We need a set of used identifiers so that we can allocate synthetic
 * names without masking or shadowing existing names.</p>
 *
 * Inputs:
 * <ul>
 *   <li>The compilation units with type info, type pool, etc.</li>
 * </ul>
 *
 * Outputs:
 * <ul>
 *   <li>A list of all type declarations excluding type parameter
 *   declarations.</li>
 *   <li>For each type declarations, identifiers which are visible
 *   within it, including inherited identifiers.</li>
 *   <li>A mapping from original (bumpy) type names to new (flat) type names.
 * </ul>
 * The type declaration list is ordered such that
 * <ul>
 *   <li>A type declaration appears after any it inherits from via
 *      {@code extends} or {@code implements}.</li>
 *   <li>A type declaration appears after any it inherits scope from.
 *      I.e., a non-static inner class appears after its outer class.</li>
 * </ul>
 * <p>This ordering simplifies implementation of later mini-passes.</p>
 * <p>Next, we allocate a flat name to each type declaration:</p>
 * <ol>
 *   <li>All top level declarations receive the same name.</li>
 *   <li>We try to rename named inner classes as {@code Outer$Inner}
 *     and where there is a conflict, give precedence to any {@code public}
 *     classes, and add a suffix to disambiguate.</li>
 *   <li>We generate names for anonymous and local classes that
 *     roughly match the name that their compiled {@code .class} file would
 *     have, a name like {@code Outer$1} for the first (in token scan order)
 *     anonymous class in {@code Outer}.</li>
 * </ol>
 *
 * <h2>Rename type declarations so that they are top level</h2>
 * <p>For example,</p>
 * {@code
 * class Outer<O> {
 *   class Inner<I> {}
 * }
 * }
 * becomes
 * {@code
 * class Outer<O> {}
 * class Outer$Inner<I> {}  // <O> is no longer in scope.  Fix that later.
 * }
 *
 * Outputs:
 * <ul>
 *   <li>Type declarations with flat names.
 *   <li>A list of compilation units, one per type declaration.
 * </ul>
 *
 * <h2>Add outer class type parameters to declarations</h2>
 * <p>When an outer class has type parameters, its non-static inner classes
 * need to inherit them.
 * This includes non-static inner classes declared in subclasses of the
 * outer class.</p>
 * {@code
 * class Outer<O> {}
 * class Outer$Inner<I> {}
 * }
 * becomes
 * {@code
 * class Outer<O> {}
 * class Outer$Inner<O, I> {}
 * }
 * <p>
 * We prepend outer type parameters to the inner type's parameter list.
 * We could append but that would be inconsistent with how we later handle
 * threading extra method parameters to constructors.
 * We cannot append method parameters without breaking variadic methods.
 * </p>
 *
 * Outputs:
 * <ul>
 *   <li>For each type declaration, a record detailing for each flat
 *   type parameter the bumpy type parameter from which it was derived.</p>
 * </ul>
 *
 * <h2>Add synthetic accessors for private fields accessed cross class</h2>
 * TODO
 * {@code
 * class Outer {
 *   private int f() { return 42; };
 *   class Inner {
 *     int f() { return Outer.this.f(); }
 *   }
 * }
 * }
 * <p>{@code Outer.this.f()} accesses a private method cross class which
 * is disallowed, so normally javac addes synthetic access methods.</p>
 * <p>Simply making the method package-private would cause it to take part
 * in masking and overriding which is a large change in semantics that
 * would require additional compensatory methods to compensate for.</p>
 *
 * <h2>Add fields for outer {@code this} values</h2>
 * <p>The previous output of an earlier step:</p>
 * {@code
 * class Outer<O> {}
 * class Outer$Inner<O, I> {}
 * }
 * becomes
 * {@code
 * class Outer<O> {}
 * class Outer$Inner<O, I> {
 *   private Outer<O, I> this__Outer;  // not initialized yet
 * }
 * }
 * <p>If a non-static inner class extends a non-static inner class, it may
 * have more than one {@code this__Outer}.</p>
 *
 * Outputs:
 * <ul>
 *   <li>For each type declaration, a map between allocated fields and
 *     '(closed_over BumpyOuterType.this).</li>
 * </ul>
 *
 * <h2>Add fields for captured locals of local classes</h2>
 * <p>
 * Local classes are declared in method bodies and can close over
 * effectively final local variables in scope where they are declared.
 * Consider the following class:</p>
 * {@code
 * class C {
 *   static Object f(int x) {
 *     static class Foo {
 *       public String toString() { return "" + x; }
 *     }
 *     return new Foo();
 *   }
 * }
 * }
 * <p>The previous mini-passes pull out the inner class, but since
 * {@code f(int)} is static, do not add a {@code this} reference so we get:
 * </p>
 * {@code
 * class C {
 *   static Object f(int x) {
 *     // Eventually this constructor use will pass x for captured__x.
 *     return new Foo();
 *   }
 * }
 * class Foo {
 *   final int captured__x;  // Not yet initialized
 *   public String toString() { return "" + x; }
 *   // Eventually the read x will be rewritten to closedOver__x
 * }
 * }
 *
 * Outputs:
 * <ul>
 *   <li>For each type declaration, a map between allocated fields and
 *     '(closed_over LocalVariable).</li>
 * </ul>
 *
 * <h2>Modify constructors to initialize outer {@code this} values
 * and captured locals</h2>
 * <p>Now we add implicit constructors if necessary and prepend argument
 * lists.</p>
 * <p>Remember the previously mentioned nested class:
 * {@code
 * class Outer<O> {}
 * class Outer$Inner<O, I> {
 *   private Outer<O, I> this__Outer;  // not initialized yet
 * }
 * }
 * <p>
 * We need to modify every constructor (implied or explicit) to
 * initialize {@code outer_this}.  There is a wrinkle though:
 * in Java, references to the containing instance are initialized
 * before control passes to the super constructor.
 * </p>
 * {@code
 *   Outer$Inner(Outer<O, I> this__Outer) {
 *     super();
 *     // TOO LATE if super() calls a method that depends on outer__this.
 *     this.this__Outer = this__Outer;
 *   }
 * }
 * <p>Instead, we add a side-effecting callout when making arguments to
 * pass to the constructor which actually initializes the object.
 * We generate a {@code private} constructor that takes the same
 * arguments as the un-augmented constructor but also takes additional
 * boolean arguments to disambiguate it from existing constructors, and
 * possibly a byte argument if any constructor's has a variadic
 * parameter after zero or more leading boolean parameters to avoid
 * ambiguity.
 * </p>
 * {@code
 *   Outer$Inner(Outer<O, I> this__Outer) {
 *     this(initClosedOverState(this__Outer));
 *   }
 *
 *   private Outer$Inner(boolean b0) {
 *     // super call happens after b0 is computed which happens after
 *     // initClosedOverState assigns this__Outer.
 *     super();
 *     // If Inner's zero-argument constructor had done any work,
 *     // it would go here.
 *   }
 *
 *   private boolean initClosedOverState(Outer<O, I> this__Outer) {
 *     // Outer.this cannot be null
 *     if (this__Outer == null) { throw new NullPointerException(); }
 *     this.this__Outer = this__Outer;
 *     return false;
 *   }
 * }
 *
 * <p>
 * When a constructor explicitly delegates to another constructor, we don't
 * transform as above, but instead just thread parameters through to the
 * other constructor.
 * </p>
 * <p>
 * There is a rarely used form of super call that requires special
 * attention:
 * </p>
 * {@code
 *   private static final Outer MY_OUTER = new Outer();
 *
 *   Outer$Inner(Object x) {
 *     MY_OUTER.super(x);
 *   }
 * }
 * <p>
 * That constructor uses an explicit {@code super} call to set the
 * outer class instance before delegating to the super class's constructor.
 * In this case, we do not add a parameter for the outer this value.
 * We still need to take any other captured state, and do our shenanigans to
 * get it initialized in the right order:
 * </p>
 * {@code
 *   private static final Outer MY_OUTER = new Outer();
 *   private Outer this__Outer;
 *
 *   Outer$Inner(Object x) {
 *     this(initClosedOverState(MY_OUTER), x);
 *   }
 *
 *   private Outer$Inner(boolean b0, Object x) {
 *     super(x);
 *   }
 *
 *   private boolean initClosedOverState(Outer this__Outer) {
 *     if (this__Outer == null) { throw new NullPointerException(); }
 *     this.this__Outer = this__Outer;
 *   }
 * }
 *
 * Outputs:
 * <ul>
 *   <li>Map from constructors to extra parameters needed</li>
 * </ul>
 *
 * <h2>Rewrite bare member accesses to use the right implicit receiver</h2>
 * <p>
 * In the code below, there are bare references to {@code x}, {@code y},
 * {@code f}, and {@code g}.
 * </p>
 * {@code
 * class Outer {
 *   int x;
 *   void f() { System.out.println("x=" + x); }
 *   class Inner {
 *     int y;
 *     void g() { System.out.println("x=" + x + ", y=" + y); }
 *     {
 *       f();
 *       g();
 *     }
 *   }
 * }
 * }
 * <p>
 * Once our classes explicitly close over state, we can rewrite bare
 * references.
 * </p>
 * {@code
 * class Outer {
 *   int x;
 *   void f() { System.out.println("x=" + this.x); }
 * }
 * class Outer$Inner {
 *   int y;
 *   private Outer this__Outer;  // See mini-passes above.
 *   void g() {
 *     // We rewrite bare x and y.
 *     System.out.println("x=" + this.this__Outer.x + ", y=" + this.y);
 *   }
 *   {
 *     // We rewrite bare method calls.
 *     this.this__Outer.f();
 *     this.g();
 *   }
 * }
 * }
 *
 * <h2>Add additional actual arguments to constructor uses</h2>
 * TODO
 * <p>We prepended arguments to constructors so that a class explicitly
 * captures all the state it implicitly closed over.
 * We find all constructor calls and augment them with the
 * additional arguments they use.
 * This includes super-constructor calls inside constructors.
 * </p>
 *
 * <h2>Convert bumpy names to flat names</h2>
 * <p>Above we discuss deriving flat-type-hierarchy names for user-defined
 * classes and how type parameters declared on outer classes are in-scope
 * for non-static inner classes.
 * <p>We need to rewrite type name references to use the new names.
 * <p>Since we expand the type parameter lists of inner classes, we also
 * need to expand the type argument list when such classes are referenced.
 * </p>
 *
 * <h2>Caveat: Builtin types</h2>
 * <p>We cannot flatten builtin types like {@code Map.Entry}, so we just
 * leave references to such types alone.</p>
 */
public final class FlattenPass
extends AbstractPass<ImmutableList<J8FileNode>> {

  final TypeInfoResolver r;

  FlattenPass(Logger logger, TypeInfoResolver r) {
    super(logger);
    this.r = r;
  }

  @Override
  public ImmutableList<J8FileNode> run(
      Iterable<? extends J8FileNode> fileNodes) {
    ForwardingTypeInfoResolver resolver = new ForwardingTypeInfoResolver(r);
    TypePool pool = new TypePool(resolver);

    PassState ps = new CollectTypeDeclarationsMiniPass(logger, pool, resolver)
        .run(ImmutableList.copyOf(Iterables.transform(
            fileNodes,
            new Function<J8FileNode, J8FileNode>() {
              @Override
              public J8FileNode apply(J8FileNode fn) {
                return (J8FileNode) fn.deepClone();
              }
            })));

    ImmutableList<J8FileNode> flatFileNodes =
        new FlattenTypeHierarchyMiniPass(logger, resolver)
        .run(ps);
    new InheritTypeParametersMiniPass(logger, pool).run(ps);
    new CaptureClosedOverStateMiniPass(logger, pool).run(ps);
    new InitializeClosedOverStateMiniPass(logger, pool).run(ps);
    new RewriteUsesOfClosedOverStateMiniPass(logger, pool).run(ps);
    new FlattenNamesMiniPass(logger, pool).run(ps);
    new ForwardClosedOverStateToConstructorsMiniPass(logger, pool)
    .run(ps);
    // TODO: add private accessors

    // Scrub type metadata which refers to bumpy types so that
    // the common passes can reinfer flat types.
    for (J8FileNode file : flatFileNodes) {
      ((J8BaseNode) file).transformMetadata(MetadataBridge.Bridges.ZERO, true);
    }
    return flatFileNodes;
  }

}
