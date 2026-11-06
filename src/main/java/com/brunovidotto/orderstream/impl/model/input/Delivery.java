package com.brunovidotto.orderstream.impl.model.input;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/**
 * Represents the "delivery" sub-object in an incoming order.
 * <p>
 * The {@link #deliveryTime} is bound directly to an {@link Instant}, so it is both
 * sortable and serialized back as a standard ISO 8601 instant.
 *
 * @param deliveryId   A unique identifier for this delivery.
 * @param deliveryTime The date/time of this delivery as an {@link Instant}.
 */
public record Delivery(
    @JsonProperty("delivery_id")
    String deliveryId,
    @JsonProperty("delivery_time")
    Instant deliveryTime
) {}