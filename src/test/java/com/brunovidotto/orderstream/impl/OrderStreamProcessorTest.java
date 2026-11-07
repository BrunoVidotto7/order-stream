package com.brunovidotto.orderstream.impl;

import static org.junit.jupiter.api.Assertions.*;

import com.brunovidotto.orderstream.api.OrderStreamProcessor;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.skyscreamer.jsonassert.JSONAssert;

final class OrderStreamProcessorTest {
    private final OrderStreamProcessorFactoryImpl factory = new OrderStreamProcessorFactoryImpl();

    static String order(String id, String status, String delivery, String amount) {
        return "{\"order_id\":\"" + id + "\",\"order_status\":\"" + status
                + "\",\"delivery\":{\"delivery_id\":\"" + delivery
                + "\",\"delivery_time\":\"2023-01-01T00:00:00Z\"},\"amount\":" + amount + "}";
    }

    private String process(String input, int limit) throws IOException {
        ByteArrayOutputStream sink = new ByteArrayOutputStream();
        factory.createProcessor(limit, Duration.ofSeconds(30)).process(
                new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)), sink);
        return sink.toString(StandardCharsets.UTF_8);
    }

    @Test
    void preservesSortingStatusNullTotalsAndProcessorReuse() throws Exception {
        OrderStreamProcessor processor = factory.createProcessor(10, Duration.ofSeconds(30));
        String input = order("1", "delivered", "B", "null") + "\n"
                + order("3", "cancelled", "B", "999") + "\n"
                + order("2", "cancelled", "A", "7");
        ByteArrayOutputStream sink = new ByteArrayOutputStream();
        processor.process(new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)), sink);
        JSONAssert.assertEquals("""
                [{"delivery_id":"A","delivery_time":"2023-01-01T00:00:00Z","delivery_status":"cancelled",
                  "orders":[{"order_id":"2","amount":7}],"total_amount":null},
                 {"delivery_id":"B","delivery_time":"2023-01-01T00:00:00Z","delivery_status":"delivered",
                  "orders":[{"order_id":"3","amount":999},{"order_id":"1","amount":null}],"total_amount":0}]
                """, sink.toString(StandardCharsets.UTF_8), true);
        sink.reset();
        processor.process(new ByteArrayInputStream(new byte[0]), sink);
        assertEquals("[]", sink.toString(StandardCharsets.UTF_8));
    }

    private static final class TrackingInput extends ByteArrayInputStream {
        boolean closed;

        TrackingInput(String text) {
            super(text.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }
    }

    private static final class TrackingOutput extends ByteArrayOutputStream {
        boolean closed;

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }
    }

    private static final class FailingInput extends InputStream {
        boolean closed;

        @Override
        public int read() throws IOException {
            throw new IOException("source failure");
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    private static final class FailingOutput extends OutputStream {
        @Override
        public void write(int b) throws IOException {
            throw new IOException("sink failure");
        }
    }

    @Test
    void neverClosesSourceOrSinkOnSuccess() throws Exception {
        TrackingInput source = new TrackingInput(order("1", "delivered", "A", "5"));
        TrackingOutput sink = new TrackingOutput();
        factory.createProcessor(10, Duration.ofSeconds(30)).process(source, sink);
        assertFalse(source.closed);
        assertFalse(sink.closed);
    }

    @Test
    void neverClosesSourceOrSinkWithZeroBudgets() throws Exception {
        for (OrderStreamProcessor processor : new OrderStreamProcessor[] {
                factory.createProcessor(0, Duration.ofSeconds(30)),
                factory.createProcessor(10, Duration.ZERO)}) {
            TrackingInput source = new TrackingInput(order("1", "delivered", "A", "5"));
            TrackingOutput sink = new TrackingOutput();
            processor.process(source, sink);
            assertFalse(source.closed);
            assertFalse(sink.closed);
        }
    }

    @Test
    void sourceFailurePropagatesWithoutClosingSource() {
        FailingInput source = new FailingInput();
        IOException e = assertThrows(IOException.class, () ->
                factory.createProcessor(10, Duration.ofSeconds(30)).process(source, new ByteArrayOutputStream()));
        assertEquals("source failure", e.getMessage());
        assertFalse(source.closed);
    }

    @Test
    void sinkFailurePropagates() {
        TrackingInput source = new TrackingInput(order("1", "delivered", "A", "5"));
        IOException e = assertThrows(IOException.class, () ->
                factory.createProcessor(10, Duration.ofSeconds(30)).process(source, new FailingOutput()));
        assertEquals("sink failure", e.getMessage());
        assertFalse(source.closed);
    }

    @Test
    void rejectsRecordsMissingRequiredFieldsAndKeepsValidNeighbors() throws Exception {
        String time = "\"delivery_time\":\"2023-01-01T00:00:00Z\"";
        String input = order("1", "delivered", "A", "5") + "\n"
                + "{\"order_id\":\" \",\"order_status\":\"delivered\",\"delivery\":{\"delivery_id\":\"B\"," + time + "}}\n"
                + "{\"order_status\":\"delivered\",\"delivery\":{\"delivery_id\":\"B\"," + time + "}}\n"
                + "{\"order_id\":\"2\",\"order_status\":\"delivered\"}\n"
                + "{\"order_id\":\"3\",\"order_status\":\"delivered\",\"delivery\":{\"delivery_id\":\"B\"," + time + "}}\n"
                + "{\"order_id\":\"4\",\"order_status\":\"delivered\",\"delivery\":{\"delivery_id\":\" \"," + time + "}}\n"
                + "{\"order_id\":\"5\",\"order_status\":\"delivered\",\"delivery\":{" + time + "}}\n"
                + "{\"order_id\":\"6\",\"order_status\":\"delivered\",\"delivery\":{\"delivery_id\":\"B\"}}\n"
                + "{\"order_id\":\"7\",\"delivery\":{\"delivery_id\":\"B\"," + time + "}}\n"
                + "null\n"
                + order("8", "cancelled", "C", "9");
        JSONAssert.assertEquals("""
                [{"delivery_id":"A","delivery_time":"2023-01-01T00:00:00Z","delivery_status":"delivered",
                  "orders":[{"order_id":"1","amount":5}],"total_amount":5},
                 {"delivery_id":"B","delivery_time":"2023-01-01T00:00:00Z","delivery_status":"delivered",
                  "orders":[{"order_id":"3","amount":null}],"total_amount":0},
                 {"delivery_id":"C","delivery_time":"2023-01-01T00:00:00Z","delivery_status":"cancelled",
                  "orders":[{"order_id":"8","amount":9}],"total_amount":null}]
                """, process(input, 20), true);
    }

    @Test
    void rejectsWholeLineWithTrailingContent() throws Exception {
        String valid = order("1", "delivered", "A", "5");
        String input = valid + " garbage\n"
                + valid + valid + "\n"
                + order("2", "delivered", "B", "6");
        JSONAssert.assertEquals("""
                [{"delivery_id":"B","delivery_time":"2023-01-01T00:00:00Z","delivery_status":"delivered",
                  "orders":[{"order_id":"2","amount":6}],"total_amount":6}]
                """, process(input, 10), true);
    }

    @Test
    void rejectsFractionalAmountInsteadOfTruncating() throws Exception {
        String input = order("1", "delivered", "A", "12.5") + "\n"
                + order("3", "delivered", "C", "12.0") + "\n"
                + order("2", "delivered", "B", "6");
        JSONAssert.assertEquals("""
                [{"delivery_id":"B","delivery_time":"2023-01-01T00:00:00Z","delivery_status":"delivered",
                  "orders":[{"order_id":"2","amount":6}],"total_amount":6}]
                """, process(input, 10), true);
    }

    @Test
    void totalsDoNotOverflowIntAndIgnoreCancelledAmounts() throws Exception {
        String input = order("1", "delivered", "A", "2147483647") + "\n"
                + order("2", "delivered", "A", "2147483647") + "\n"
                + order("3", "cancelled", "A", "2147483647");
        JSONAssert.assertEquals("""
                [{"delivery_id":"A","delivery_time":"2023-01-01T00:00:00Z","delivery_status":"delivered",
                  "orders":[{"order_id":"3","amount":2147483647},{"order_id":"2","amount":2147483647},
                            {"order_id":"1","amount":2147483647}],"total_amount":4294967294}]
                """, process(input, 10), true);
    }

    @Test
    void rejectsAmountBeyondIntRangeAndKeepsNeighbors() throws Exception {
        String input = order("1", "delivered", "A", "2147483648") + "\n"
                + order("2", "delivered", "B", "6");
        JSONAssert.assertEquals("""
                [{"delivery_id":"B","delivery_time":"2023-01-01T00:00:00Z","delivery_status":"delivered",
                  "orders":[{"order_id":"2","amount":6}],"total_amount":6}]
                """, process(input, 10), true);
    }

    @Test
    void oversizedRecordCountsAsOneRejectedRecord() throws Exception {
        String tooLong = "x".repeat(1_048_577);
        String input = tooLong + "\n"
                + order("1", "delivered", "A", "5") + "\n"
                + order("2", "delivered", "B", "6");
        JSONAssert.assertEquals("""
                [{"delivery_id":"A","delivery_time":"2023-01-01T00:00:00Z","delivery_status":"delivered",
                  "orders":[{"order_id":"1","amount":5}],"total_amount":5}]
                """, process(input, 2), true);
    }

    @Test
    void oversizedWhitespaceLineIsAKeepAliveAndDoesNotConsumeTheBudget() throws Exception {
        String blankish = " ".repeat(1_048_577);
        String input = blankish + "\n" + blankish + "\n"
                + order("1", "delivered", "A", "5") + "\n"
                + order("2", "delivered", "B", "6");
        JSONAssert.assertEquals("""
                [{"delivery_id":"A","delivery_time":"2023-01-01T00:00:00Z","delivery_status":"delivered",
                  "orders":[{"order_id":"1","amount":5}],"total_amount":5}]
                """, process(input, 1), true);
    }

    @Test
    void readsCrAndCrlfSeparatedRecords() throws Exception {
        String input = order("1", "delivered", "A", "5") + "\r"
                + order("2", "delivered", "B", "6") + "\r\n"
                + order("3", "delivered", "C", "7");
        assertEquals(3, new ObjectMapper().readTree(process(input, 10)).size());
    }

    private static final class NoReadInput extends InputStream {
        final AtomicInteger reads = new AtomicInteger();

        @Override
        public int read() {
            reads.incrementAndGet();
            return fail("source must not be read");
        }

        @Override
        public int read(byte[] b, int off, int len) {
            return read();
        }
    }

    private static final class SlowEndlessLineInput extends InputStream {
        @Override
        public int read() throws IOException {
            return sleepThen('x');
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            b[off] = (byte) sleepThen('x');
            return 1;
        }
    }

    private static int sleepThen(int value) throws IOException {
        try {
            Thread.sleep(20);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted", e);
        }
        return value;
    }

    private static final class EndlessValidLinesInput extends InputStream {
        private final byte[] line = (order("1", "delivered", "A", "5") + "\n").getBytes(StandardCharsets.UTF_8);
        private int pos;

        @Override
        public int read() {
            final byte b = line[pos];
            pos = (pos + 1) % line.length;
            return b;
        }
    }

    @Test
    void zeroMaxOrdersOrZeroMaxTimeNeverReadsSource() throws Exception {
        for (OrderStreamProcessor processor : new OrderStreamProcessor[] {
                factory.createProcessor(0, Duration.ofSeconds(30)),
                factory.createProcessor(10, Duration.ZERO)}) {
            NoReadInput source = new NoReadInput();
            ByteArrayOutputStream sink = new ByteArrayOutputStream();
            processor.process(source, sink);
            assertEquals(0, source.reads.get());
            assertEquals("[]", sink.toString(StandardCharsets.UTF_8));
        }
    }

    @Test
    void hugeMaxTimeWorksWithFiniteInput() throws Exception {
        ByteArrayOutputStream sink = new ByteArrayOutputStream();
        factory.createProcessor(10, Duration.ofSeconds(Long.MAX_VALUE)).process(
                new ByteArrayInputStream(order("1", "delivered", "A", "5").getBytes(StandardCharsets.UTF_8)), sink);
        assertEquals(1, new ObjectMapper().readTree(sink.toString(StandardCharsets.UTF_8)).size());
    }

    @Test
    void neverEndingSlowLineStopsAfterMaxTime() {
        ByteArrayOutputStream sink = new ByteArrayOutputStream();
        assertTimeoutPreemptively(Duration.ofSeconds(10), () ->
                factory.createProcessor(Integer.MAX_VALUE, Duration.ofMillis(200))
                        .process(new SlowEndlessLineInput(), sink));
        assertEquals("[]", sink.toString(StandardCharsets.UTF_8));
    }

    @Test
    void endlessValidLinesStopAfterMaxTime() throws Exception {
        ByteArrayOutputStream sink = new ByteArrayOutputStream();
        assertTimeoutPreemptively(Duration.ofSeconds(10), () ->
                factory.createProcessor(Integer.MAX_VALUE, Duration.ofMillis(200))
                        .process(new EndlessValidLinesInput(), sink));
        assertEquals(1, new ObjectMapper().readTree(sink.toString(StandardCharsets.UTF_8)).size());
    }

    @Test
    void rejectionLogsContainOnlyOrdinalAndFixedReason() throws Exception {
        final String secret = "SECRET-PAYLOAD-123";
        final Logger logger = (Logger) LoggerFactory.getLogger(OrderStreamProcessorImpl.class);
        final Level oldLevel = logger.getLevel();
        final ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.DEBUG);
        try {
            String input = "{\"order_id\":\"" + secret + "\"\n"
                    + order("2", secret, "A", "5") + "\n"
                    + "{\"order_id\":\"" + secret + "\",\"order_status\":\"delivered\"}\n"
                    + secret + "x".repeat(1_048_577) + "\n"
                    + "null\n"
                    + order("6", "delivered", "A", "5");
            assertEquals(1, new ObjectMapper().readTree(process(input, 10)).size());
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(oldLevel);
        }

        List<String> warnings = appender.list.stream()
                .filter(e -> e.getLevel() == Level.WARN)
                .map(ILoggingEvent::getFormattedMessage)
                .toList();
        assertEquals(List.of(
                "Skipping record 1: malformed JSON",
                "Skipping record 2: invalid order data",
                "Skipping record 3: missing required field",
                "Skipping record 4: record too long",
                "Skipping record 5: missing required field"), warnings);
        for (ILoggingEvent event : appender.list) {
            assertFalse(event.getFormattedMessage().contains(secret));
            assertNull(event.getThrowableProxy());
        }
    }

    private List<String> infoLogs(String input, int limit) throws IOException {
        final Logger logger = (Logger) LoggerFactory.getLogger(OrderStreamProcessorImpl.class);
        final ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            process(input, limit);
        } finally {
            logger.detachAppender(appender);
        }
        return appender.list.stream()
                .filter(e -> e.getLevel() == Level.INFO)
                .map(ILoggingEvent::getFormattedMessage)
                .toList();
    }

    @Test
    void logsWhyReadingStoppedAtEndOfInput() throws Exception {
        String input = order("1", "delivered", "A", "5") + "\n\n" + order("2", "delivered", "B", "6");
        assertTrue(infoLogs(input, 10).contains("Stopped reading after 2 records: end of input"));
    }

    @Test
    void logsWhyReadingStoppedAtMaxOrders() throws Exception {
        String input = order("1", "delivered", "A", "5") + "\n" + order("2", "delivered", "B", "6");
        assertTrue(infoLogs(input, 1).contains("Stopped reading after 1 records: max orders reached"));
    }

    @Test
    void logsWhyReadingStoppedWhenTimeBudgetElapsed() {
        final Logger logger = (Logger) LoggerFactory.getLogger(OrderStreamProcessorImpl.class);
        final ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            assertTimeoutPreemptively(Duration.ofSeconds(10), () ->
                    factory.createProcessor(Integer.MAX_VALUE, Duration.ofMillis(200))
                            .process(new SlowEndlessLineInput(), new ByteArrayOutputStream()));
        } finally {
            logger.detachAppender(appender);
        }
        assertTrue(appender.list.stream().anyMatch(e ->
                e.getFormattedMessage().equals("Stopped reading after 0 records: time budget elapsed")));
    }

    @Test
    void rejectsInvalidLimits() {
        assertEquals("maxOrders must not be negative", assertThrows(IllegalArgumentException.class,
                () -> factory.createProcessor(-1, Duration.ofSeconds(1))).getMessage());
        assertEquals("maxTime", assertThrows(NullPointerException.class,
                () -> factory.createProcessor(1, null)).getMessage());
        assertEquals("maxTime must not be negative", assertThrows(IllegalArgumentException.class,
                () -> factory.createProcessor(1, Duration.ofMillis(-1))).getMessage());
    }
}
