package com.mikesamuel.cil.ast.passes;

import java.lang.reflect.Modifier;

import com.mikesamuel.cil.ast.j8.ModifierNode;

final class ModifierNodes {
  static int modifierBits(ModifierNode.Variant modVariant) {
    switch (modVariant) {
      case Abstract:     return Modifier.ABSTRACT;
      case Annotation:   return 0;
      case Default:      return 0;
      case Final:        return Modifier.FINAL;
      case Native:       return Modifier.NATIVE;
      case Private:      return Modifier.PRIVATE;
      case Protected:    return Modifier.PROTECTED;
      case Public:       return Modifier.PUBLIC;
      case Static:       return Modifier.STATIC;
      case Strictfp:     return Modifier.STRICT;
      case Synchronized: return Modifier.SYNCHRONIZED;
      case Transient:    return Modifier.TRANSIENT;
      case Volatile:     return Modifier.VOLATILE;
    }
    throw new AssertionError(modVariant);
  }
}
