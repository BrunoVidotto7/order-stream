# Order Stream

A small Java 21 CLI that processes a live stream of JSON orders: it filters, groups, sorts and
aggregates them, then writes a single JSON document to standard output.

The project is a compact example of stream-oriented processing with a clean separation between the
public API, the processing implementation and the input/output models. It uses Jackson for JSON,
`ServiceLoader` for pluggable processor implementations, and JUnit 5 for parameterised,
fixture-driven tests.

## What it does

The processor reads newline-delimited JSON orders from an `InputStream`. Reading stops when any of
the following happens:

- the maximum number of orders has been read,
- the time budget has elapsed,
- the input stream ends.

Only `delivered` and `cancelled` orders are retained. The retained orders are then:

- grouped by delivery ID,
- ordered by delivery time (ascending), breaking ties by delivery ID (ascending),
- ordered within each delivery by order ID (descending),
- summarised with a delivery status and a total amount for delivered orders.

The result is written to an `OutputStream` as a JSON array.

## Requirements

- JDK 21+
- Maven 3.8+

## Build and test

```bash
mvn clean verify
```

The build runs the full test suite and produces a runnable fat JAR:

```text
target/order-stream-1.0.0-jar-with-dependencies.jar
```

## Usage

The CLI takes two arguments:

```bash
java -jar target/order-stream-1.0.0-jar-with-dependencies.jar <maxOrders> <maxTime>
```

- `maxOrders` — maximum number of input lines to read.
- `maxTime` — maximum time to spend reading, as an ISO-8601 duration (for example `PT30S`).

Example:

```bash
cat orders.json-stream | java -jar target/order-stream-1.0.0-jar-with-dependencies.jar 100 PT30S
```

### Input

Each line is a complete order object:

```json
{"order_id":"1234567890","order_status":"delivered","delivery":{"delivery_id":"d923jd29j91d1gh6","delivery_time":"2022-05-20T11:50:48Z"},"amount":6477}
```

| Field          | Type   | Description                                              |
|:---------------|:-------|:---------------------------------------------------------|
| `order_id`     | String | Unique order identifier.                                 |
| `order_status` | String | One of `created`, `delivered` or `cancelled`.            |
| `delivery`     | Object | Delivery details, containing `delivery_id` and `delivery_time`. |
| `amount`       | Number | Paid amount in cents; may be `null`.                     |

Blank lines are treated as keep-alives and skipped. Lines that cannot be parsed are logged and
skipped, without stopping the rest of the stream.

### Output

```json
[
  {
    "delivery_id": "d923jd29j91d1gh6",
    "delivery_time": "2022-05-20T11:50:48Z",
    "delivery_status": "delivered",
    "orders": [
      {
        "order_id": "1234567890",
        "amount": 6477
      }
    ],
    "total_amount": 6477
  }
]
```

A delivery is marked `delivered` if at least one of its orders was delivered. Its `total_amount` is
the sum of the amounts of its delivered orders, or `null` when no order was delivered.

## Project structure

```text
src/main/java/com/brunovidotto/orderstream
├── api          Public interfaces and the CLI entry point
├── exception    Domain exceptions
└── impl         Processing implementation, factory and I/O models
```

- `api/OrderStreamProcessor` — processes an input stream into an output stream.
- `api/OrderStreamProcessorFactory` — creates processors with an order and time limit.
- `impl/OrderStreamProcessorImpl` — the filtering, grouping, sorting and aggregation logic.
- `impl/model` — immutable records for input and output data.

The processor implementation is discovered through `ServiceLoader`, so another implementation can
be plugged in without changing the CLI.

## Tests

The test suite is parameterised over JSON fixtures covering:

- the happy path,
- order and time limits,
- ignored order statuses,
- delivered versus cancelled deliveries,
- `null` amounts,
- blank and malformed lines,
- empty streams,
- service loading of exactly one processor factory.

Run only the tests with:

```bash
mvn test
```
