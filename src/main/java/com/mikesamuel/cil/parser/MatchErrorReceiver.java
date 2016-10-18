package com.mikesamuel.cil.parser;

/**
 * Receives information about match failures.
 * <p>
 * It is normal for some errors to be raised as branches fail, so a good
 * rule-of-thumb is to delay reporting any until the parse as a whole is known
 * to fail and then report the error with the greatest index as most likely
 * indicative of the underlying cause.
 */
public interface MatchErrorReceiver {
  /** Called when an attempt to match a particular variant fails. */
  void error(MatchState state, String message);
}
