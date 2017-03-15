package com.mikesamuel.cil.ast.jmin;

import com.google.common.base.Optional;
import com.mikesamuel.cil.ast.BaseNode;
import com.mikesamuel.cil.ast.Grammar;
import com.mikesamuel.cil.ast.NodeI;
import com.mikesamuel.cil.ast.NodeType;
import com.mikesamuel.cil.ast.NodeVariant;
import com.mikesamuel.cil.parser.ParSer;

final class GrammarImpl implements Grammar<JminBaseNode, JminNodeType> {

  private GrammarImpl() {
    // singleton
  }

  static final GrammarImpl INSTANCE = new GrammarImpl();

  @Override
  public JminBaseNode makePseudoRoot(
      Iterable<? extends JminBaseNode> subRoots) {
    throw new UnsupportedOperationException();
  }

  @Override
  public JminBaseNode cast(NodeI<?, ?, ?> node) throws ClassCastException {
    return (JminBaseNode) node;
  }

  @Override
  public JminNodeVariant cast(NodeVariant<?, ?> v) throws ClassCastException {
    return (JminNodeVariant) v;
  }

  @Override
  public JminNodeType cast(NodeType<?, ?> t) throws ClassCastException {
    return (JminNodeType) t;
  }

  @Override
  public Object tryToCoerce(Object value, JminNodeType nodeType) {
    throw new UnsupportedOperationException();  // TODO
  }

  @Override
  public Optional<ParSer> parserForNonStandardReplacement(
      NodeType<?, ?> nodeType) {
    return Optional.absent();
  }

  @Override
  public Optional<ParSer> parserForNonStandardInterstitial(
      NodeType<?, ?> nodeType) {
    return Optional.absent();
  }

  @Override
  public boolean isNonStandardReplacement(NodeType<?, ?> nodeType) {
    return false;
  }

  @Override
  public boolean isNonStandardInterstitial(NodeType<?, ?> nodeType) {
    return false;
  }

  @Override
  public Optional<JminBaseNode> wrap(
      BaseNode<?, ?, ?> inner, JminNodeType outerType) {
    return Optional.absent();  // TODO
  }

}
