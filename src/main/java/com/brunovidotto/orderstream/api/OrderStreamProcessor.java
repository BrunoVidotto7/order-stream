package com.brunovidotto.orderstream.api;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import javax.annotation.WillNotClose;

/**
 * Processes a stream of orders and writes the result to an output stream.
 */
public interface OrderStreamProcessor {
    /**
     * Processes data from the given input stream, sending the result to the provided output stream.
     *
     * @param source The source of data to be processed.
     * @param sink   The sink to which the processing result is sent.
     * @throws IOException Upon failure to read from the source or write to the sink.
     */
    void process(@WillNotClose InputStream source, @WillNotClose OutputStream sink)
            throws IOException;
}
