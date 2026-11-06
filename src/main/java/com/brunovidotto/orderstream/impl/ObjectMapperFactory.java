package com.brunovidotto.orderstream.impl;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Constructs {@link ObjectMapper} instances with custom features enabled or disabled.
 */
final class ObjectMapperFactory {
    private ObjectMapperFactory() {
    }

    /**
     * You may tweak the configuration of the mapper returned by this method as needed.
     */
    public static ObjectMapper createObjectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.getFactory().disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET);
        // Support java.time types (Instant, etc.) natively
        objectMapper.registerModule(new JavaTimeModule());
        // Write date as ISO-8601 strings
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        return objectMapper;
    }
}

