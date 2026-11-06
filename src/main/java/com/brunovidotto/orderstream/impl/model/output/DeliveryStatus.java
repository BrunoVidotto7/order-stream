package com.brunovidotto.orderstream.impl.model.output;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Enum representing the possible statuses of a delivery in the output model.
 * <p>
 * This enum is used to store the final delivery status, which can be either:
 * <ul>
 *   <li>{@link #DELIVERED} - At least one order in the delivery was successfully delivered.</li>
 *   <li>{@link #CANCELED} - All orders in the delivery were canceled.</li>
 * </ul>
 * <p>
 * This enum integrates with Jackson for JSON serialization.
 */
public enum DeliveryStatus {
  /**
   * Delivery status indicating that at least one order in the delivery was successfully delivered.
   */
  DELIVERED("delivered"),

  /**
   * Delivery status indicating that all orders in the delivery were canceled.
   */
  CANCELED("cancelled");

  private final String status;

  /**
   * Private constructor to assign a string representation to the enum value.
   *
   * @param status The string representation of the delivery status.
   */
  DeliveryStatus(String status) {
    this.status = status;
  }

  /**
   * Returns the string representation of the delivery status.
   * <p>
   * This method is annotated with {@link JsonValue}, meaning that when serialized to JSON,
   * the enum value will be represented as its corresponding string.
   *
   * @return The string representation of the delivery status.
   */
  @JsonValue
  public String getStatus() {
    return this.status;
  }
}
