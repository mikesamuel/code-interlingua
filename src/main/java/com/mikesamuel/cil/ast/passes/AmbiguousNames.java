package com.mikesamuel.cil.ast.passes;

import java.util.logging.Logger;

import javax.annotation.Nullable;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.mikesamuel.cil.ast.BaseNode;
import com.mikesamuel.cil.ast.IdentifierNode;
import com.mikesamuel.cil.ast.NodeType;
import com.mikesamuel.cil.ast.TypeArgumentNode;
import com.mikesamuel.cil.ast.WildcardBoundsNode;
import com.mikesamuel.cil.ast.WildcardNode;
import com.mikesamuel.cil.ast.meta.Name;
import com.mikesamuel.cil.ast.meta.StaticType;
import com.mikesamuel.cil.ast.meta.TypeInfo;
import com.mikesamuel.cil.ast.meta.TypeInfoResolver;
import com.mikesamuel.cil.ast.meta.TypeNameResolver;
import com.mikesamuel.cil.ast.meta.TypeSpecification;
import com.mikesamuel.cil.ast.meta.TypeSpecification.TypeBinding;
import com.mikesamuel.cil.parser.SList;
import com.mikesamuel.cil.parser.SourcePosition;

final class AmbiguousNames {
  private AmbiguousNames() {
    // Static API
  }

  private static void error(
      Logger logger, @Nullable SourcePosition pos, String msg) {
    String fullMessage = pos != null ? pos + ": " + msg : msg;
    logger.severe(fullMessage);
  }

  static TypeSpecification typeSpecificationOf(
      BaseNode nameNode, TypeNameResolver canonResolver, Logger logger) {
    SourcePosition pos = nameNode.getSourcePosition();

    // TODO: obviate the distinction between diamonds and arguments by rewriting
    // all diamond operators in-situ.
    Name rawName = ambiguousNameOf(nameNode);
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
                 NodeType.Annotation,
                 // Don't recurse in the finder.  Recurse via this method.
                 NodeType.TypeArgument)
             .find()) {
      TypeSpecification argSpec = typeSpecificationOf(
          arg.getChildren().get(0), canonResolver, logger);
      TypeSpecification.Variance variance =
          TypeSpecification.Variance.INVARIANT;
      switch (arg.getVariant()) {
        case ReferenceType:
          break;
        case Wildcard:
          WildcardNode wc = arg.firstChildWithType(WildcardNode.class);
          if (wc != null) {
            WildcardBoundsNode wcb = arg.firstChildWithType(
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
              break;
            }
          }
          error(
              logger, arg.getSourcePosition(),
              "Missing bounds for type argument");
          break;
      }
      bindings.add(new TypeBinding(variance, argSpec));
    }
    return new TypeSpecification(canonName, bindings.build());
  }

  static @Nullable Name ambiguousNameOf(BaseNode nameNode) {
    Name name = null;
    for (IdentifierNode ident :
         nameNode.finder(IdentifierNode.class)
             .exclude(
                 NodeType.Annotation,
                 NodeType.TypeArgumentList,
                 NodeType.TypeParameters)
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
              if (pos != null) {
                msg.append(pos).append(": ");
              }
              msg.append("Ambiguous type name ");
              nm.appendDottedString(msg);
              msg.append(": ").append(qualNames);
              logger.severe(msg.toString());
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
