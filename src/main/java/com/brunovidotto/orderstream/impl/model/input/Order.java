package com.brunovidotto.orderstream.impl.model.input;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents an incoming order line as read from the JSON input stream.
 * <p>
 * An {@code Order} includes:
 * <ul>
 *   <li>An {@link #orderId} unique string identifier.</li>
 *   <li>An {@link #orderStatus} which is one of "created", "delivered", or "cancelled".</li>
 *   <li>A {@link #delivery} object describing when and where this order is delivered.</li>
 *   <li>An {@link #amount} indicating the amount paid, in cents (may be {@code null}).</li>
 * </ul>
 *
 * @param orderId     The unique identifier for the order.
 * @param orderStatus The status of the order (e.g. "created", "delivered", or "cancelled").
 * @param delivery    The associated {@link Delivery} data (including delivery ID and time).
 * @param amount      The paid amount for this order in cents, or {@code null} if not applicable.
 */
public record Order(
    @JsonProperty("order_id")
    String orderId,
    @JsonProperty("order_status")
    OrderStatus orderStatus,
    @JsonProperty("delivery")
    Delivery delivery,
    @JsonProperty("amount")
    Integer amount
) {}
