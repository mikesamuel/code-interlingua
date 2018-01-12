package com.mikesamuel.cil.ast.meta;

import static java.lang.annotation.ElementType.LOCAL_VARIABLE;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.SOURCE;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Marks a node that was synthesized by the
 * {@link com.mikesamuel.cil.ast.passes.synth.SyntheticMemberPass}.
 * Otherwise, the synthetic bit does not survive passes that scrub and
 * regenerate metadata.
 */
@Documented
@Retention(SOURCE)
@Target({ LOCAL_VARIABLE, METHOD })
public @interface Synthetic {
  // This interface declaration body left intentionally blank.
}
