package com.mikesamuel.cil.ast.passes;

import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Nullable;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.mikesamuel.cil.ast.j8.IdentifierNode;
import com.mikesamuel.cil.ast.j8.J8BaseNode;
import com.mikesamuel.cil.ast.j8.TypeArgumentListNode;
import com.mikesamuel.cil.ast.j8.TypeArgumentNode;
import com.mikesamuel.cil.ast.j8.WildcardBoundsNode;
import com.mikesamuel.cil.ast.j8.WildcardNode;
import com.mikesamuel.cil.ast.meta.Name;
import com.mikesamuel.cil.ast.meta.PartialTypeSpecification;
import com.mikesamuel.cil.ast.meta.StaticType;
import com.mikesamuel.cil.ast.meta.TypeInfo;
import com.mikesamuel.cil.ast.meta.TypeInfoResolver;
import com.mikesamuel.cil.ast.meta.TypeNameResolver;
import com.mikesamuel.cil.ast.meta.TypeSpecification;
import com.mikesamuel.cil.ast.meta.TypeSpecification.TypeBinding;
import com.mikesamuel.cil.parser.SList;
import com.mikesamuel.cil.parser.SourcePosition;
import com.mikesamuel.cil.util.LogUtils;

final class AmbiguousNames {
  private AmbiguousNames() {
    // Static API
  }

  static void error(
      Logger logger, @Nullable SourcePosition pos, String msg) {
    LogUtils.log(logger, Level.SEVERE, pos, msg, null);
  }

  static TypeSpecification typeSpecificationOf(
      J8BaseNode nameNode, TypeNameResolver canonResolver, Logger logger) {
    SourcePosition pos = nameNode.getSourcePosition();

    Map<Name, TypeArgumentListNode> argumentsByName = Maps.newLinkedHashMap();
    Name rawName = ambiguousNameOf(null, nameNode, argumentsByName);
    if (rawName == null) {
      error(logger, pos, "Missing type name");
      return StaticType.ERROR_TYPE.typeSpecification;
    }

    Optional<Name> canonNameOpt = oneCanon(canonResolver, rawName, logger, pos);
    if (!canonNameOpt.isPresent()) {
      return StaticType.ERROR_TYPE.typeSpecification;
    }
    Name canonName = canonNameOpt.get();

    if (!canonName.equals(rawName)) {
      // canonicalize keys in the map so we can match it up with segments of
      // the canonical name.
      Map<Name, TypeArgumentListNode> canonArgumentsByName =
          Maps.newLinkedHashMap();
      for (Map.Entry<Name, TypeArgumentListNode> e
           : argumentsByName.entrySet()) {
        Optional<Name> canonKeyOpt = oneCanon(
            canonResolver, e.getKey(), logger, pos);
        if (canonKeyOpt.isPresent()) {
          Name canonKey = canonKeyOpt.get();
          TypeArgumentListNode dupe = canonArgumentsByName.put(
              canonKey, e.getValue());
          if (dupe != null) {
            error(logger, e.getValue().getSourcePosition(),
                "Two lists of type arguments modify the same canonical type "
                + canonKey + ".  Original at " + dupe.getSourcePosition());
          }
        }
      }
      argumentsByName = canonArgumentsByName;
    }

    if (!canonName.type.isType) {
      error(logger, pos, "Expected type name not " + canonName);
    }

    TypeSpecification spec = (TypeSpecification) buildPartialType(
        canonName, argumentsByName, canonResolver, logger);
    if (!argumentsByName.isEmpty()) {
      // Warn on any type arguments that were obviated during type name
      // canonicalization.  The building removes entries so any left over are
      // unused.
      for (Map.Entry<Name, TypeArgumentListNode> e
          : argumentsByName.entrySet()) {
        error(logger, e.getValue().getSourcePosition(),
              "Unused type arguments");
      }
    }
    return spec;
  }

  private static PartialTypeSpecification buildPartialType(
      Name canonName, Map<Name, TypeArgumentListNode> argumentsByName,
      TypeNameResolver canonResolver, Logger logger) {
    return PartialTypeSpecification.fromName(
        canonName,
        new Function<Name, ImmutableList<TypeBinding>>() {

          @Override
          public ImmutableList<TypeBinding> apply(Name name) {
            // This remove makes this function not really a function, but
            // fromName advertises call-once per name, and our obviated name
            // detection above requires a way of tracking used keys.
            TypeArgumentListNode arguments = argumentsByName.remove(name);
            if (arguments == null) { return ImmutableList.of(); }
            return bindingsOf(arguments, canonResolver, logger);
          }

        }
        );
  }

  static ImmutableList<TypeBinding> bindingsOf(
      TypeArgumentListNode arguments, TypeNameResolver canonResolver,
      Logger logger) {
    ImmutableList.Builder<TypeBinding> b = ImmutableList.builder();
    for (int i = 0, n = arguments.getNChildren(); i < n; ++i) {
      J8BaseNode child = arguments.getChild(i);
      if (child instanceof TypeArgumentNode) {
        b.add(typeBindingOf(
            (TypeArgumentNode) child, canonResolver, logger));
      } else {
        error(logger, child.getSourcePosition(),
            "Expected a type argument, not " + child.getVariant());
      }
    }
    return b.build();
  }

  private static Optional<Name> oneCanon(
      TypeNameResolver canonResolver, Name rawName, Logger logger,
      SourcePosition pos) {
    ImmutableList<Name> canonNames = canonResolver.lookupTypeName(rawName);
    switch (canonNames.size()) {
      case 0:
        error(logger, pos, "Unrecognized type " + rawName);
        return Optional.absent();
      case 1:
        break;
      default:
        error(logger, pos, "Ambiguous type " + rawName + ": " + canonNames);
        break;
    }
    return Optional.of(canonNames.get(0));
  }

  static TypeBinding typeBindingOf(
      TypeArgumentNode arg, TypeNameResolver canonResolver, Logger logger) {
    TypeSpecification argSpec = null;
    TypeSpecification.Variance variance =
        TypeSpecification.Variance.INVARIANT;
    switch (arg.getVariant()) {
      case ReferenceType:
        argSpec = typeSpecificationOf(
            arg.getChildren().get(0), canonResolver, logger);
        break;
      case Wildcard:
        WildcardNode wc = arg.firstChildWithType(WildcardNode.class);
        if (wc != null) {
          WildcardBoundsNode wcb = wc.firstChildWithType(
              WildcardBoundsNode.class);
          if (wcb != null) {
            switch (wcb.getVariant()) {
              case ExtendsReferenceType:
                variance = TypeSpecification.Variance.EXTENDS;
                break;
              case SuperReferenceType:
                variance = TypeSpecification.Variance.SUPER;
                break;
            }
            argSpec = typeSpecificationOf(wcb, canonResolver, logger);
            break;
          } else {
            // "?" without bounds.  Assume bounds based on parameter
            // super-type.
            variance = TypeSpecification.Variance.EXTENDS;
            // use null for argSpec
            break;
          }
        }
        error(
            logger, arg.getSourcePosition(),
            "Missing bounds for type argument");
        break;
    }
    return new TypeBinding(variance, argSpec);
  }

  static @Nullable Name ambiguousNameOf(J8BaseNode nameNode) {
    return ambiguousNameOf(null, nameNode, null);
  }

  static @Nullable Name ambiguousNameOf(
      @Nullable Name prev, J8BaseNode nameNode,
      @Nullable Map<Name, TypeArgumentListNode> argumentsByName) {
    switch (nameNode.getNodeType()) {
      case Identifier:
        IdentifierNode ident = (IdentifierNode) nameNode;
        Name.Type type = ident.getNamePartType();
        if (type == null) {
          type = Name.Type.AMBIGUOUS;
        }
        Name parent = prev;
        if (type == Name.Type.PACKAGE && parent == null) {
          parent = Name.DEFAULT_PACKAGE;
        }
        return (parent == null)
            ? Name.root(ident.getValue(), type)
            : parent.child(ident.getValue(), type);
      case TypeArgumentList:
        if (argumentsByName != null) {
          argumentsByName.put(prev, (TypeArgumentListNode) nameNode);
        }
        return prev;
      case Annotation:
      case TypeParameters:
        return prev;
      default:
        Name nm = prev;
        for (int i = 0, n = nameNode.getNChildren(); i < n; ++i) {
          nm = ambiguousNameOf(nm, nameNode.getChild(i), argumentsByName);
        }
        return nm;
    }
  }

  static Optional<MaximalMatch> longestTypeMatch(
      @Nullable SourcePosition pos,
      Logger logger,
      TypeInfoResolver qualifiedNameResolver,
      TypeNameResolver canonTypeNameResolver,
      Name ambiguousName) {
    SList<Name> unrolled = null;
    for (Name nm = ambiguousName;
         nm != null && nm.type != Name.Type.PACKAGE;
         unrolled = SList.append(unrolled, nm), nm = nm.parent) {
      if (nm.type == Name.Type.CLASS
          || nm.type == Name.Type.TYPE_PARAMETER
          || nm.type == Name.Type.AMBIGUOUS) {
        ImmutableList<Name> qualNames =
            canonTypeNameResolver.lookupTypeName(nm);
        switch (qualNames.size()) {
          case 0: continue;
          default:
            if (logger != null) {
              StringBuilder msg = new StringBuilder();
              msg.append("Ambiguous type name ");
              nm.appendDottedString(msg);
              msg.append(": ").append(qualNames);
              error(logger, pos, msg.toString());
            }
            //$FALL-THROUGH$
          case 1:
            Name canonName = qualNames.get(0);
            Optional<TypeInfo> ti = qualifiedNameResolver.resolve(canonName);
            if (ti.isPresent()) {
              Name nameWithMembers = canonName;
              for (; unrolled != null; unrolled = unrolled.prev) {
                nameWithMembers = unrolled.x.reparent(nameWithMembers);
              }
              return Optional.of(new MaximalMatch(ti.get(), nameWithMembers));
            }

            if (logger != null) {
              StringBuilder msg = new StringBuilder();
              if (pos != null) {
                msg.append(pos).append(": ");
              }
              msg.append("Missing type information for ");
              canonName.appendDottedString(msg);
              logger.severe(msg.toString());
            }
            return Optional.absent();
        }
      }
    }
    return Optional.absent();
  }


  /**
   * The ambiguous name (System ? err ? println) might map to
   * the typeInfo for /java/lang/System and member names [err, println].
   */
  static final class MaximalMatch {
    final TypeInfo typeInfo;
    final Name nameWithMembers;

    MaximalMatch(TypeInfo typeInfo, Name nameWithMembers) {
      this.typeInfo = typeInfo;
      this.nameWithMembers = nameWithMembers;
    }
  }
}
