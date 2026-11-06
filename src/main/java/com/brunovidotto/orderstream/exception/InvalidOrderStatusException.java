package com.brunovidotto.orderstream.exception;

/**
 * Exception thrown when an invalid order status is encountered during processing.
 * <p>
 * This exception is used to handle cases where an order has an unrecognized status
 * that does not match the expected values.
 * It extends {@link IllegalArgumentException}, indicating that the provided argument
 * (order status) is invalid.
 * </p>
 *
 */
public class InvalidOrderStatusException extends IllegalArgumentException {

  public InvalidOrderStatusException(String message) {
    super(message);
  }
}
