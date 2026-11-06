package com.brunovidotto.orderstream.impl.model.input;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/**
 * Represents the "delivery" sub-object in an incoming order.
 * <p>
 * This record stores both:
 * <ul>
 *   <li>{@link #deliveryTime}: A parsed {@link Instant} for easy sorting/comparison.</li>
 *   <li>{@link #rawDeliveryTime}: The exact ISO 8601 date-time string from the JSON,
 *       so it can be re-output in its original format if needed.</li>
 * </ul>
 *
 * @param deliveryId      A unique identifier for this delivery.
 * @param deliveryTime    The parsed date/time of this delivery as an {@link Instant}.
 * @param rawDeliveryTime The exact date/time string as received from JSON, in ISO 8601 format.
 */
public record Delivery(
    @JsonProperty("delivery_id")
    String deliveryId,
    Instant deliveryTime,
    String rawDeliveryTime
) {
  /**
   * Constructs a new {@link Delivery} record by parsing the provided time string into an {@link Instant}.
   *
   * @param deliveryId      A unique identifier for this delivery.
   * @param deliveryTimeStr An ISO 8601-formatted UTC date-time string representing the scheduled delivery time.
   */
  @JsonCreator
  public Delivery(
      @JsonProperty("delivery_id")
      String deliveryId,
      @JsonProperty("delivery_time")
      String deliveryTimeStr
  ) {
    this(
        deliveryId,
        Instant.parse(deliveryTimeStr),
        deliveryTimeStr
    );
  }
}