package com.mikesamuel.cil.ast.passes;

import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Set;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.mikesamuel.cil.ast.j8.IdentifierNode;
import com.mikesamuel.cil.ast.j8.J8BaseNode;
import com.mikesamuel.cil.ast.j8.J8TypeDeclaration;
import com.mikesamuel.cil.ast.meta.MemberInfo;
import com.mikesamuel.cil.ast.meta.Name;
import com.mikesamuel.cil.ast.meta.TypeInfo;
import com.mikesamuel.cil.ast.meta.TypeInfoResolver;
import com.mikesamuel.cil.ast.meta.TypeSpecification;

/**
 * Allocates identifiers while avoiding masking or shadowing.
 */
public final class NameAllocator {
  private final Set<String> exclusions = new HashSet<>();

  private NameAllocator() {}

  /**
   * @param root the root to search for identifiers to exclude
   * @param limit if it returns true, then control will not descend into
   *     the argument.
   * @param resolver used to resolve super-types so that we can exclude
   *     names of inherited members.
   */
  public static NameAllocator create(
      J8BaseNode root, Predicate<? super J8BaseNode> limit,
      TypeInfoResolver resolver) {
    NameAllocator nameAllocator = new NameAllocator();
    nameAllocator.addExclusions(root, limit, resolver);
    return nameAllocator;
  }

  /** Adds extra exclusions. */
  public void exclude(Iterable<? extends String> extraExclusions) {
    for (String s : extraExclusions) {
      exclusions.add(s);
    }
  }

  /** An identifier that does not shadow or mask any existing identifier. */
  public String allocateIdentifier(String candidate) {
    if (exclusions.add(candidate)) {
      return candidate;
    }
    StringBuilder sb = new StringBuilder(candidate.length() + 16);
    sb.append(candidate).append("__");
    int resetLength = sb.length();
    int counter = 0;
    for (;;) {
      ++counter;
      Preconditions.checkState(counter != Integer.MIN_VALUE);
      sb.setLength(resetLength);
      sb.append(counter);
      String nextCandidate = sb.toString();
      if (exclusions.add(nextCandidate)) {
        return nextCandidate;
      }
    }
  }

  private void addExclusions(
      J8BaseNode node, Predicate<? super J8BaseNode> limit,
      TypeInfoResolver resolver) {
    if (node instanceof IdentifierNode) {
      exclusions.add(((IdentifierNode) node).getValue());
    } else if (!limit.apply(node)) {
      if (node instanceof J8TypeDeclaration) {
        J8TypeDeclaration d = (J8TypeDeclaration) node;
        TypeInfo ti = d.getDeclaredTypeInfo();
        if (ti != null) {
          addExclusions(ti, resolver);
        }
      }
      for(J8BaseNode child : node.getChildren()) {
        addExclusions(child, limit, resolver);
      }
    }
  }

  private void addExclusions(TypeInfo ti, TypeInfoResolver resolver) {
    for (Name pkg = ti.canonName; pkg != null; pkg = pkg.parent) {
      if (Name.DEFAULT_PACKAGE.equals(pkg.parent)
          && pkg.type == Name.Type.PACKAGE) {
        // Exclude root package names.
        exclusions.add(pkg.identifier);
      }
    }
    for (Name tp : ti.parameters) {
      exclusions.add(tp.identifier);
    }
    for (MemberInfo mi : ti.getDeclaredMembers()) {
      if (!Modifier.isPrivate(mi.modifiers)) {
        exclusions.add(mi.canonName.identifier);
      }
    }
    for (TypeSpecification st
         : Iterables.concat(ti.superType.asSet(), ti.interfaces)) {
      exclusions.add(st.rawName.identifier);
      Optional<TypeInfo> stiOpt = resolver.resolve(st.rawName);
      if (stiOpt.isPresent()) {
        TypeInfo sti = stiOpt.get();
        addExclusions(sti, resolver);
      }
    }
  }
}
