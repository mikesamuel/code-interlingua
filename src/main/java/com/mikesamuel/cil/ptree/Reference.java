package com.mikesamuel.cil.ptree;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.mikesamuel.cil.ast.NodeType;
import com.mikesamuel.cil.ast.NodeVariant;
import com.mikesamuel.cil.parser.MatchErrorReceiver;
import com.mikesamuel.cil.parser.MatchState;
import com.mikesamuel.cil.parser.ParSer;
import com.mikesamuel.cil.parser.ParSerable;
import com.mikesamuel.cil.parser.ParseErrorReceiver;
import com.mikesamuel.cil.parser.ParseState;
import com.mikesamuel.cil.parser.SerialErrorReceiver;
import com.mikesamuel.cil.parser.SerialState;

final class Reference extends PTParSer {
  final String name;
  final NodeType nodeType;
  final ParSer body;

  Reference(
      String name, Class<? extends Enum<? extends ParSerable>> variantClass) {
    this.name = name;
    ImmutableList.Builder<ParSer> variants = ImmutableList.builder();
    NodeType nt = null;
    for (Enum<?> e : variantClass.getEnumConstants()) {
      NodeVariant nv = (NodeVariant) e;
      variants.add(nv.getParSer());
      if (nt == null) {
        nt = nv.getNodeType();
      } else {
        Preconditions.checkState(nt == nv.getNodeType());
      }
    }
    this.nodeType = Preconditions.checkNotNull(nt);
    this.body = Alternation.of(variants.build()).getParSer();
  }

  @Override
  public String toString() {
    return name;
  }

  @Override
  Kind getKind() {
    return Kind.REF;
  }

  @Override
  public Optional<ParseState> parse(ParseState state, ParseErrorReceiver err) {
    return body.parse(state, err);
  }

  @Override
  public Optional<SerialState> unparse(
      SerialState state, SerialErrorReceiver err) {
    return body.unparse(state, err);
  }

  @Override
  public Optional<MatchState> match(
      MatchState state, MatchErrorReceiver err) {
    return body.match(state, err);
  }
}
