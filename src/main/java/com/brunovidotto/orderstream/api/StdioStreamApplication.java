package com.brunovidotto.orderstream.api;

import java.io.IOException;
import java.time.Duration;
import java.util.Iterator;
import java.util.ServiceLoader;

/**
 * Command line entry point: pipes stdin through the first service-loaded {@link
 * OrderStreamProcessor} and writes the result to stdout.
 */
public final class StdioStreamApplication {
    private StdioStreamApplication() {
    }

    /**
     * Pipes stdin through the first {@link OrderStreamProcessor} that can {@link ServiceLoader
     * service-loaded}, sending the result to stdout.
     */
    public static void main(String[] args) throws IOException {
        if (args.length != 2) {
            System.err.printf("Usage:%n  java -jar application.jar <maxOrders> <maxTime>%n");
            System.exit(1);
        }

        int maxOrders = Integer.parseInt(args[0]);
        Duration maxTime = Duration.parse(args[1]);

        processStdio(maxOrders, maxTime);
    }

    private static void processStdio(int maxOrders, Duration maxTime) throws IOException {
        Iterator<OrderStreamProcessorFactory> factories =
                ServiceLoader.load(OrderStreamProcessorFactory.class).iterator();
        if (!factories.hasNext()) {
            throw new IllegalStateException("No OrderStreamProcessorFactory found");
        }

        OrderStreamProcessorFactory factory = factories.next();
        OrderStreamProcessor processor = factory.createProcessor(maxOrders, maxTime);
        processor.process(System.in, System.out);
    }
}
