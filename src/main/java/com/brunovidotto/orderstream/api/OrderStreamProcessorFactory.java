package com.brunovidotto.orderstream.api;

import java.time.Duration;

/**
 * Creates {@link OrderStreamProcessor} instances that limit the amount of data they process.
 */
public interface OrderStreamProcessorFactory {
    /**
     * Returns a new {@link OrderStreamProcessor} which limits input processing as configured.
     *
     * <p>
     * {@code maxTime} is a soft, cooperative budget: reading stops at the first check after the time
     * has elapsed. A single blocking read on the input stream is not interrupted. Zero values are
     * allowed and result in no input being read.
     *
     * @param maxOrders the desired and maximum number of orders to be processed; must not be negative.
     * @param maxTime   the maximum amount of time to process orders for; must not be null or negative.
     * @return a {@link OrderStreamProcessor} which reads orders from its input stream and writes the
     * result of processing said orders out its output stream.
     * @throws IllegalArgumentException if {@code maxOrders} or {@code maxTime} is negative.
     * @throws NullPointerException     if {@code maxTime} is null.
     */
    OrderStreamProcessor createProcessor(int maxOrders, Duration maxTime);
}
