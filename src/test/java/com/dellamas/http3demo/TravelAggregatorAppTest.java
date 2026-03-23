package com.dellamas.http3demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpClient.Version;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import javax.net.ssl.SSLSession;
import org.junit.jupiter.api.Test;

class TravelAggregatorAppTest {

    @Test
    void shouldAggregateSupplierResponses() throws InterruptedException, ExecutionException {
        var client = new FakeHttpClient(Map.of(
                URI.create("https://supplier-a.test/search"), new FakeResponse(200, "A"),
                URI.create("https://supplier-b.test/search"), new FakeResponse(200, "B")));

        var app = new TravelAggregatorApp(client);
        var result = app.fetchQuotes(List.of(
                URI.create("https://supplier-a.test/search"),
                URI.create("https://supplier-b.test/search")), "{}");

        assertEquals(List.of("A", "B"), result);
    }

    @Test
    void shouldFailWhenSupplierReturnsErrorStatus() {
        var client = new FakeHttpClient(Map.of(
                URI.create("https://supplier-a.test/search"), new FakeResponse(503, "down")));

        var app = new TravelAggregatorApp(client);

        assertThrows(IllegalStateException.class,
                () -> app.fetchQuotes(List.of(URI.create("https://supplier-a.test/search")), "{}"));
    }

    static class FakeHttpClient extends HttpClient {
        private final Map<URI, FakeResponse> responses;

        FakeHttpClient(Map<URI, FakeResponse> responses) {
            this.responses = responses;
        }

        @Override
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler)
                throws IOException {
            FakeResponse response = responses.get(request.uri());
            if (response == null) {
                throw new IOException("No stub for URI: " + request.uri());
            }
            @SuppressWarnings("unchecked")
            HttpResponse<T> cast = (HttpResponse<T>) response;
            return cast;
        }

        @Override
        public <T> java.util.concurrent.CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request,
                HttpResponse.BodyHandler<T> responseBodyHandler) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> java.util.concurrent.CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request,
                HttpResponse.BodyHandler<T> responseBodyHandler,
                HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
            throw new UnsupportedOperationException();
        }

        @Override public Optional<java.net.CookieHandler> cookieHandler() { return Optional.empty(); }
        @Override public Optional<Duration> connectTimeout() { return Optional.empty(); }
        @Override public Redirect followRedirects() { return Redirect.NEVER; }
        @Override public Optional<java.net.ProxySelector> proxy() { return Optional.empty(); }
        @Override public javax.net.ssl.SSLContext sslContext() { throw new UnsupportedOperationException(); }
        @Override public javax.net.ssl.SSLParameters sslParameters() { throw new UnsupportedOperationException(); }
        @Override public Optional<java.net.Authenticator> authenticator() { return Optional.empty(); }
        @Override public Version version() { return Version.HTTP_2; }
        @Override public Optional<java.util.concurrent.Executor> executor() { return Optional.empty(); }
    }

    record FakeResponse(int statusCode, String body) implements HttpResponse<String> {
        @Override public int statusCode() { return statusCode; }
        @Override public HttpRequest request() { return null; }
        @Override public Optional<HttpResponse<String>> previousResponse() { return Optional.empty(); }
        @Override public HttpHeaders headers() { return HttpHeaders.of(Map.of(), (a, b) -> true); }
        @Override public String body() { return body; }
        @Override public Optional<SSLSession> sslSession() { return Optional.empty(); }
        @Override public URI uri() { return URI.create("https://stub.local"); }
        @Override public Version version() { return Version.HTTP_2; }
    }
}
