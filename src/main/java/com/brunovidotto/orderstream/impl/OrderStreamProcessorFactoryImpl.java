package com.brunovidotto.orderstream.impl;

import com.google.auto.service.AutoService;
import java.time.Duration;
import com.brunovidotto.orderstream.api.OrderStreamProcessorFactory;
import com.brunovidotto.orderstream.api.OrderStreamProcessor;

@AutoService(OrderStreamProcessorFactory.class)
public final class OrderStreamProcessorFactoryImpl implements OrderStreamProcessorFactory {
    @Override
    public OrderStreamProcessor createProcessor(int maxOrders, Duration maxTime) {
        return new OrderStreamProcessorImpl(maxOrders, maxTime);
    }
}
