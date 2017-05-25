package com.mikesamuel.cil.ast.meta;

import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

/**
 * Resolves expression name parts to locals, or fields.
 */
public interface ExpressionNameResolver {
  /**
   * The resolution for the given identifier to a readable/writable field or
   * local.
   */
  public Optional<Name> resolveReference(
      String ident, DeclarationPositionMarker m);

  /**
   * An expression name resolver that behaves the same way as this one, but
   * with any internal state transformed according to the given bridge.
   */
  public ExpressionNameResolver map(MetadataBridge b);

  /**
   * Static factories for resolvers.
   */
  public final class Resolvers {

    /**
     *
     * @param explicit Names import via {@code import static foo.Bar.baz;}
     * @param wildcards Names import via {@code import static foo.Bar.*;}
     * @param canonNameResolver used to resolve super types of wildcard imports.
     * @param fromPackage the package for the compilation unit into which these
     *    names are imported which is used to evaluate whether package private
     *    fields are visible.
     * @param logger receives warnings about ambiguities in explicit imports.
     */
    public static ExpressionNameResolver forImports(
        Iterable<? extends Name> explicit,
        Iterable<? extends TypeInfo> wildcards,
        TypeInfoResolver canonNameResolver,
        Name fromPackage,
        Logger logger) {
      Map<String, Name> identToExplicitName = Maps.newLinkedHashMap();
      for (Name name : explicit) {
        Name dupe = identToExplicitName.get(name.identifier);
        if (dupe != null && !name.equals(dupe)) {
          logger.severe("static import of " + name + " conflicts with " + dupe);
        } else {
          identToExplicitName.put(name.identifier, name);
        }
      }
      for (TypeInfo wildcard : wildcards) {
        addFieldsFrom(
            wildcard, identToExplicitName, canonNameResolver,
            true, 0,
            fromPackage);
      }

      return new FromMapResolver(identToExplicitName);
    }

    /**
     * Resolves to fields of the given type.
     */
    public static ExpressionNameResolver forType(
        TypeInfo typeInfo, TypeInfoResolver canonNameResolver) {
      Map<String, Name> fieldNameToCanonName = Maps.newLinkedHashMap();
      addFieldsFrom(
          typeInfo, fieldNameToCanonName, canonNameResolver, false,
          Modifier.PRIVATE | Modifier.PROTECTED,
          typeInfo.getPackage());
      return new FromMapResolver(fieldNameToCanonName);
    }


    private static void addFieldsFrom(
        TypeInfo ti,
        Map<String, Name> fieldNameToCanonName,
        TypeInfoResolver canonNameResolver,
        boolean staticOnly, int permBits,
        Name fromPackage) {
      Preconditions.checkArgument(fromPackage.type == Name.Type.PACKAGE);
      Name typePackage = ti.getPackage();
      for (MemberInfo mi : ti.getDeclaredMembers()) {
        if (mi instanceof FieldInfo) {
          int mods = mi.modifiers;
          if (staticOnly && !Modifier.isStatic(mods)) { continue; }
          switch (
              mods
              & (Modifier.PUBLIC | Modifier.PROTECTED | Modifier.PRIVATE)) {
            case Modifier.PUBLIC:
              break;
            case Modifier.PRIVATE:
              if ((permBits & Modifier.PRIVATE) == 0) {
                continue;
              }
              break;
            case Modifier.PROTECTED:
              if ((permBits & Modifier.PROTECTED) != 0) {
                break;
              }
              //$FALL-THROUGH$
            case 0:
              if (!typePackage.equals(fromPackage)) {
                continue;
              }
              break;
          }
          String ident = mi.canonName.identifier;
          if (!fieldNameToCanonName.containsKey(ident)) {
            fieldNameToCanonName.put(ident, mi.canonName);
          }
        }
      }
      if (ti.superType.isPresent()) {
        Optional<TypeInfo> superTypeInfoOpt = canonNameResolver.resolve(
            ti.superType.get().rawName);
        if (superTypeInfoOpt.isPresent()) {
          addFieldsFrom(
              superTypeInfoOpt.get(), fieldNameToCanonName, canonNameResolver,
              staticOnly, permBits & ~Modifier.PRIVATE, fromPackage);
        }
      }
      for (TypeSpecification interfaceSpec : ti.interfaces) {
        Optional<TypeInfo> interfaceTypeInfoOpt = canonNameResolver.resolve(
            interfaceSpec.rawName);
        if (interfaceTypeInfoOpt.isPresent()) {
          addFieldsFrom(
              interfaceTypeInfoOpt.get(),
              fieldNameToCanonName, canonNameResolver,
              staticOnly, permBits & ~Modifier.PRIVATE, fromPackage);
        }
      }
    }
    private static class FromMapResolver implements ExpressionNameResolver {
      private final ImmutableMap<String, Name> identToCanonName;

      FromMapResolver(Map<String, Name> identToCanonName) {
        this.identToCanonName = ImmutableMap.copyOf(identToCanonName);
      }

      @Override
      public ExpressionNameResolver map(MetadataBridge b) {
        if (b == MetadataBridge.Bridges.IDENTITY) { return this; }
        ImmutableMap.Builder<String, Name> identToCanonNameBridged =
            ImmutableMap.builder();
        for (Map.Entry<String, Name> e : identToCanonName.entrySet()) {
          identToCanonNameBridged.put(
              e.getKey(),
              b.bridgeReferencedExpressionName(e.getValue()));
        }
        return new FromMapResolver(identToCanonNameBridged.build());
      }

      @Override
      public Optional<Name> resolveReference(
          String ident, DeclarationPositionMarker m) {
        Name canon = identToCanonName.get(ident);
        if (canon != null) {
          return Optional.of(canon);
        }
        return Optional.absent();
      }

      @Override
      public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("(Names");
        abbreviateNames(ImmutableList.copyOf(identToCanonName.values()), sb);
        return sb.append(')').toString();
      }
    }

    static void abbreviateNames(List<Name> names, StringBuilder sb) {
      Name lastParent = null;
      for (int i = 0, n = names.size(); i < n; ++i) {
        sb.append(i != 0 ? ',' : ' ');
        Name nm = names.get(i);
        if (lastParent != null && lastParent.equals(nm.parent)) {
          sb.append(nm.identifier);
        } else {
          if (lastParent != null) {
            sb.append('}');
            lastParent = null;
          }
          if (i + 1 < n && nm.parent != null
              && nm.parent.equals(names.get(i + 1).parent)) {
            lastParent = nm.parent;
            sb.append(nm.parent);
            sb.append('{');
            sb.append(nm.identifier);
          } else {
            sb.append(nm);
          }
        }
      }
      if (lastParent != null) {
        sb.append('}');
      }
    }
  }

  /**
   * A name resolver for local variables and formal parameters visible within
   * blocks of statements.
   */
  public static final class BlockExpressionNameResolver
  implements ExpressionNameResolver {
    private final List<Name> declarations = Lists.newArrayList();

    /**
     * @return the position for all declarations afterwards.
     */
    public DeclarationPositionMarker declare(Name name) {
      BlockMarker bm = new BlockMarker(declarations.size());
      declarations.add(name);
      return bm;
    }

    @Override
    public ExpressionNameResolver map(MetadataBridge b) {
      if (b == MetadataBridge.Bridges.IDENTITY) { return this; }
      BlockExpressionNameResolver bridged = new BlockExpressionNameResolver();
      for (Name d : declarations) {
        bridged.declarations.add(b.bridgeReferencedExpressionName(d));
      }
      return bridged;
    }


    final class BlockMarker implements DeclarationPositionMarker {
      final int index;

      BlockMarker(int index) {
        this.index = index;
      }

      BlockExpressionNameResolver getResolver() {
        return BlockExpressionNameResolver.this;
      }

      @Override
      public int compareTo(DeclarationPositionMarker o) {
        if (o == LATEST) {
          return -1;
        }
        if (o == EARLIEST) {
          return 1;
        }
        BlockMarker that = (BlockMarker) o;
        Preconditions.checkState(
            this.getResolver() == that.getResolver());
        return this.index - that.index;
      }

      @Override
      public boolean equals(Object o) {
        if (!(o instanceof BlockMarker)) { return false; }
        BlockMarker that = (BlockMarker) o;
        return this.getResolver() == that.getResolver()
            && this.index == that.index;
      }

      @Override
      public int hashCode() {
        return getResolver().hashCode() + 31 * index;
      }

      @Override
      public String toString() {
        return "#" + index;
      }
    }

    @Override
    public Optional<Name> resolveReference(
        String ident, DeclarationPositionMarker m) {
      int stopIndex;
      if (m == DeclarationPositionMarker.EARLIEST) {
        return Optional.absent();
      } else if (m == DeclarationPositionMarker.LATEST) {
        stopIndex = declarations.size();
      } else {
        BlockMarker bm = (BlockMarker) m;
        Preconditions.checkArgument(
            bm.index <= declarations.size() && bm.getResolver() == this);
        // bm.index is inclusive, stopIndex is exclusive
        stopIndex = bm.index + 1;
      }
      for (int i = stopIndex; --i >= 0;) {
        Name name = declarations.get(i);
        if (ident.equals(name.identifier)) { return Optional.of(name); }
      }
      return Optional.absent();
    }

    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder();
      sb.append("(Block");
      Resolvers.abbreviateNames(declarations, sb);
      return sb.append(')').toString();
    }
  }


  /**
   * A marker used to specify where in a scope the reference occurs so that
   * later declarations can be ignored.
   */
  public interface DeclarationPositionMarker
  extends Comparable<DeclarationPositionMarker>{

    /**
     * A position marker that is after all declarations, so all are in scope.
     */
    public static final DeclarationPositionMarker EARLIEST
    = new DeclarationPositionMarker() {

      @Override
      public int compareTo(DeclarationPositionMarker o) {
        if (o == this) { return 0; }
        return -Integer.signum(o.compareTo(this));
      }

      @Override
      public boolean equals(Object o) {
        return this == o;
      }

      @Override
      public int hashCode() {
        return -1;
      }

      @Override
      public String toString() {
        return "EARLIEST";
      }
    };

    /**
     * A position marker that is after all declarations, so all are in scope.
     */
    public static final DeclarationPositionMarker LATEST
    = new DeclarationPositionMarker() {

      @Override
      public int compareTo(DeclarationPositionMarker o) {
        if (o == this) { return 0; }
        if (o == EARLIEST) { return 1; }
        return -Integer.signum(o.compareTo(this));
      }

      @Override
      public boolean equals(Object o) {
        return this == o;
      }

      @Override
      public int hashCode() {
        return -1;
      }

      @Override
      public String toString() {
        return "LATEST";
      }
    };
  }

}
