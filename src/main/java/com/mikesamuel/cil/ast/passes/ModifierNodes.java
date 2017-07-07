package com.mikesamuel.cil.ast.passes;

import java.lang.reflect.Modifier;

import javax.annotation.Nullable;

import com.google.common.base.Preconditions;
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

  public static @Nullable ModifierNode.Variant modifierVariant(int bit) {
    Preconditions.checkArgument(bit != 0 && (bit & (bit - 1)) == 0);
    switch (bit) {
      case Modifier.ABSTRACT:     return ModifierNode.Variant.Abstract;
      case Modifier.FINAL:        return ModifierNode.Variant.Final;
      case Modifier.INTERFACE:    return null;
      case Modifier.NATIVE:       return ModifierNode.Variant.Native;
      case Modifier.PRIVATE:      return ModifierNode.Variant.Private;
      case Modifier.PROTECTED:    return ModifierNode.Variant.Protected;
      case Modifier.PUBLIC:       return ModifierNode.Variant.Public;
      case Modifier.STATIC:       return ModifierNode.Variant.Static;
      case Modifier.STRICT:       return ModifierNode.Variant.Strictfp;
      case Modifier.SYNCHRONIZED: return ModifierNode.Variant.Synchronized;
      case Modifier.TRANSIENT:    return ModifierNode.Variant.Transient;
      case Modifier.VOLATILE:     return ModifierNode.Variant.Volatile;
    }
    throw new IllegalArgumentException(Modifier.toString(bit));
  }
}
