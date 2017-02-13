/**
 * Classes related to AST template parsing and processing.
 *
 * <p>
 * AST Templates are Java8 source files with some nonstandard syntax
 * and which opt-in by including the
 * {@code import static com.mikesamuel.cil.HereBe._TEMPLATES_;}
 * which will not appear in the template output.
 *
 * <h3>Syntax</h3>
 * <p>In an AST template there are several kinds of non-standard syntax:
 * <ul>
 *   <li><code>(%...)</code> and <code>{%...%}</code> sections delimit
 *   {@link com.mikesamuel.cil.ast.NodeType#TemplateInterpolation}s.
 *   <li>Statement like constructions are prefixed with <code>%%</code>.
 *   <li><code>\foo.bar.Baz</code> is a quoted name, which instead of
 *   evaluating to the package, class, or value referenced, evaluates
 *   to a qualified name for the referent which can be subsituted into
 *   the AST.
 * </ul>
 *
 * <p>
 * <code>%%template name : { &hellip; %%}</code> defines a template
 * that can be invoked.
 *
 * <p>
 * <code>%%{ let ident<sub>0</sub> = expr, ident<sub>1</sub> = expr; &hellip; %%}</code>
 * starts a block and optionally declares variables visible to
 * expressions in template directives and interpolations in that
 * block.
 *
 * <p>
 * <code>%%if (expr) { &hellip; %%}</code> conditionally includes
 * the &hellip;.
 *
 * <p>
 * <code>%%for (ident : expr) { &hellip; %%}</code> evaluates
 * <code>expr</code> and repeatedly binds <code>ident</code>
 * to one of the values in the iterable <code>
 * the &hellip;.
 *
 * <p>
 * The Java lexical structure is the same except that <code>(%</code>,
 * <code>{%</code>, and <code>%%</code> are recognized as additional
 * punctuation tokens which should not affect any valid Java programs.
 * <p>This means that Java tokens like comments are unaffected by template
 * instructions: {@code // A java comment (% not a template instruction )}
 * as are strings:
 * {@code static final String S = "(% not a template instruction )"}.
 * <p>Java defines constant expressions to include concatenation so
 * {@code static final String S = "foo" + (% rest of string );} allows
 * adding characters to a constant string.
 * <p>Since the lexical structure and lexical pre-processing are unaffected,
 * {@code &#x5c;u0028% template instruction &#x5c;u0029} is a template
 * instruction.
 *
 * <h3>Pre-processing</h3>
 * <p>
 * When parsed in
 * {@linkplain com.mikesamuel.cil.parser.Input#allowNonStandardProductions nonstandard}
 * mode, special handling adds parse events for these template nodes in
 * approximately the right places.
 *
 * <p>
 * {@linkplain com.mikesamuel.cil.template.Templates#generalize post-processing}
 * of the event stream moves them to the right place.
 *
 * <h3>Template application</h3>
 * <p>
 * A template AST can be applied to an input bundle which provides
 * bindings for any free variables in template expressions.
 *
 * <p>
 * Template instructions can contain Java expressions, which are evaluated in
 * a template scope which is separate from the scopes of the Java compilation
 * unit being produced.
 * The scope includes the contents of the input bundle, any template variables
 * introduced via the {@code let} syntax described above, and any
 * {@code static final} field declarations that are in scope and which appear before
 * the template instruction and which are initialized
 * (directly, not via static initializers) to a valid constant expression that does
 * not refer to any constants defined outside the compilation unit.
 *
 * <h4>Order of application</h4>
 * <p>
 * Application proceeds based on the following conventions:<ol>
 *  <li>{@code let} declarations are evaluated on entry to a block left to
 *      right.
 *  <li>Nested template instructions are evaluated before the instruction that
 *      nests them.  Because of (1), a template instruction nested inside a
 *      directive in a block will happen after initialization of any {@code let}
 *      variables defined at the beginning of that block that are lexically
 *      before it.  If it is nested in a {@code let} name or initializer then
 *      that variable will not be available to its body.
 *  <li>Evaluation in a block of directives proceeds left to right.
 *  <li><code>%%template &hellip;%%}</code> blocks are evaluated before other
 *      blocks to make template declarations available to other blocks.
 *      Template declarations are effectively hoisted this way.
 *  <li>Within a template, non-template blocks are evaluated left-to-right.
 *  <li>Within an expression, normal Java rules apply.
 * </ol>
 * So in
 * <pre>
 * void f() {
 * <u>%%{
 *   let x = ..., y = ...;</y>
 *
 *   if ( <u>(%x)</u> ) {
 *     <u>{%foo(x, y)}</u>
 *   }
 * <u>%%}</u>
 * }
 *
 * <u>%%template foo(x, y) {</u>
 *   ...
 * <u>%%}</u>
 * </pre>
 * <p>
 * The <code>%%template</code> is first evaluated to bind {@code foo}.
 * Then the block is entered, and {@code x} and {@code y} declared
 * in that scope, then {@code x} is initialized, followed by {@code y}.
 * <p>
 * Application then proceeds to the body of the block, and {@code (%x)} is
 * evaluated and interpolated into the parse tree.
 * <p>
 * Finally, {@code %foo(x, y)} is evaluated and the free name {@code foo} and
 * since there is no local variable of that name, it resolves to the template
 * declaration and the template body is entered and the template result
 * interpolated in-situ.
 */
@javax.annotation.ParametersAreNonnullByDefault
package com.mikesamuel.cil.template;
