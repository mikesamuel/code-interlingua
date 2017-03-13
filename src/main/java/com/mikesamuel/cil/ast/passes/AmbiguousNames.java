package com.mikesamuel.cil.ast.passes;

import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Nullable;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.mikesamuel.cil.ast.j8.IdentifierNode;
import com.mikesamuel.cil.ast.j8.J8BaseNode;
import com.mikesamuel.cil.ast.j8.J8NodeType;
import com.mikesamuel.cil.ast.j8.TypeArgumentNode;
import com.mikesamuel.cil.ast.j8.WildcardBoundsNode;
import com.mikesamuel.cil.ast.j8.WildcardNode;
import com.mikesamuel.cil.ast.meta.Name;
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

    // TODO: obviate the distinction between diamonds and arguments by rewriting
    // all diamond operators in-situ.
    Name rawName = ambiguousNameOf(nameNode);
    if (rawName == null) {
      error(logger, pos, "Missing type name");
      return StaticType.ERROR_TYPE.typeSpecification;
    }
    ImmutableList<Name> canonNames = canonResolver.lookupTypeName(rawName);
    switch (canonNames.size()) {
      case 0:
        error(logger, pos, "Unrecognized type " + rawName);
        return StaticType.ERROR_TYPE.typeSpecification;
      case 1:
        break;
      default:
        error(logger, pos, "Ambiguous type " + rawName + ": " + canonNames);
        break;
    }

    Name canonName = canonNames.get(0);

    ImmutableList.Builder<TypeBinding> bindings = ImmutableList.builder();
    for (TypeArgumentNode arg :
         nameNode.finder(TypeArgumentNode.class)
             .exclude(
                 J8NodeType.Annotation,
                 // Don't recurse in the finder.  Recurse via this method.
                 J8NodeType.TypeArgument)
             .find()) {
      bindings.add(typeBindingOf(arg, canonResolver, logger));
    }
    return new TypeSpecification(canonName, bindings.build());
  }

  static TypeBinding typeBindingOf(
      TypeArgumentNode arg, TypeNameResolver canonResolver,
      Logger logger) {
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
    Name name = null;
    for (IdentifierNode ident :
         nameNode.finder(IdentifierNode.class)
             .exclude(
                 J8NodeType.Annotation,
                 J8NodeType.TypeArgumentList,
                 J8NodeType.TypeParameters)
             .find()) {
      Name.Type type = ident.getNamePartType();
      if (type == null) {
        type = Name.Type.AMBIGUOUS;
      }
      if (type == Name.Type.PACKAGE && name == null) {
        name = Name.DEFAULT_PACKAGE;
      }
      name = (name == null)
          ? Name.root(ident.getValue(), type)
          : name.child(ident.getValue(), type);
    }
    return name;
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
