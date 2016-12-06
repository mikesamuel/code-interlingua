package com.mikesamuel.cil.parser;

/**
 * Receives information about serialization failures.
 * <p>
 * It is normal for some errors to be raised as branches fail, so a good
 * rule-of-thumb is to delay reporting any until the parse as a whole is known
 * to fail and then report the error with the greatest index as most likely
 * indicative of the underlying cause.
 */
public interface SerialErrorReceiver {

  /** Called when an attempt to serialize a particular variant fails. */
  void error(SerialState state, String message);

  /** Silently ignores error reports. */
  public static final SerialErrorReceiver DEV_NULL = new SerialErrorReceiver() {

    @Override
    public void error(SerialState state, String message) {
      // Drop silently.
    }

  };

}
