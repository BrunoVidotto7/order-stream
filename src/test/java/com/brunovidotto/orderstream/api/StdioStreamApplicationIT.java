package com.brunovidotto.orderstream.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Launches the packaged CLI jar and checks its exit code, stdout and stderr. */
class StdioStreamApplicationIT {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String SECRET = "SECRET-PAYLOAD-123";

    private record Result(int exitCode, String stdout, String stderr) {
    }

    @Test
    void happyPathWritesOnlyTheJsonDocumentToStdout() throws Exception {
        String input = Files.readString(Path.of(
                "src/test/resources/com/brunovidotto/orderstream/impl/happy-path-input.json-stream"));

        Result result = run(input, "100", "PT30S");

        assertEquals(0, result.exitCode());
        JsonNode output = parseSingleDocument(result.stdout());
        assertTrue(output.isArray());
        assertEquals(2, output.size());
    }

    @Test
    void rejectedRecordsAreLoggedToStderrWithoutLeakingTheirContent() throws Exception {
        String input = """
                {not json %s
                {"order_id":"1","order_status":"%s","delivery":{"delivery_id":"d1","delivery_time":"2022-05-20T11:50:48Z"},"amount":1}
                {"order_id":"2","order_status":"delivered","delivery":{"delivery_id":"d2","delivery_time":"2022-05-20T11:50:48Z"},"amount":5}
                """.formatted(SECRET, SECRET);

        Result result = run(input, "100", "PT30S");

        assertEquals(0, result.exitCode());
        JsonNode output = parseSingleDocument(result.stdout());
        assertEquals(1, output.size());
        assertEquals("d2", output.get(0).get("delivery_id").asText());
        assertFalse(result.stdout().contains(SECRET));
        assertFalse(result.stderr().contains(SECRET));
        assertTrue(result.stderr().contains("Skipping record"));
    }

    @Test
    void wrongArgumentCountFailsWithUsage() throws Exception {
        assertCleanFailure(run("", "100"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "-1"})
    void invalidMaxOrdersFailsCleanly(String maxOrders) throws Exception {
        assertCleanFailure(run("", maxOrders, "PT30S"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"30s", "-PT1S"})
    void invalidMaxTimeFailsCleanly(String maxTime) throws Exception {
        assertCleanFailure(run("", "100", maxTime));
    }

    @ParameterizedTest
    @CsvSource({"0, PT30S", "10, PT0S"})
    void zeroBudgetsProduceAnEmptyArray(String maxOrders, String maxTime) throws Exception {
        Result result = run("{\"order_id\":\"1\"}\n", maxOrders, maxTime);

        assertEquals(0, result.exitCode());
        assertEquals("[]", result.stdout().trim());
    }

    @Test
    void failsWithExitCodeOneWhenStdoutCannotBeWritten() throws Exception {
        File stderr = File.createTempFile("cli-stderr", ".txt");
        Process process = new ProcessBuilder(command("100", "PT30S"))
                .redirectError(stderr)
                .start();
        try {
            process.getInputStream().close();
            try (OutputStream in = process.getOutputStream()) {
                in.write("{\"order_id\":\"1\"}\n".getBytes(StandardCharsets.UTF_8));
            } catch (IOException e) {
                // The CLI may exit without reading stdin.
            }
            if (!process.waitFor(30, TimeUnit.SECONDS)) {
                fail("CLI did not finish within 30 seconds");
            }
            assertEquals(1, process.exitValue());
            assertTrue(Files.readString(stderr.toPath()).contains("Failed to write output."));
        } finally {
            process.destroyForcibly();
            Files.deleteIfExists(stderr.toPath());
        }
    }

    private static void assertCleanFailure(Result result) {
        assertEquals(1, result.exitCode());
        assertEquals("", result.stdout());
        assertTrue(result.stderr().contains("Usage:"));
        assertFalse(result.stderr().isBlank());
        assertFalse(result.stderr().contains("Exception"));
        assertFalse(result.stderr().contains("\tat "));
    }

    private static JsonNode parseSingleDocument(String stdout) throws IOException {
        try (JsonParser parser = MAPPER.getFactory().createParser(stdout)) {
            JsonNode document = MAPPER.readTree(parser);
            assertNull(parser.nextToken(), "stdout has content after the JSON document");
            return document;
        }
    }

    private static List<String> command(String... args) {
        List<String> command = new ArrayList<>(List.of(
                System.getProperty("java.home") + "/bin/java", "-jar", System.getProperty("cli.jar")));
        command.addAll(List.of(args));
        return command;
    }

    private static Result run(String stdin, String... args) throws Exception {
        File stdout = File.createTempFile("cli-stdout", ".txt");
        File stderr = File.createTempFile("cli-stderr", ".txt");
        Process process = new ProcessBuilder(command(args))
                .redirectOutput(stdout)
                .redirectError(stderr)
                .start();
        try {
            try (OutputStream in = process.getOutputStream()) {
                in.write(stdin.getBytes(StandardCharsets.UTF_8));
            } catch (IOException e) {
                // The CLI may exit without reading stdin.
            }
            if (!process.waitFor(30, TimeUnit.SECONDS)) {
                fail("CLI did not finish within 30 seconds");
            }
            return new Result(process.exitValue(),
                    Files.readString(stdout.toPath()), Files.readString(stderr.toPath()));
        } finally {
            process.destroyForcibly();
            Files.deleteIfExists(stdout.toPath());
            Files.deleteIfExists(stderr.toPath());
        }
    }
}
