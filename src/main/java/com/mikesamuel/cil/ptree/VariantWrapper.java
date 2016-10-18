package com.mikesamuel.cil.ptree;

import com.google.common.base.Optional;
import com.mikesamuel.cil.ast.MatchEvent;
import com.mikesamuel.cil.ast.NodeVariant;
import com.mikesamuel.cil.ast.MatchEvent.PushMatchEvent;
import com.mikesamuel.cil.parser.Chain;
import com.mikesamuel.cil.parser.MatchErrorReceiver;
import com.mikesamuel.cil.parser.MatchState;
import com.mikesamuel.cil.parser.ParSerable;
import com.mikesamuel.cil.parser.ParseErrorReceiver;
import com.mikesamuel.cil.parser.ParseState;
import com.mikesamuel.cil.parser.SerialErrorReceiver;
import com.mikesamuel.cil.parser.SerialState;

final class VariantWrapper extends PTParSer {
  final NodeVariant v;
  final ParSerable child;
  final boolean isLeftRecursive;

  VariantWrapper(NodeVariant v, ParSerable child, boolean isLeftRecursive) {
    this.v = v;
    this.child = child;
    this.isLeftRecursive = isLeftRecursive;
  }

  @Override
  public Optional<ParseState> parse(
      ParseState state, ParseErrorReceiver err) {
    if (isLeftRecursive) {
      Optional<ParseState> withLrStart = Optional.absent();
      for (Chain<? extends MatchEvent> o = state.output;
          o != null; o = o.prev) {
        if (o.x.wasTextConsumed()) {
          break;
        } else if (o.x instanceof MatchEvent.PushMatchEvent) {
          MatchEvent.PushMatchEvent push = (PushMatchEvent) o.x;
          if (push.variant.getNodeType() == v.getNodeType()) {
            withLrStart = Optional.of(
                state.withOutput(Chain.append(
                    Chain.<MatchEvent>copyOf(o.prev),
                    MatchEvent.leftRecursionStart(push.variant))));
            break;
          }
        }
      }
      if (withLrStart.isPresent()) {
        // Grow the seed instead of calling into it.
        GrowTheSeed gts = GrowTheSeed.of(this.v.getNodeType());
        ParSerable seed = gts.seed;
        ParSerable suffix = gts.suffix;

        Optional<ParseState> afterSeedOpt =
            seed.getParSer().parse(withLrStart.get(), err);
        if (!afterSeedOpt.isPresent()) {
          return Optional.absent();
        }
        ParseState afterSeed = afterSeedOpt.get();
        Optional<ParseState> afterSuffixOpt =
            suffix.getParSer() .parse(afterSeed, err);
        if (!afterSuffixOpt.isPresent()) {
          return Optional.absent();
        }
        ParseState afterSuffix = afterSuffixOpt.get();
        return Optional.of(
            afterSuffix.appendOutput(MatchEvent.leftRecursionEnd()));
      }
    }

    ParseState pushed = state.appendOutput(MatchEvent.push(v));
    Optional<ParseState> result = child.getParSer().parse(pushed, err);
    if (result.isPresent()) {
      return Optional.of(result.get().appendOutput(MatchEvent.pop()));
    } else {
      return Optional.absent();
    }
  }

  @Override
  public Optional<SerialState> unparse(
      SerialState state, SerialErrorReceiver err) {
    MatchEvent wantedBefore = MatchEvent.push(v);
    Optional<SerialState> afterPush = state.expectEvent(wantedBefore, err);
    if (!afterPush.isPresent()) {
      return Optional.absent();
    }

    Optional<SerialState> stateAfterOpt = child.getParSer().unparse(
        afterPush.get(), err);

    return stateAfterOpt.isPresent()
        ? stateAfterOpt.get().expectEvent(MatchEvent.pop(), err)
        : Optional.absent();
  }

  @Override
  public Optional<MatchState> match(
      MatchState state, MatchErrorReceiver err) {
    MatchEvent wantedBefore = MatchEvent.push(v);
    Optional<MatchState> afterPush = state.expectEvent(wantedBefore, err);
    if (!afterPush.isPresent()) {
      return Optional.absent();
    }

    Optional<MatchState> stateAfterOpt = child.getParSer().match(
        afterPush.get(), err);

    return stateAfterOpt.isPresent()
        ? stateAfterOpt.get().expectEvent(MatchEvent.pop(), err)
        : Optional.absent();
  }

  @Override
  Kind getKind() {
    return Kind.VNT;
  }

  @Override
  public String toString() {
    return "(/*" + v.getNodeType() + "." + v + "*/ " + child + ")";
  }
}