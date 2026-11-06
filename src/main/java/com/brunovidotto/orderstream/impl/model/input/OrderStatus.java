package com.brunovidotto.orderstream.impl.model.input;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.brunovidotto.orderstream.exception.InvalidOrderStatusException;

/**
 * Enum representing the possible statuses of an order in the input model.
 * <p>
 * This enum defines the various stages an order can go through:
 * <ul>
 *   <li>{@link #CREATED} - The order has been created.</li>
 *   <li>{@link #DELIVERED} - The order has been successfully delivered to the customer.</li>
 *   <li>{@link #CANCELED} - The order was canceled before being fulfilled.</li>
 * </ul>
 * <p>
 * This enum integrates with Jackson for JSON serialization and deserialization.
 */
public enum OrderStatus {
  /**
   * Order status indicating that the order has been created.
   */
  CREATED("created"),

  /**
   * Order status indicating that the order has been successfully delivered.
   */
  DELIVERED("delivered"),

  /**
   * Order status indicating that the order was canceled before being fulfilled.
   */
  CANCELED("cancelled");


  private final String status;

  /**
   * Constructor to assign a string representation to the enum value.
   *
   * @param status The string representation of the order status.
   */
  OrderStatus(String status) {
    this.status = status;
  }

  /**
   * Returns the string representation of the order status.
   * <p>
   * This method is annotated with {@link JsonValue}, meaning that when serialized to JSON,
   * the enum value will be represented as its corresponding string.
   *
   * @return The string representation of the order status.
   */
  @JsonValue
  public String getStatus() {
    return this.status;
  }

  /**
   * Converts a string value into an {@link OrderStatus} enum.
   * <p>
   * This method is annotated with {@link JsonCreator}, allowing Jackson to automatically
   * map a JSON string to the appropriate enum value during deserialization.
   *
   * @param status The string representation of the order status.
   * @return The corresponding {@link OrderStatus} enum.
   * @throws InvalidOrderStatusException If the given status string does not match any known value.
   */
  @JsonCreator
  public static OrderStatus fromString(String status) {
    for (OrderStatus orderStatus : OrderStatus.values()) {
      if (orderStatus.status.equalsIgnoreCase(status)) {
        return orderStatus;
      }
    }
    throw new InvalidOrderStatusException("Unknown order status: " + status);
  }
}
