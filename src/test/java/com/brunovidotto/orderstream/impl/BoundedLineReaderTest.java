package com.brunovidotto.orderstream.impl;

import static com.brunovidotto.orderstream.impl.BoundedLineReader.Result.Kind.END;
import static com.brunovidotto.orderstream.impl.BoundedLineReader.Result.Kind.LINE;
import static com.brunovidotto.orderstream.impl.BoundedLineReader.Result.Kind.TOO_LONG;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

final class BoundedLineReaderTest {
    private static List<String> readAll(String input, int maxChars) throws IOException {
        BoundedLineReader reader = new BoundedLineReader(new StringReader(input), maxChars, () -> false);
        List<String> results = new ArrayList<>();
        BoundedLineReader.Result result;
        while ((result = reader.readLine()).kind() != END) {
            results.add(result.kind() == LINE ? result.text() : "<" + result.kind() + ">");
        }
        return results;
    }

    @Test
    void readsLfCrlfCrAndMixedTerminators() throws IOException {
        assertEquals(List.of("a", "b", "c"), readAll("a\nb\nc\n", 100));
        assertEquals(List.of("a", "b", "c"), readAll("a\r\nb\r\nc\r\n", 100));
        assertEquals(List.of("a", "b", "c"), readAll("a\rb\rc\r", 100));
        assertEquals(List.of("a", "b", "", "c", "d"), readAll("a\nb\r\n\rc\r\nd", 100));
    }

    @Test
    void readsCrlfSplitAcrossBufferBoundary() throws IOException {
        String first = "x".repeat(8191);
        assertEquals(List.of(first, "next"), readAll(first + "\r\nnext", 20_000));
    }

    @Test
    void readsFinalLineWithoutNewlineAndEmptyInput() throws IOException {
        assertEquals(List.of("a", "b"), readAll("a\nb", 100));
        assertEquals(List.of(), readAll("", 100));
    }

    @Test
    void acceptsLineExactlyAtLimit() throws IOException {
        assertEquals(List.of("abcde", "z"), readAll("abcde\nz", 5));
        assertEquals(List.of("abcde"), readAll("abcde", 5));
    }

    @Test
    void rejectsLineOverLimitAndReadsFollowingLine() throws IOException {
        assertEquals(List.of("<" + TOO_LONG + ">", "z"), readAll("abcdef\nz", 5));
        assertEquals(List.of("<" + TOO_LONG + ">", "z"), readAll("abcdef\r\nz", 5));
        assertEquals(List.of("<" + TOO_LONG + ">"), readAll("abcdef", 5));
    }

    @Test
    void discardsLongOversizedLineAcrossManyBuffers() throws IOException {
        String huge = "x".repeat(100_000);
        assertEquals(List.of("<" + TOO_LONG + ">", "z"), readAll(huge + "\nz", 5));
    }

    @Test
    void oversizedWhitespaceOnlyLineIsReturnedAsEmptyLine() throws IOException {
        assertEquals(List.of("", "z"), readAll(" \t   \nz", 3));
        assertEquals(List.of(""), readAll("      ", 3));
        assertEquals(List.of("<" + TOO_LONG + ">"), readAll("     x", 3));
        assertEquals(List.of("<" + TOO_LONG + ">"), readAll("x     ", 3));
    }
}
