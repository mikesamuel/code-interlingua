package com.mikesamuel.cil.expr;

import java.util.Map;

import javax.annotation.Nullable;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import com.mikesamuel.cil.ast.meta.Name;

/**
 * Maps local and ambiguous names to values.
 * <p>
 * Ambiguous names can appear in template contexts which have not been run
 * through the disambiguation pass.
 */
public final class Locals<VALUE> {
  /** Outer locals that might be masked by names declared in this scope. */
  public final @Nullable Locals<VALUE> outer;
  private final Map<Name, VALUE> values = Maps.newLinkedHashMap();
  private final Map<String, Name> identToLocallyDeclaredName =
      Maps.newLinkedHashMap();
  private final Map<String, Function<? super VALUE, ? extends VALUE>> coercions
      = Maps.newLinkedHashMap();

  /** */
  public Locals() {
    this(null);
  }

  /** */
  public Locals(@Nullable Locals<VALUE> outer) {
    this.outer = outer;
  }

  /** Adds a binding for the given name. */
  public void declare(
      Name name, Function<? super VALUE, ? extends VALUE> coercion) {
    Preconditions.checkArgument(
        name.type == Name.Type.LOCAL
        || name.type == Name.Type.AMBIGUOUS
        );
    Preconditions.checkArgument(
        !identToLocallyDeclaredName.containsKey(name.identifier));
    identToLocallyDeclaredName.put(name.identifier, name);
    coercions.put(name.identifier, coercion);
    VALUE zeroValue = coercion.apply(null);
    values.put(name, zeroValue);
  }

  private Name fixName(Name name) {
    if (name.type == Name.Type.AMBIGUOUS) {
      Name nm = identToLocallyDeclaredName.get(name.identifier);
      if (nm != null) { return nm; }
    }
    return name;
  }

  /**
   * The value of the named local or defaultValue if none such exists.
   */
  public VALUE get(Name name, VALUE defaultValue) {
    Name uname = fixName(name);
    if (values.containsKey(uname)) {
      return values.get(uname);
    }
    if (outer != null) {
      return outer.get(name, defaultValue);
    }
    return defaultValue;
  }

  /**
   * Sets the value associated with the named local variable.
   *
   * @return the coerced value.  For example, if a short value is assigned to
   *    an int local, the short is promoted to an int and returned.
   */
  public VALUE set(Name name, VALUE value) throws IllegalArgumentException {
    Name uname = fixName(name);
    if (values.containsKey(uname)) {
      Function<? super VALUE, ? extends VALUE> coercion =
          coercions.get(uname.identifier);
      VALUE cvalue = coercion.apply(value);
      values.put(uname, cvalue);
      return cvalue;
    }
    if (outer != null) {
      return outer.set(name, value);
    }
    throw new IllegalArgumentException("Undeclared name " + name);
  }

  /**
   * True if the name corresponds to a
   */
  public boolean has(Name name) {
    Name uname = fixName(name);
    return values.containsKey(uname) || outer != null && outer.has(name);
  }
}
