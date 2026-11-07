package com.brunovidotto.orderstream.impl;

import java.io.IOException;
import java.io.Reader;
import java.util.function.BooleanSupplier;

/**
 * Reads lines (LF, CRLF or CR terminated) from a {@link Reader} without ever holding more than
 * {@code maxChars} characters of a line. The wrapped reader is never closed.
 */
final class BoundedLineReader {
  private static final int BUFFER_SIZE = 8192;

  /**
   * Outcome of one {@link #readLine()} call. {@code text} is only set for {@link Kind#LINE}.
   */
  record Result(Kind kind, String text) {
    enum Kind { LINE, TOO_LONG, END, TIME_UP }

    private static final Result TOO_LONG = new Result(Kind.TOO_LONG, null);
    private static final Result END = new Result(Kind.END, null);
    private static final Result TIME_UP = new Result(Kind.TIME_UP, null);
  }

  private final Reader reader;
  private final int maxChars;
  private final BooleanSupplier timeUp;
  private final char[] buffer = new char[BUFFER_SIZE];
  private int pos;
  private int limit;
  private boolean skipLf;

  /**
   * @param reader   the reader to read from; never closed.
   * @param maxChars the longest accepted line, in characters, without its terminator.
   * @param timeUp   checked before every read from {@code reader}; when true, reading stops.
   */
  BoundedLineReader(Reader reader, int maxChars, BooleanSupplier timeUp) {
    this.reader = reader;
    this.maxChars = maxChars;
    this.timeUp = timeUp;
  }

  /**
   * Returns the next line, {@link Result.Kind#TOO_LONG} if the line exceeded the limit (the rest of
   * it is discarded; a whitespace-only line is returned as an empty line instead), {@link Result.Kind#END} at end of input, or {@link Result.Kind#TIME_UP} if
   * time ran out (a partial line is discarded).
   */
  Result readLine() throws IOException {
    final StringBuilder line = new StringBuilder();
    boolean tooLong = false;
    boolean anyChars = false;
    boolean anyNonWhitespace = false;

    while (true) {
      if (pos == limit) {
        if (timeUp.getAsBoolean()) {
          return Result.TIME_UP;
        }
        final int read = reader.read(buffer, 0, buffer.length);
        if (read < 0) {
          if (tooLong) {
            return tooLongResult(anyNonWhitespace);
          }
          return anyChars ? new Result(Result.Kind.LINE, line.toString()) : Result.END;
        }
        pos = 0;
        limit = read;
        continue;
      }

      final char c = buffer[pos++];
      if (skipLf) {
        skipLf = false;
        if (c == '\n') {
          continue;
        }
      }
      if (c == '\n' || c == '\r') {
        skipLf = c == '\r';
        return tooLong ? tooLongResult(anyNonWhitespace) : new Result(Result.Kind.LINE, line.toString());
      }

      anyChars = true;
      anyNonWhitespace |= !Character.isWhitespace(c);
      if (!tooLong) {
        if (line.length() == maxChars) {
          tooLong = true;
          line.setLength(0);
        } else {
          line.append(c);
        }
      }
    }
  }

  /** A too long line made only of whitespace is a blank keep-alive, so it is returned as an empty line. */
  private static Result tooLongResult(boolean anyNonWhitespace) {
    return anyNonWhitespace ? Result.TOO_LONG : new Result(Result.Kind.LINE, "");
  }
}
