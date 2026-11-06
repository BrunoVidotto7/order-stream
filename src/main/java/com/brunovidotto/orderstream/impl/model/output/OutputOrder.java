package com.brunovidotto.orderstream.impl.model.output;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A simple DTO that represents an order in the output JSON. Each instance corresponds to an entry in
 * the "orders" array of a grouped delivery.
 *
 * @param orderId The unique identifier of the order.
 * @param amount   The paid amount in cents, or {@code null} if not applicable.
 */
public record OutputOrder(
    @JsonProperty("order_id")
    String orderId,
    Integer amount
) {}
