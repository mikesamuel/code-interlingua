package com.mikesamuel.cil.ast.j8.traits;

import com.mikesamuel.cil.ast.NodeI;
import com.mikesamuel.cil.ast.j8.J8BaseNode;
import com.mikesamuel.cil.ast.j8.J8NodeType;
import com.mikesamuel.cil.ast.j8.J8NodeVariant;

interface J8Trait extends NodeI<J8BaseNode, J8NodeType, J8NodeVariant> {
  // Supplies type variable bindings.
}
