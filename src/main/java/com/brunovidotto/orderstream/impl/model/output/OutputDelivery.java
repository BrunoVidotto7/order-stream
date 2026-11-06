package com.brunovidotto.orderstream.impl.model.output;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a grouped delivery object in the <em>output</em> JSON after processing.
 * <p>
 * The fields mirror the final output format:
 * <ul>
 *   <li>{@link #deliveryId} and {@link #deliveryTime} identify the delivery.</li>
 *   <li>{@link #deliveryStatus} indicates whether at least one order was delivered.</li>
 *   <li>{@link #orders} is the list of orders within this delivery.</li>
 *   <li>{@link #totalAmount} is the sum of amounts of all delivered orders (or {@code null} if no delivered orders).</li>
 * </ul>
 *
 * @param deliveryId     The unique identifier for the delivery.
 * @param deliveryTime   The scheduled delivery time in ISO 8601 format.
 * @param deliveryStatus The status of this delivery in the output ("delivered" or "cancelled").
 * @param orders         The list of orders (by {@code orderId} and {@code amount}) for this delivery.
 * @param totalAmount    The total amount in cents of all "delivered" orders, or {@code null} if none were delivered.
 */
public record OutputDelivery(
    @JsonProperty("delivery_id") String deliveryId,
    @JsonProperty("delivery_time") String deliveryTime,
    @JsonProperty("delivery_status") DeliveryStatus deliveryStatus,
    List<OutputOrder> orders,
    @JsonProperty("total_amount") Integer totalAmount
) {
    /**
     * Builder for {@link OutputDelivery} to facilitate object construction.
     */
    public static class Builder {
        private String deliveryId;
        private String deliveryTime;
        private DeliveryStatus deliveryStatus;
        private List<OutputOrder> orders = new ArrayList<>();
        private Integer totalAmount;

        public Builder() {
            // This constructor is intentionally empty, as it allows for a step-by-step
            // construction of an OutputDelivery object using method chaining.
        }

        /**
         * Sets the delivery ID.
         *
         * @param deliveryId The unique identifier for the delivery.
         * @return The builder instance.
         */
        public Builder deliveryId(String deliveryId) {
            this.deliveryId = deliveryId;
            return this;
        }

        /**
         * Sets the delivery time.
         *
         * @param deliveryTime The scheduled delivery time in ISO 8601 format.
         * @return The builder instance.
         */
        public Builder deliveryTime(String deliveryTime) {
            this.deliveryTime = deliveryTime;
            return this;
        }

        /**
         * Sets the delivery status.
         *
         * @param deliveryStatus The delivery status ("delivered" or "cancelled").
         * @return The builder instance.
         */
        public Builder deliveryStatus(DeliveryStatus deliveryStatus) {
            this.deliveryStatus = deliveryStatus;
            return this;
        }

        /**
         * Adds a single order to the delivery.
         *
         * @param order The order to add.
         * @return The builder instance.
         */
        public Builder addOrder(OutputOrder order) {
            this.orders.add(order);
            return this;
        }

        /**
         * Adds a list of orders to the delivery.
         *
         * @param orders The list of orders to add.
         * @return The builder instance.
         */
        public Builder orders(List<OutputOrder> orders) {
            this.orders = new ArrayList<>(orders);
            return this;
        }

        /**
         * Sets the total amount for delivered orders.
         *
         * @param totalAmount The total amount in cents, or {@code null} if no delivered orders exist.
         * @return The builder instance.
         */
        public Builder totalAmount(Integer totalAmount) {
            this.totalAmount = totalAmount;
            return this;
        }

        /**
         * Builds and returns the immutable {@link OutputDelivery} instance.
         *
         * @return A new {@link OutputDelivery} object.
         */
        public OutputDelivery build() {
            return new OutputDelivery(deliveryId, deliveryTime, deliveryStatus, orders, totalAmount);
        }
    }
}
