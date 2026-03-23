package com.dellamas.http3demo;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class TravelAggregatorApp {

    private final HttpClient client;

    public TravelAggregatorApp(HttpClient client) {
        this.client = client;
    }

    public List<HotelOffer> fetchQuotes(List<URI> suppliers, String payload) {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<HotelOffer>> futures = suppliers.stream()
                    .map(uri -> executor.submit(() -> sendRequest(uri, payload)))
                    .toList();

            List<HotelOffer> offers = new ArrayList<>();
            for (Future<HotelOffer> future : futures) {
                offers.add(joinUnchecked(future));
            }
            return offers.stream()
                    .sorted(Comparator.comparingDouble(HotelOffer::price))
                    .toList();
        }
    }

    private HotelOffer joinUnchecked(Future<HotelOffer> future) {
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Thread interrupted while waiting supplier response", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Supplier request failed", e.getCause());
        }
    }

    private HotelOffer sendRequest(URI uri, String payload) {
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
            return HotelOffer.fromJson(response.body());
        } catch (IOException e) {
            throw new IllegalStateException("I/O error calling supplier " + uri, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while calling supplier " + uri, e);
        }
    }

    public static void main(String[] args) throws Exception {
        MockSupplierServer server = new MockSupplierServer(8080);
        server.start();

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
                URI.create("http://localhost:8080/api/supplier-a"),
                URI.create("http://localhost:8080/api/supplier-b"),
                URI.create("http://localhost:8080/api/supplier-c"));

        try {
            List<HotelOffer> offers = app.fetchQuotes(suppliers, payload);
            System.out.println("Best offers for Gramado:");
            offers.forEach(System.out::println);
        } finally {
            server.stop();
        }
    }

    public record HotelOffer(String supplier, String hotel, double price, String currency) {

        static HotelOffer fromJson(String json) {
            return new HotelOffer(
                    extract(json, "supplier"),
                    extract(json, "hotel"),
                    Double.parseDouble(extract(json, "price")),
                    extract(json, "currency"));
        }

        private static String extract(String json, String field) {
            String token = "\"" + field + "\":";
            int start = json.indexOf(token);
            if (start < 0) {
                throw new IllegalArgumentException("Field not found: " + field);
            }
            start += token.length();
            while (start < json.length() && Character.isWhitespace(json.charAt(start))) {
                start++;
            }
            if (json.charAt(start) == '"') {
                int end = json.indexOf('"', start + 1);
                return json.substring(start + 1, end);
            }
            int end = start;
            while (end < json.length() && ",}\n".indexOf(json.charAt(end)) == -1) {
                end++;
            }
            return json.substring(start, end).trim();
        }

        @Override
        public String toString() {
            return supplier + " -> " + hotel + " (" + currency + " " + price + ")";
        }
    }

    static class MockSupplierServer {
        private final HttpServer server;

        MockSupplierServer(int port) throws IOException {
            this.server = HttpServer.create(new InetSocketAddress(port), 0);
            this.server.createContext("/api/supplier-a", new StaticJsonHandler(
                    "{\"supplier\":\"supplier-a\",\"hotel\":\"Mountain View Inn\",\"price\":620.90,\"currency\":\"BRL\"}",
                    200,
                    250));
            this.server.createContext("/api/supplier-b", new StaticJsonHandler(
                    "{\"supplier\":\"supplier-b\",\"hotel\":\"Lago Negro Resort\",\"price\":580.40,\"currency\":\"BRL\"}",
                    200,
                    700));
            this.server.createContext("/api/supplier-c", new StaticJsonHandler(
                    "{\"supplier\":\"supplier-c\",\"hotel\":\"Centro Premium Stay\",\"price\":599.99,\"currency\":\"BRL\"}",
                    200,
                    400));
            this.server.setExecutor(Executors.newFixedThreadPool(4));
        }

        void start() {
            server.start();
        }

        void stop() {
            server.stop(0);
        }
    }

    static class StaticJsonHandler implements HttpHandler {
        private final String body;
        private final int status;
        private final long delayInMillis;

        StaticJsonHandler(String body, int status, long delayInMillis) {
            this.body = body;
            this.status = status;
            this.delayInMillis = delayInMillis;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                    send(exchange, 405, "{\"error\":\"method not allowed\"}");
                    return;
                }
                Thread.sleep(delayInMillis);
                send(exchange, status, body);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                send(exchange, 500, "{\"error\":\"interrupted\"}");
            }
        }

        private void send(HttpExchange exchange, int httpStatus, String responseBody) throws IOException {
            byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(httpStatus, bytes.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(bytes);
            }
        }
    }
}
