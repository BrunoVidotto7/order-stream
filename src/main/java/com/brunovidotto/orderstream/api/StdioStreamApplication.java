package com.brunovidotto.orderstream.api;

import java.io.IOException;
import java.time.Duration;
import java.time.format.DateTimeParseException;
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
            exitWithUsage("Expected exactly two arguments.");
            return;
        }

        final OrderStreamProcessor processor;
        try {
            processor = createProcessor(Integer.parseInt(args[0]), Duration.parse(args[1]));
        } catch (IllegalArgumentException | DateTimeParseException e) {
            exitWithUsage("Invalid arguments: maxOrders must be a non-negative integer and maxTime "
                    + "a non-negative ISO-8601 duration such as PT30S.");
            return;
        }

        processor.process(System.in, System.out);
        if (System.out.checkError()) {
            System.err.println("Failed to write output.");
            System.exit(1);
        }
    }

    private static void exitWithUsage(String message) {
        System.err.printf("%s%nUsage:%n  java -jar application.jar <maxOrders> <maxTime>%n", message);
        System.exit(1);
    }

    private static OrderStreamProcessor createProcessor(int maxOrders, Duration maxTime) {
        Iterator<OrderStreamProcessorFactory> factories =
                ServiceLoader.load(OrderStreamProcessorFactory.class).iterator();
        if (!factories.hasNext()) {
            throw new IllegalStateException("No OrderStreamProcessorFactory found");
        }

        return factories.next().createProcessor(maxOrders, maxTime);
    }
}
