package com.mikesamuel.cil.parser;

import javax.annotation.Nullable;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.mikesamuel.cil.ast.BaseNode;
import com.mikesamuel.cil.ptree.Tokens;

/**
 * State of a {@link ParSer#forceFit} operation.
 */
public final class ForceFitState {
  /** Parts to fit together. */
  public final ImmutableList<FitPart> parts;
  /** Ways of fitting parts together thus far. */
  public final ImmutableSet<PartialFit> fits;

  /** */
  public ForceFitState(Iterable<? extends FitPart> parts) {
    this(parts, ImmutableSet.of(new PartialFit(0, null)));
  }

  /** */
  ForceFitState(
      Iterable<? extends FitPart> parts,
      Iterable<? extends PartialFit> fits) {
    this.parts = ImmutableList.copyOf(parts);
    this.fits = ImmutableSet.copyOf(fits);
  }

  /** Like this but with the given fits instead. */
  public ForceFitState withFits(Iterable<? extends PartialFit> newFits) {
    return new ForceFitState(parts, newFits);
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((fits == null) ? 0 : fits.hashCode());
    result = prime * result + ((parts == null) ? 0 : parts.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    ForceFitState other = (ForceFitState) obj;
    if (fits == null) {
      if (other.fits != null) {
        return false;
      }
    } else if (!fits.equals(other.fits)) {
      return false;
    }
    if (parts == null) {
      if (other.parts != null) {
        return false;
      }
    } else if (!parts.equals(other.parts)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "(ForceFitState " + parts + ")";
  }


  /**
   * A part of the content of an AST.
   */
  public static final class PartialFit {
    /** Index into the force state's fit parts. */
    public final int index;
    /** For each non-fixed fit part in parts[0:index), its resolution. */
    public @Nullable SList<BaseNode> resolutions;
    /** Cached hashCode computation. */
    private int hc;

    /** */
    public PartialFit(int index, @Nullable SList<BaseNode> resolutions) {
      this.index = index;
      this.resolutions = resolutions;
    }

    @Override
    public int hashCode() {
      final int prime = 31;
      if (hc == 0) {
        int result = 1;
        result = prime * result + index;
        result = prime * result + ((resolutions == null)
            ? 0 : resolutions.hashCode());
        hc = result == 0 ? -1 : hc;
      }
      return hc;
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      }
      if (obj == null) {
        return false;
      }
      if (getClass() != obj.getClass()) {
        return false;
      }
      PartialFit other = (PartialFit) obj;
      if (hc != 0 && other.hc != 0 && hc != other.hc) { return false; }
      if (index != other.index) {
        return false;
      }
      if (resolutions == null) {
        if (other.resolutions != null) {
          return false;
        }
      } else if (!resolutions.equals(other.resolutions)) {
        return false;
      }
      return true;
    }

    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder();
      sb.append("(Fit #");
      sb.append(index);

      String sep = " [";
      for (BaseNode resolution : SList.forwardIterable(resolutions)) {
        sb.append(sep);
        sep = ", ";
        sb.append(resolution.getNodeType());
      }
      if (", ".equals(sep)) { sb.append(']'); }
      return sb.append(')').toString();
    }

    /** A partial fit like this but with the index incremented. */
    public PartialFit advance() {
      return new PartialFit(index + 1, resolutions);
    }

    /**
     * A partial fit like this but with the index incremented, and the
     * index-th part resolved to the given node.
     */
    public PartialFit advanceAndResolve(BaseNode resolution) {
      return new PartialFit(index + 1, SList.append(resolutions, resolution));
    }
  }


  /**
   * A part in a sequence of parts of an AST's content.
   */
  public static abstract class FitPart {
    private FitPart() {
    }

    /**
     * A fit part that is a child that needs no additional wrapping.
     */
    @SuppressWarnings("synthetic-access")
    public static FixedNode fixedNode(BaseNode child) {
      return new FixedNode(child);
    }

    /**
     * An interpolated value that may need wrapping and type conversion to fit
     * into the parent node's child list.
     */
    @SuppressWarnings("synthetic-access")
    public static InterpolatedValue interpolatedValue(Object value) {
      return new InterpolatedValue(value);
    }

    @Override
    public abstract boolean equals(Object obj);

    @Override
    public abstract int hashCode();

    @Override
    public abstract String toString();
  }

  /**
   * A fit part that is a child that needs no additional wrapping.
   */
  public static final class FixedNode extends FitPart {
    /**
     * A child node that needs no additional wrapping.
     */
    public final BaseNode child;

    /** */
    @SuppressWarnings("synthetic-access")
    private FixedNode(BaseNode child) {
      this.child = child;
    }

    @Override
    public int hashCode() {
      final int prime = 31;
      int result = 1;
      result = prime * result + ((child == null) ? 0 : child.hashCode());
      return result;
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      }
      if (obj == null) {
        return false;
      }
      if (getClass() != obj.getClass()) {
        return false;
      }
      FixedNode other = (FixedNode) obj;
      if (child == null) {
        if (other.child != null) {
          return false;
        }
      } else if (!child.equals(other.child)) {
        return false;
      }
      return true;
    }

    @Override
    public String toString() {
      return "(!" + child.getNodeType() + ")";
    }
  }

  /**
   * An interpolated value that may need wrapping and type conversion to fit
   * into the parent node's child list.
   */
  public static final class InterpolatedValue extends FitPart {
    /**
     * An interpolated value that may need wrapping and type conversion to fit
     * into the parent node's child list.
     */
    public final Object value;

    /** */
    @SuppressWarnings("synthetic-access")
    private InterpolatedValue(Object value) {
      this.value = value;
    }

    @Override
    public int hashCode() {
      final int prime = 31;
      int result = 1;
      result = prime * result + ((value == null) ? 0 : value.hashCode());
      return result;
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      }
      if (obj == null) {
        return false;
      }
      if (getClass() != obj.getClass()) {
        return false;
      }
      InterpolatedValue other = (InterpolatedValue) obj;
      if (value == null) {
        if (other.value != null) {
          return false;
        }
      } else if (!value.equals(other.value)) {
        return false;
      }
      return true;
    }

    @Override
    public String toString() {
      return (
          "(~"
          + (value instanceof CharSequence
             ? Tokens.encodeString((CharSequence) value)
             : value instanceof BaseNode
             ? ((BaseNode) value).getNodeType()
             : value)
          + ")");
    }
  }
}
