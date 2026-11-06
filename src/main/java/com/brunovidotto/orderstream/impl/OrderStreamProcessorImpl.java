package com.brunovidotto.orderstream.impl;

import static com.brunovidotto.orderstream.impl.model.input.OrderStatus.CANCELED;
import static com.brunovidotto.orderstream.impl.model.input.OrderStatus.DELIVERED;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.WillNotClose;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.brunovidotto.orderstream.api.OrderStreamProcessor;
import com.brunovidotto.orderstream.impl.model.input.Order;
import com.brunovidotto.orderstream.impl.model.input.OrderStatus;
import com.brunovidotto.orderstream.impl.model.output.DeliveryStatus;
import com.brunovidotto.orderstream.impl.model.output.OutputDelivery;
import com.brunovidotto.orderstream.impl.model.output.OutputOrder;

/**
 * An {@link OrderStreamProcessor} implementation that:
 * <ul>
 *   <li>Reads up to {@code maxOrders} lines (or until {@code maxTime} elapses, or input ends).</li>
 *   <li>Parses each line into an {@link Order}, filtering out non-relevant statuses.</li>
 *   <li>Groups by delivery ID, sorts by delivery time and ID, and computes total amounts.</li>
 *   <li>Outputs JSON of {@link OutputDelivery} objects.</li>
 * </ul>
 */
public final class OrderStreamProcessorImpl implements OrderStreamProcessor {
  private static final Logger LOG = LoggerFactory.getLogger(OrderStreamProcessorImpl.class);
  private static final List<OrderStatus> VALID_ORDER_STATUS = Arrays.asList(DELIVERED, CANCELED);

  private final int maxOrders;
  private final Duration maxTime;
  private final ObjectMapper mapper;

  public OrderStreamProcessorImpl(int maxOrders, Duration maxTime) {
    this.maxOrders = maxOrders;
    this.maxTime = maxTime;
    this.mapper = ObjectMapperFactory.createObjectMapper();

    LOG.debug("Created OrderStreamProcessorImpl with maxOrders={}, maxTime={}", maxOrders, maxTime);
  }

  /**
   * {@inheritDoc}
   * <p>
   * This implementation processes incoming order data from an {@link InputStream}, filters and groups
   * valid orders, transforms them into structured {@link OutputDelivery} objects, and writes the
   * final JSON output to the provided {@link OutputStream}.
   * <p>
   * The processing consists of three main steps:
   * <ol>
   *   <li><b>Reading & Filtering:</b> Reads up to {@code maxOrders} from the input stream within
   *       the {@code maxTime} limit. Only orders with status {@code DELIVERED} or {@code CANCELED}
   *       are retained and grouped by delivery ID.</li>
   *   <li><b>Transformation:</b> The grouped orders are processed into {@link OutputDelivery}
   *       objects, sorted by delivery time (ascending) and delivery ID (ascending).</li>
   *   <li><b>Serialization:</b> The final list of deliveries is written to the output stream as
   *       a JSON array.</li>
   * </ol>
   *
   * @param source The {@link InputStream} containing newline-separated JSON order records.
   *               <b>Must not be closed</b> within this method.
   * @param sink   The {@link OutputStream} where the processed JSON output is written.
   *               <b>Must not be closed</b> within this method.
   * @throws IOException If an I/O error occurs while reading from {@code source} or writing to {@code sink}.
   */
  public void process(@WillNotClose InputStream source, @WillNotClose OutputStream sink) throws IOException {
    LOG.info("Starting order stream processing...");

    final Map<String, List<Order>> deliveriesMap = readOrders(source);
    LOG.debug("Finished reading orders. Found {} distinct delivery IDs.", deliveriesMap.size());

    final List<OutputDelivery> outputDeliveries = createOutputDeliveries(deliveriesMap);
    LOG.debug("Built {} output deliveries.", outputDeliveries.size());

    LOG.info("Writing {} deliveries to output...", outputDeliveries.size());
    mapper.writeValue(sink, outputDeliveries);

    LOG.info("Order stream processing complete.");
  }

  /**
   * Reads lines from the provided {@code source} until we hit {@code maxOrders}, exceed
   * {@code maxTime}, or reach the end of the stream. Only lines corresponding to valid orders with
   * status {@code DELIVERED} or {@code CANCELED} are retained. All others are skipped, but still
   * count toward the order limit.
   *
   * @param source   The source {@link InputStream} to read from.
   * @return A map of {@code deliveryId -> list of orders}, containing only the relevant orders.
   * @throws IOException If an I/O error occurs while reading the stream.
   */
  private Map<String, List<Order>> readOrders(final InputStream source) throws IOException {
    final Map<String, List<Order>> deliveriesMap = new HashMap<>();
    final long deadlineMillis = System.currentTimeMillis() + maxTime.toMillis();

    LOG.debug("Reading orders up to {} lines or {}ms, whichever occurs first...", maxOrders, maxTime.toMillis());

    try (BufferedReader br = new BufferedReader(new InputStreamReader(source, StandardCharsets.UTF_8))) {
      int linesRead = 0;
      String line;

      while (linesRead < maxOrders
          && System.currentTimeMillis() < deadlineMillis
          && (line = br.readLine()) != null) {

        if (line.isBlank()) {
          LOG.debug("Skipping blank keep-alive line.");
          continue;
        }

        linesRead++;
        final Order parsedOrder = parseOrder(line);


        if (parsedOrder != null && VALID_ORDER_STATUS.contains(parsedOrder.orderStatus())) {
          deliveriesMap
              .computeIfAbsent(parsedOrder.delivery().deliveryId(), k -> new ArrayList<>())
              .add(parsedOrder);
        }
      }
    }

    LOG.debug("Finished reading orders.");
    return deliveriesMap;
  }


  /**
   * Parses a single JSON line into an {@link Order}, returning {@code null} if parsing fails.
   *
   * @param line   A non-blank JSON line representing an order.
   * @return An {@link Order} if successfully parsed, or {@code null} if invalid JSON.
   */
  private Order parseOrder(String line) {
    try {
      return mapper.readValue(line, Order.class);
    } catch (IOException e) {
      LOG.warn("Failed to parse line as Order: '{}'. Skipping...", line);
      return null;
    }
  }

  /**
   * Groups the valid orders from the map into a list of {@link OutputDelivery} records, sorted by:
   * <ol>
   *   <li>{@code deliveryTime} ascending</li>
   *   <li>{@code deliveryId} ascending</li>
   *   <li>{@code orders} by {@code orderId} descending</li>
   * </ol>
   *
   * @param deliveriesMap The map of {@code deliveryId -> list of orders} to transform.
   * @return A sorted list of {@link OutputDelivery} suitable for final serialization.
   */
  private List<OutputDelivery> createOutputDeliveries(final Map<String, List<Order>> deliveriesMap) {
    if (deliveriesMap == null || deliveriesMap.isEmpty()) {
      LOG.debug("No deliveries found to transform; returning empty list.");
      return Collections.emptyList();
    }

    final List<OutputDelivery> outputDeliveries = new ArrayList<>(deliveriesMap.size());

    for (final Map.Entry<String, List<Order>> entry : deliveriesMap.entrySet()) {
      final String deliveryId = entry.getKey();
      final List<Order> orders = entry.getValue();

      if (!orders.isEmpty()) {
        outputDeliveries.add(buildOutputDelivery(deliveryId, orders));
      } else {
        LOG.warn("Skipping delivery with ID '{}' because it has no orders.", deliveryId);
      }
    }

    outputDeliveries.sort(
        Comparator
            .comparing(OutputDelivery::deliveryTime)
            .thenComparing(OutputDelivery::deliveryId)
    );
    return outputDeliveries;
  }

  /**
   * Creates a single {@link OutputDelivery} from the given list of {@link Order} entries,
   * which share a delivery ID.
   *
   * @param deliveryId The unique delivery ID.
   * @param orders     All orders belonging to that ID.
   * @return A new {@link OutputDelivery}.
   */
  private OutputDelivery buildOutputDelivery(final String deliveryId, final List<Order> orders) {
    orders.sort(Comparator.comparing(Order::orderId).reversed());

    final Instant deliveryTime = orders.getFirst().delivery().deliveryTime();

    boolean anyDelivered = false;
    int sum = 0;
    List<OutputOrder> outputOrders = new ArrayList<>(orders.size());

    for (Order order : orders) {
      outputOrders.add(new OutputOrder(order.orderId(), order.amount()));

      if (DELIVERED.equals(order.orderStatus())) {
        anyDelivered = true;
        if (order.amount() != null) {
          sum += order.amount();
        }
      }
    }

    final DeliveryStatus deliveryStatus = anyDelivered ? DeliveryStatus.DELIVERED : DeliveryStatus.CANCELED;
    final Integer totalAmount = anyDelivered ? sum : null;

    return new OutputDelivery.Builder()
        .deliveryId(deliveryId)
        .deliveryTime(deliveryTime)
        .deliveryStatus(deliveryStatus)
        .orders(outputOrders)
        .totalAmount(totalAmount)
        .build();
  }
}
