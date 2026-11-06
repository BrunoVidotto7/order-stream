package com.brunovidotto.orderstream.api;

import java.time.Duration;

/**
 * Creates {@link OrderStreamProcessor} instances that limit the amount of data they process.
 */
public interface OrderStreamProcessorFactory {
    /**
     * Returns a new {@link OrderStreamProcessor} which limits input processing as configured.
     *
     * @param maxOrders the desired and maximum number of orders to be processed.
     * @param maxTime   the maximum amount of time to process orders for; the processor must stop
     *                  reading from its input once this time has elapsed.
     * @return a {@link OrderStreamProcessor} which reads orders from its input stream and writes the
     * result of processing said orders out its output stream.
     */
    OrderStreamProcessor createProcessor(int maxOrders, Duration maxTime);
}
