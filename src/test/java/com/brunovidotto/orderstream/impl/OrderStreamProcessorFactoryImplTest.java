package com.brunovidotto.orderstream.impl;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Iterator;
import java.util.Scanner;
import java.util.ServiceLoader;
import java.util.stream.Stream;
import org.json.JSONException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import com.brunovidotto.orderstream.api.OrderStreamProcessor;
import com.brunovidotto.orderstream.api.OrderStreamProcessorFactory;

final class OrderStreamProcessorFactoryImplTest {
    /**
     * Provides inline JSON data and corresponding expected outputs
     * to validate various scenarios
     */
    private static Stream<Arguments> testProcessInputProvider() {
        return Stream.of(
                Arguments.of(
                        100, Duration.ofSeconds(30), "happy-path-input.json-stream", "happy-path-output.json"),

            //ADDITIONAL TESTS
            // 1) Limited Orders: Ensures maxOrders stops processing after reaching limit.
            Arguments.of(
                1,
                Duration.ofSeconds(30),
                "scenario-1-limited-orders/input.json-stream",
                "scenario-1-limited-orders/output.json"
            ),

            // 2) Ignore Non-Delivered Orders: Orders with statuses "created" and "other" should be ignored.
            Arguments.of(
                5,
                Duration.ofSeconds(30),
                "scenario-2-ignore-non-delivered/input.json-stream",
                "scenario-2-ignore-non-delivered/output.json"
            ),

            // 3) Delivered vs. Cancelled Orders: Sorting, summation, and tie-breaking logic for orders.
            Arguments.of(
                10,
                Duration.ofSeconds(30),
                "scenario-3-delivered-vs-cancelled/input.json-stream",
                "scenario-3-delivered-vs-cancelled/output.json"
            ),

            // 4) Null Amounts Handling: Ensures null amounts in delivered orders don't affect total.
            Arguments.of(
                10,
                Duration.ofMillis(500),
                "scenario-4-null-amounts/input.json-stream",
                "scenario-4-null-amounts/output.json"
            ),
            // 5) Blank Lines Handling: Verifies blank lines are skipped without affecting processing.
            Arguments.of(
                10,
                Duration.ofMillis(30),
                "scenario-5-blank-lines/input.json-stream",
                "empty-output.json"
            ),
            // 6) No Valid Orders: Ensures output is empty when no valid orders are present.
            Arguments.of(
                5,
                Duration.ofMillis(30),
                "scenario-6-no-valid-orders/input.json-stream",
                "scenario-6-no-valid-orders/output.json"
            ),
            // 7) Null Input Stream: Confirms behavior when the input stream is null (should return an empty result).
            Arguments.of(
                10, Duration.ofSeconds(30),
                "scenario-7-null-input/input.json-stream",
                "empty-output.json"
            ),
            // 8) Zero maxOrders: No lines should be processed if maxOrders is set to zero.
            Arguments.of(
                0,
                Duration.ofSeconds(30),
                "scenario-8-zero-max-orders/input.json-stream",
                "empty-output.json"
            ),
            // 9) Zero maxTime: No lines should be processed if maxTime is set to zero.
            Arguments.of(
                10,
                Duration.ofSeconds(0),
                "scenario-9-zero-max-time/input.json-stream",
                "empty-output.json"
            ),
            // 10) skip malformed lines: No lines should be processed if maxTime is set to zero.
            Arguments.of(
                10,
                Duration.ofSeconds(30),
                "scenario-10-skip-malformed-lines/input.json-stream",
                "scenario-10-skip-malformed-lines/output.json"
            )

        );
    }

    @ParameterizedTest(name = "[{index}] {2} -> {3}")
    @MethodSource("testProcessInputProvider")
    void testProcess(
            int maxOrders, Duration maxTime, String inputResource, String expectedOutputResource)
            throws IOException, JSONException {


        try (InputStream source = getClass().getResourceAsStream(inputResource);
             ByteArrayOutputStream sink = new ByteArrayOutputStream()) {
            OrderStreamProcessorFactory factory = new OrderStreamProcessorFactoryImpl();
            OrderStreamProcessor processor = factory.createProcessor(maxOrders, maxTime);
            processor.process(source, sink);
            String expectedOutput = loadResource(expectedOutputResource);
            String actualOutput = sink.toString(StandardCharsets.UTF_8);
            JSONAssert.assertEquals(expectedOutput, actualOutput, JSONCompareMode.STRICT);
        }
    }

    private String loadResource(String resource) throws IOException {
        try (InputStream is = getClass().getResourceAsStream(resource);
             Scanner scanner = new Scanner(is, StandardCharsets.UTF_8)) {
            scanner.useDelimiter("\\A");
            return scanner.hasNext() ? scanner.next() : "";
        }
    }

    /**
     * Verifies that precisely one {@link OrderStreamProcessorFactory} can be service-loaded.
     */
    @Test
    void testServiceLoading() {
        Iterator<OrderStreamProcessorFactory> factories =
                ServiceLoader.load(OrderStreamProcessorFactory.class).iterator();
        assertTrue(factories.hasNext(), "No OrderStreamProcessorFactory is service-loaded");
        factories.next();
        assertFalse(factories.hasNext(), "More than one OrderStreamProcessorFactory is service-loaded");
    }
}
