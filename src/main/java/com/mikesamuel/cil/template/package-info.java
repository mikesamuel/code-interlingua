/**
 * Classes related to AST template parsing and processing.
 *
 * <p>
 * AST Templates are Java8 source files with some nonstandard syntax.
 * {@code <%...%>} sections delimit
 * {@link com.mikesamuel.cil.ast.NodeType#TemplateDirective}s and
 * {@link com.mikesamuel.cil.ast.NodeType#TemplateInterpolation}s.
 *
 * <p>
 * The Java lexical structure is the same except that {@code <%} and {@code %>}
 * are recognized as additional punctuation tokens which should not affect any
 * valid Java programs.
 * <p>This means that Java tokens like comments are unaffected by template
 * instructions: {@code // A java comment <% not a template instruction %>}
 * as are strings:
 * {@code static final String S = "<% not a template instruction %>"}.
 * <p>Java defines constant expressions to include concatenation so
 * {@code static final String S = "foo" + <% rest of string %>;} allows
 * adding characters to a constant string.
 * <p>Since the lexical structure and lexical pre-processing are unaffected,
 * {@code &#x5c;u003c% template instruction %&#x5c;u003e} is a template
 * instruction.
 *
 * <p>
 * Template instructions can contain Java expressions, which are evaluated in
 * a template scope which is separate from the scopes of the Java compilation
 * unit being produced.
 *
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
 * <p>
 * TODO: The template can be executed to produce an AST without template
 * instructions from an input.
 */
@javax.annotation.ParametersAreNonnullByDefault
package com.mikesamuel.cil.template;
