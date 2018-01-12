package com.mikesamuel.cil.ast.passes.synth;

import java.lang.reflect.Modifier;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

import javax.annotation.Nullable;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.mikesamuel.cil.ast.j8.J8BaseNode;
import com.mikesamuel.cil.ast.j8.J8FileNode;
import com.mikesamuel.cil.ast.meta.CallableInfo;
import com.mikesamuel.cil.ast.meta.MemberInfo;
import com.mikesamuel.cil.ast.meta.Name;
import com.mikesamuel.cil.ast.meta.TypeInfo;
import com.mikesamuel.cil.ast.passes.AbstractRewritingPass.Parent;
import com.mikesamuel.cil.parser.SList;

/** Builds the maps in common. */
final class AnalyzeMiniPass extends PerUseAbstractMiniPass {
  AnalyzeMiniPass(Common c) {
    super(c);
  }

  @Override
  void run(ImmutableList<J8FileNode> files) {
    super.run(files);
    findBridgesNeeded();
  }

  @Override
  void processUse(
      UserDefinedType udt,
      J8BaseNode node, @Nullable SList<Parent> pathFromRoot,
      Name used, EnumSet<UseType> ts) {
    EnumSet<UseType> s = c.uses.get(used);
    if (s == null) {
      c.uses.put(used, EnumSet.copyOf(ts));
    } else {
      s.addAll(ts);
    }
  }

  private void findBridgesNeeded() {
    for (UserDefinedType udt : c.byName.values()) {
      ImmutableList<CallableInfo> methods;
      {
        ImmutableList.Builder<CallableInfo> b = ImmutableList.builder();
        for (MemberInfo mi : udt.ti.getDeclaredMembers()) {
          if (!(mi instanceof CallableInfo)) { continue; }
          CallableInfo ci = (CallableInfo) mi;
          if (ci.isInitializer
              || Modifier.isAbstract(ci.modifiers)
              || ci.isConstructor()) {
            continue;
          }
          b.add(ci);
        }
        methods = b.build();
      }
      Set<Erasure> declared = new HashSet<>();
      for (CallableInfo ci : methods) {
        declared.add(Erasure.from(ci));
      }
      for (CallableInfo ci : methods) {
        for (Name overridden : c.memberInfoPool.overriddenBy(ci)) {
          Optional<TypeInfo> declaringTiOpt = c.typePool.r.resolve(
              overridden.getContainingClass());
          if (!declaringTiOpt.isPresent()) { continue; }
          TypeInfo declaringTi = declaringTiOpt.get();
          Optional<CallableInfo> ovCiOpt =
              declaringTi.declaredCallableNamed(overridden);
          if (!ovCiOpt.isPresent()) { continue; }
          Erasure er = Erasure.from(ovCiOpt.get());
          if (!declared.contains(er)) {
            c.bridges.put(ci.canonName, er);
          }
        }
      }
    }
  }
}
