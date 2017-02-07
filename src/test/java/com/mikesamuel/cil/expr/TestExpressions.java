package com.mikesamuel.cil.expr;

import java.util.List;

import com.google.common.collect.ImmutableList;

/**
 * This class is compiled when tests are compiled, but the source is also
 * available as a resource, so the field initializer expressions can be
 * evaluated and the results compared to the authoritative javac-compiled
 * version.
 */
@SuppressWarnings("javadoc")
public final class TestExpressions {
  public static final int SUM = 2 + 3;
  public static final int SUB = 1 - 1 - 1;  // Obliquely test associativity.
  public static final int[] INTS = { 1, 2, 3 };
  public static int accumulator = 0;
  public static final int ORDER_OF_OPS
    = INTS[(accumulator++)]
    = INTS[--accumulator]
    = INTS[++accumulator]--;
  // TODO: do static accesses like (++x).MAX_VALUE actually involve side effects
  public static final int MUL = SUM * SUB;
  public static final int TILD = ~(SUM * SUB);
  public static final int OR = TILD | 0xABCDEF01;
  public static final long XOR = -2 ^ OR;
  public static final int ONE_THOUSAND = 1_000;
  public static final double D = 1.5;
  public static final double[] DOUBLE_DIVS = {
    D / Math.PI, Math.PI / D, D / 0, 0 / Math.PI, 0. / .0,
    -1.0 / 0.0, 1.0 / -0.0, -1.0 / -0.0, 1.0 / -0,
  };
  // If the widening conversion is done after negating the right hand side, then
  // this will be the same as 4L + Integer.MIN_VALUE which is the wrong result.
  public static final long LIMIT_SUB = 4L - Integer.MIN_VALUE;

  public static final float F_P1 = 1.0F;
  public static final float F_N1 = -1.0F;
  public static final float F_P0 = 0F;
  public static final float F_N0 = -0F;
  public static final float[] FLOAT_DIVS = {
    1 / (F_P1 + F_N1), 1 / (F_N1 + F_P1),
    F_P0 / F_P0,
    F_P1 / F_P1, F_P1 / F_N1, F_P1 / F_P0, F_P1 / F_N0,
    F_N1 / F_P1, F_N1 / F_N1, F_N1 / F_P0, F_N1 / F_N0,
    F_P0 / F_P1, F_P0 / F_N1, F_P0 / F_P0, F_P0 / F_N0,
    F_N0 / F_P1, F_N0 / F_N1, F_N0 / F_P0, F_N0 / F_N0,
  };

  public static final short S = (short) 0xABCD;
  public static final short T = (short) 0x7012;
  public static final int S_MUL_T = S * T;
  public static final int S_DIV_T = S / T;
  public static final int S_MIN_X = S - 'x';

  public static final Object[] OS = {
    1 + 1, "1" + 1, 1 + "1", "1" + "1", "1" + null, null + "1",
    FLOAT_DIVS.length % DOUBLE_DIVS.length,
  };

  public static int accumMultiAssign = 1379;
  public static final int[] ACCUM_MULTI_ASSIGN_STATES = new int[] {
    accumMultiAssign += 2,
    accumMultiAssign -= 3,
    accumMultiAssign *= 4,
    accumMultiAssign /= 5,
    accumMultiAssign %= 129,
    accumMultiAssign ^= 1025,
    accumMultiAssign &= -12,
    accumMultiAssign >>= 3,
    accumMultiAssign >>= 1,
    accumMultiAssign <<= 4,
    accumMultiAssign |= 65537,
    accumMultiAssign = accumMultiAssign--,
  };

  public static final String APPENDS = new StringBuilder(Integer.valueOf("100"))
    .append("foo").append(123).append('\n').appendCodePoint(0x41)
    .toString();

  public static int actualAccumulator;
  public static final List<Object> LIST = ImmutableList.of(
      actualAccumulator += 2,
      actualAccumulator *= 39,
      ++actualAccumulator,
      actualAccumulator++);

  public static final List<Object> ANOTHER_LIST = ImmutableList.copyOf(
      new Double[] { 1., 2., 3d, Math.E });

  public static final Object NULL = null;

  public static int shouldBeZero = 0;

  public static final boolean B0 = Math.PI > 3;
  public static final boolean B1 = Math.E > 3;
  @SuppressWarnings("unused")
  public static final boolean[] BOOLS = {
      B0 || dereference(null),
      B0 || ++shouldBeZero == 0,
      B1 || B0,
      B1 && dereference(null),
      B1 || ++shouldBeZero == 0,
      B0 && B1,
      !B0, !B1,
      !!B1,
      B0 ^ B1,
      B0 | B1,
      B0 & B1,
      B0 || Boolean.TRUE,
      Boolean.TRUE && B1,
      !Boolean.valueOf("false"),
  };

  public static final String[][][] MULTI_DIM_ARRAY = new String[3][2][];

  public static final Object[] HOOK_RESULTS = {
      B0 ? true : TestExpressions.dereference((Boolean) null),
      B1 ? TestExpressions.dereference((Boolean) null) : false,
      Boolean.TRUE ? 0 : 1,
      !B0 ? "foo" : "bar",
  };

  public static final <T> boolean dereference(T x) {
    return x.equals(Boolean.TRUE);
  }

  public static void main(String[] argv) {
    // Allow running from the command line so we can easily test that the class
    // initializes properly.
    System.err.println("Hi");
    System.err.println("Bye");
  }
}
