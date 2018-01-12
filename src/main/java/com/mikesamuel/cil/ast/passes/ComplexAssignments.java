package com.mikesamuel.cil.ast.passes;

import java.util.EnumMap;

import javax.annotation.Nullable;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.mikesamuel.cil.ast.j8.AdditiveExpressionNode;
import com.mikesamuel.cil.ast.j8.AdditiveOperatorNode;
import com.mikesamuel.cil.ast.j8.AndExpressionNode;
import com.mikesamuel.cil.ast.j8.AssignmentOperatorNode;
import com.mikesamuel.cil.ast.j8.ExclusiveOrExpressionNode;
import com.mikesamuel.cil.ast.j8.InclusiveOrExpressionNode;
import com.mikesamuel.cil.ast.j8.J8BaseNode;
import com.mikesamuel.cil.ast.j8.J8NodeVariant;
import com.mikesamuel.cil.ast.j8.MultiplicativeExpressionNode;
import com.mikesamuel.cil.ast.j8.MultiplicativeOperatorNode;
import com.mikesamuel.cil.ast.j8.ShiftExpressionNode;
import com.mikesamuel.cil.ast.j8.ShiftOperatorNode;

/**
 * Utilities related to complex assignments like {@code +=}.
 *
 * @see AssignmentOperatorNode
 */
public final class ComplexAssignments {
  private ComplexAssignments() {}

  /** Op variants per assignment operator. */
  public static final ImmutableMap<AssignmentOperatorNode.Variant, OpVariants>
      ASSIGNMENT_OPERATOR_TO_BINARY_OPERATOR_VARIANT;

  /**
   * Encapsulates additional information about an assignment operator.
   */
  public static final class OpVariants {
    /**
     * The variant used to build an operation that contains a left operand,
     * maybe an operator node, and a right operand.
     */
    public final J8NodeVariant operationVariant;
    /** If not null, the variant for the infix operator node. */
    public final @Nullable J8NodeVariant operatorVariant;

    OpVariants(
        J8NodeVariant operationVariant,
        @Nullable J8NodeVariant operatorVariant) {
      this.operationVariant = operationVariant;
      this.operatorVariant = operatorVariant;
    }

    /**
     * Build an expression that computes the effective right hand
     * side of a complex assignment.
     */
    public J8BaseNode buildNode(
        J8BaseNode leftOperand, J8BaseNode rightOperand) {
      ImmutableList.Builder<J8BaseNode> children = ImmutableList.builder();
      children.add(leftOperand);
      if (operatorVariant != null) {
        children.add(operatorVariant.buildNode(ImmutableList.of()));
      }
      children.add(rightOperand);
      return operationVariant.buildNode(children.build());
    }
  }

  static {
    EnumMap<AssignmentOperatorNode.Variant, OpVariants> m = new EnumMap<>(
        AssignmentOperatorNode.Variant.class);

    // TODO: It would be nice to be able to infer this by reflecting over the
    // ptree.
    m.put(AssignmentOperatorNode.Variant.AmpEq,
        new OpVariants(
            AndExpressionNode.Variant.AndExpressionAmpEqualityExpression,
            null));
    m.put(AssignmentOperatorNode.Variant.DshEq,
        new OpVariants(
            AdditiveExpressionNode.Variant
            .AdditiveExpressionAdditiveOperatorMultiplicativeExpression,
            AdditiveOperatorNode.Variant.Dsh));
    m.put(AssignmentOperatorNode.Variant.PlsEq,
        new OpVariants(
            AdditiveExpressionNode.Variant
            .AdditiveExpressionAdditiveOperatorMultiplicativeExpression,
            AdditiveOperatorNode.Variant.Pls));
    m.put(AssignmentOperatorNode.Variant.FwdEq,
        new OpVariants(
            MultiplicativeExpressionNode.Variant
            .MultiplicativeExpressionMultiplicativeOperatorUnaryExpression,
            MultiplicativeOperatorNode.Variant.Fwd));
    m.put(AssignmentOperatorNode.Variant.PctEq,
        new OpVariants(
            MultiplicativeExpressionNode.Variant
            .MultiplicativeExpressionMultiplicativeOperatorUnaryExpression,
            MultiplicativeOperatorNode.Variant.Pct));
    m.put(AssignmentOperatorNode.Variant.StrEq,
        new OpVariants(
            MultiplicativeExpressionNode.Variant
            .MultiplicativeExpressionMultiplicativeOperatorUnaryExpression,
            MultiplicativeOperatorNode.Variant.Str));
    m.put(AssignmentOperatorNode.Variant.Gt2Eq,
        new OpVariants(
            ShiftExpressionNode.Variant
            .ShiftExpressionShiftOperatorAdditiveExpression,
            ShiftOperatorNode.Variant.Gt2));
    m.put(AssignmentOperatorNode.Variant.Gt3Eq,
        new OpVariants(
            ShiftExpressionNode.Variant
            .ShiftExpressionShiftOperatorAdditiveExpression,
            ShiftOperatorNode.Variant.Gt3));
    m.put(AssignmentOperatorNode.Variant.Lt2Eq,
        new OpVariants(
            ShiftExpressionNode.Variant
            .ShiftExpressionShiftOperatorAdditiveExpression,
            ShiftOperatorNode.Variant.Lt2));
    m.put(AssignmentOperatorNode.Variant.HatEq,
        new OpVariants(
            ExclusiveOrExpressionNode.Variant
            .ExclusiveOrExpressionHatAndExpression,
            null));
    m.put(AssignmentOperatorNode.Variant.PipEq,
        new OpVariants(
            InclusiveOrExpressionNode.Variant
            .InclusiveOrExpressionPipExclusiveOrExpression,
            null));

    ASSIGNMENT_OPERATOR_TO_BINARY_OPERATOR_VARIANT =
        Maps.immutableEnumMap(m);
  }

}
