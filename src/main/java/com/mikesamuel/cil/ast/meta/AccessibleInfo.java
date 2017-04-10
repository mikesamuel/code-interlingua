package com.mikesamuel.cil.ast.meta;

import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Set;

import com.google.common.base.Optional;
import com.google.common.collect.Sets;

/**
 * Super class for a named item with limited visibility.
 */
public abstract class AccessibleInfo {
  /**
   * @see java.lang.reflect.Modifier
   */
  public final int modifiers;
  /**
   * The canonical name, or in the case of anonymous classes and initializers,
   * the ordinal following the canonical name of the containing element.
   */
  public final Name canonName;

  AccessibleInfo(int modifiers, Name canonName) {
    this.modifiers = modifiers;
    this.canonName = canonName;
  }


  /**
   * A predicate that determines whether a given canonical name is visible.
   *
   * @param origin the canonical name of the origin of the use.
   * @param typeInfoResolver used to figure out type inheritance relationships
   *    when computing the accessibility of {@code protected} members.
   */
  public boolean accessibleFrom(
      Name origin, TypeInfoResolver typeInfoResolver) {
    int accessControlMods = this.modifiers
        & (Modifier.PUBLIC | Modifier.PROTECTED | Modifier.PRIVATE);
    switch (accessControlMods) {
      case Modifier.PUBLIC:
        return true;
      case Modifier.PRIVATE:
        Name originTopLevelClass = origin.getTopLevelClass();
        return originTopLevelClass != null && originTopLevelClass.equals(
            canonName.getTopLevelClass());
      case Modifier.PROTECTED: {
        Name originPackage = origin.getPackage();
        if (originPackage != null
            && originPackage.equals(canonName.getPackage())) {
          return true;
        }
        // //docs.oracle.com/javase/specs/jls/se8/html/jls-6.html#jls-6.6.2
        Name containingClass = canonName.getContainingClass();
        if (containingClass != null) {
          Name originClass = origin.getContainingClass();
          if (originClass != null) {
            return isSubTypeOf(containingClass, originClass, typeInfoResolver);
          }
        }
        return false;
      }
      case 0: {
        Name originPackage = origin.getPackage();
        return originPackage != null
            && originPackage.equals(canonName.getPackage());
      }
      default:
        throw new IllegalStateException(
            "Multiple access control mods "
            + Modifier.toString(accessControlMods)
            + " defined on " + canonName);
    }
  }

  private static final Name JAVA_LANG_OBJECT = Name.DEFAULT_PACKAGE
      .child("java", Name.Type.PACKAGE)
      .child("lang", Name.Type.PACKAGE)
      .child("Object", Name.Type.CLASS);

  private static boolean isSubTypeOf(
      Name className, Name potentialSuperTypeName,
      TypeInfoResolver typeInfoResolver) {
    if (className.equals(potentialSuperTypeName)) {  // Non-strict.
      return true;
    }
    if (JAVA_LANG_OBJECT.equals(potentialSuperTypeName)) {
      // Implicit super-type of all interface types.
      return true;
    }
    Optional<TypeInfo> typeInfoOpt = typeInfoResolver.resolve(className);
    if (!typeInfoOpt.isPresent()) {
      return false;
    }
    Optional<TypeInfo> superTypeInfoOpt = typeInfoResolver.resolve(
        potentialSuperTypeName);
    if (!superTypeInfoOpt.isPresent()) {
      return false;
    }
    TypeInfo superTypeInfo = superTypeInfoOpt.get();
    if (Modifier.isInterface(superTypeInfo.modifiers)) {
      Set<Name> interfaceNames = Sets.newHashSet();
      Deque<TypeInfo> unprocessed = new ArrayDeque<>();
      interfaceNames.add(className);
      unprocessed.add(typeInfoOpt.get());
      do {
        TypeInfo ti = unprocessed.removeFirst();
        for (TypeSpecification ts : ti.interfaces) {
          if (ts.rawName.equals(potentialSuperTypeName)) {
            return true;
          }
          if (interfaceNames.add(ts.rawName)) {
            Optional<TypeInfo> interfaceInfoOpt = typeInfoResolver.resolve(
                ts.rawName);
            if (interfaceInfoOpt.isPresent()) {
              unprocessed.add(interfaceInfoOpt.get());
            }
          }
        }
      } while (!unprocessed.isEmpty());
    } else {
      // Just walk the super-type chain
      TypeInfo ti = typeInfoOpt.get();
      while (true) {
        if (!ti.superType.isPresent()) {
          break;
        }
        TypeSpecification ts = ti.superType.get();
        if (ts.rawName.equals(potentialSuperTypeName)) {
          return true;
        }
        Optional<TypeInfo> ancestorTypeInfoOpt = typeInfoResolver.resolve(
            ts.rawName);
        if (ancestorTypeInfoOpt.isPresent()) {
          ti = ancestorTypeInfoOpt.get();
        } else {
          break;
        }
      }
    }
    return false;
  }

}
