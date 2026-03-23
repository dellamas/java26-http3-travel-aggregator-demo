package com.dellamas.http3demo;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class TravelAggregatorApp {

    private final HttpClient client;

    public TravelAggregatorApp(HttpClient client) {
        this.client = client;
    }

    public List<String> fetchQuotes(List<URI> suppliers, String payload)
            throws InterruptedException, ExecutionException {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<String>> futures = suppliers.stream()
                    .map(uri -> executor.submit(() -> sendRequest(uri, payload)))
                    .toList();

            return futures.stream().map(this::joinUnchecked).toList();
        }
    }

    private String joinUnchecked(Future<String> future) {
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Thread interrupted while waiting supplier response", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Supplier request failed", e.getCause());
        }
    }

    private String sendRequest(URI uri, String payload) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .version(HttpClient.Version.HTTP_2)
                .timeout(Duration.ofSeconds(3))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();

        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new IllegalStateException("Supplier returned status " + response.statusCode() + " for " + uri);
            }
            return response.body();
        } catch (IOException e) {
            throw new IllegalStateException("I/O error calling supplier " + uri, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while calling supplier " + uri, e);
        }
    }

    public static void main(String[] args) throws Exception {
        HttpClient client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .connectTimeout(Duration.ofSeconds(2))
                .build();

        TravelAggregatorApp app = new TravelAggregatorApp(client);
        String payload = """
                {
                  \"destination\": \"Gramado\",
                  \"checkIn\": \"2026-04-10\",
                  \"checkOut\": \"2026-04-13\",
                  \"guests\": 2
                }
                """;

        List<URI> suppliers = List.of(
                URI.create("https://example.com/api/supplier-a"),
                URI.create("https://example.com/api/supplier-b"),
                URI.create("https://example.com/api/supplier-c"));

        System.out.println(app.fetchQuotes(suppliers, payload));
    }
}
