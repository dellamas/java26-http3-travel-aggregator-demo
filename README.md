# java26-http3-travel-aggregator-demo

Small Java demo that simulates a travel aggregator calling multiple supplier APIs concurrently with `HttpClient` and virtual threads.

## Why this repo exists

I used this demo as the companion code for an article about HTTP/3 in Java 26. The point is not to create a full booking platform, but to show how a backend service can fan out requests to multiple providers and keep the code simple, readable, and resilient.

## What it shows

- `HttpClient` usage for outbound calls
- virtual threads for concurrent fan-out
- explicit timeout configuration
- basic error handling for supplier failures
- tests around aggregation and failure behavior

## Running tests

```bash
mvn test
```

## Important note

This sample uses `HttpClient.Version.HTTP_2` by default so it compiles and runs in a stable setup. If you are testing on Java 26 with HTTP/3 available, you can adapt the client configuration and benchmark real integrations.
