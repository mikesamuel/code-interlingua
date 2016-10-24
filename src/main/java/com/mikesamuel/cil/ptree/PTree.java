package com.mikesamuel.cil.ptree;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.mikesamuel.cil.parser.ParSer;
import com.mikesamuel.cil.parser.ParSerable;
import com.mikesamuel.cil.ast.NodeVariant;

/**
 * ParSers that work based on a JLS style grammar.
 */
public final class PTree {

  /** A builder for a tree of the given kind. */
  @SuppressWarnings("synthetic-access")
  public static Builder builder(Kind k) { return new Builder(k); }

  /** The kind of node in a parse tree. */
  public enum Kind {
    // Inner node kinds.
    /** Set-union of languages. */
    ALTERNATION,
    /** Concatenation of languages. */
    SEQUENCE,
    /** The concatenation of child languages or the empty string. */
    OPTIONAL,
    /** Kleene star.  A repetition of the child languages. */
    REPEATED,
    // Leaves
    /** Matches literal text. */
    LITERAL,
    /** Delegates to a named production. */
    REFERENCE,
    /** Consumes no input and passes when its body fails. */
    NEGATIVE_LOOKAHEAD,
  }

  /**
   * A parSer that tries each variant in turn.
   */
  public static ParSerable nodeWrapper(
      final String name,
      final Class<? extends Enum<? extends NodeVariant>> variantClass) {
    return new ParSerable() {
      private Reference r;

      @Override
      public ParSer getParSer() {
        if (r == null) {
          r = new Reference(name, variantClass);
        }
        return r;
      }
    };
  }

  /* *
   * A parSer that generates {@link MatchEvent#push} and pop events during parse
   * and consume them during match and unparse operations.
   *
   * @param isLeftRecursive when this variant is directly left-recursive.
   *     Indirect left-recursion is not handled.
   *
  public static ParSerable variantWrapper(
      NodeVariant v, ParSerable p, boolean isLeftRecursive) {
    return new VariantWrapper(v, p, isLeftRecursive);
  }*/

  /**
   * A ParSer that assumes/verifies that it is dealing with a complete
   * input/output.
   * <p>
   * For parsing, this simply delegates, and then checks that there is no
   * remaining unparsed input.
   * <p>
   * For serializing, this simply delegates.  There is no need to put a
   * full-stop at the end of the output.
   * <p>
   * For matching, this simply delegates, and then checks that there are no
   * more input events.
   */
  public static ParSerable complete(ParSerable p) {
    return new Completer(p);
  }

  /**
   * Builder for a ParSer.
   */
  public static final class Builder {
    private final Kind kind;
    private final ImmutableList.Builder<ParSerable> parSerables
        = ImmutableList.builder();

    private Builder(Kind kind) {
      this.kind = kind;
    }

    /** Adds a child. */
    public Builder add(ParSerable parSerable) {
      parSerables.add(parSerable);
      return this;
    }

    /** Specifies that the text is matched literally. */
    public Builder leaf(String leafText, int ln, int co, int ix) {
      parSerables.add(Literal.of(leafText, ln, co, ix));
      return this;
    }

    /** Builds the ParSer. */
    public ParSerable build() {
      ImmutableList<ParSerable> ps = this.parSerables.build();
      switch (kind) {
        case ALTERNATION:
          return Alternation.of(ps);
        case LITERAL:
          Preconditions.checkState(ps.size() == 1);
          return ps.get(0);
        case OPTIONAL:
          return Alternation.of(
              ImmutableList.of(Concatenation.of(ps), Concatenation.EMPTY));
        case REFERENCE:
          Preconditions.checkState(ps.size() == 1);
          return ps.get(0);
        case REPEATED:
          return Repetition.of(Concatenation.of(ps));
        case SEQUENCE:
          return Concatenation.of(ps);
        case NEGATIVE_LOOKAHEAD:
          return Lookahead.of(Lookahead.Valence.NEGATIVE, Concatenation.of(ps));
      }
      throw new AssertionError(kind);
    }
  }
}
