package com.dellamas.http3demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

class TravelAggregatorAppTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void shouldAggregateSupplierResponsesAndSortByPrice() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/supplier-a", new JsonHandler("{\"supplier\":\"a\",\"hotel\":\"Hotel A\",\"price\":620.0,\"currency\":\"BRL\"}", 200));
        server.createContext("/supplier-b", new JsonHandler("{\"supplier\":\"b\",\"hotel\":\"Hotel B\",\"price\":580.0,\"currency\":\"BRL\"}", 200));
        server.start();

        var client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();

        var app = new TravelAggregatorApp(client);
        var result = app.fetchQuotes(List.of(
                URI.create("http://localhost:" + server.getAddress().getPort() + "/supplier-a"),
                URI.create("http://localhost:" + server.getAddress().getPort() + "/supplier-b")), "{}");

        assertEquals(2, result.size());
        assertEquals("b", result.get(0).supplier());
        assertEquals(580.0, result.get(0).price());
    }

    @Test
    void shouldFailWhenSupplierReturnsErrorStatus() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/supplier-a", new JsonHandler("{\"error\":\"down\"}", 503));
        server.start();

        var client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();

        var app = new TravelAggregatorApp(client);

        assertThrows(IllegalStateException.class,
                () -> app.fetchQuotes(List.of(
                        URI.create("http://localhost:" + server.getAddress().getPort() + "/supplier-a")), "{}"));
    }

    static class JsonHandler implements HttpHandler {
        private final String body;
        private final int status;

        JsonHandler(String body, int status) {
            this.body = body;
            this.status = status;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            byte[] bytes = body.getBytes();
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        }
    }
}
