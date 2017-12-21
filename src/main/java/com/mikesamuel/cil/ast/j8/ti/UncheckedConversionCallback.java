package com.mikesamuel.cil.ast.j8.ti;

import com.mikesamuel.cil.ast.meta.TypeSpecification;

interface UncheckedConversionCallback {
  void uncheckedConversionOccurred(TypeSpecification s, TypeSpecification t);
}
