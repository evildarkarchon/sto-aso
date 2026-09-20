/*
 * Copyright (C) 2026 Dave Kor
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.kor.admiralty.ui.artwork;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import java.io.IOException;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Flow;

/** Delivers scripted responses through real body handlers without opening a network connection. */
final class ScriptedArtworkHttpClient extends HttpClient {
    private final Queue<Reply> replies = new ArrayDeque<>();
    final List<HttpRequest> requests = new ArrayList<>();
    int bytesDelivered;
    int cancelledBodies;

    /** Queues a response whose bytes will be delivered in small chunks. */
    synchronized void enqueue(int status, Map<String, List<String>> headers, byte[] bytes) {
        replies.add(new Reply(status, HttpHeaders.of(headers, (name, value) -> true), bytes.clone(), null));
    }

    /** Queues a transport failure for the next request. */
    synchronized void fail(Throwable failure) {
        replies.add(new Reply(0, null, null, failure));
    }

    /** Runs the supplied body handler synchronously, respecting subscriber cancellation. */
    @Override
    public synchronized <T> CompletableFuture<HttpResponse<T>> sendAsync(
            HttpRequest request, HttpResponse.BodyHandler<T> handler) {
        requests.add(request);
        Reply reply = replies.poll();
        if (reply == null) {
            return CompletableFuture.failedFuture(new AssertionError("No scripted HTTP response for " + request.uri()));
        }
        if (reply.failure() != null) {
            return CompletableFuture.failedFuture(reply.failure());
        }
        try {
            HttpResponse.BodySubscriber<T> subscriber = handler.apply(new HttpResponse.ResponseInfo() {
                /** Returns the scripted status. */
                @Override public int statusCode() { return reply.status(); }
                /** Returns the scripted headers. */
                @Override public HttpHeaders headers() { return reply.headers(); }
                /** Matches the test client's configured protocol. */
                @Override public Version version() { return Version.HTTP_1_1; }
            });
            ScriptedSubscription subscription = new ScriptedSubscription();
            subscriber.onSubscribe(subscription);
            for (int offset = 0; offset < reply.bytes().length && !subscription.cancelled; offset += 7) {
                int length = Math.min(7, reply.bytes().length - offset);
                bytesDelivered += length;
                subscriber.onNext(List.of(ByteBuffer.wrap(reply.bytes(), offset, length)));
            }
            if (!subscription.cancelled) {
                subscriber.onComplete();
            }
            return subscriber.getBody().toCompletableFuture().thenApply(body ->
                    new ScriptedResponse<>(request, reply.status(), reply.headers(), body));
        } catch (Throwable failure) {
            return CompletableFuture.failedFuture(failure);
        }
    }

    /** Delegates to ordinary scripted delivery because this fixture never emits server pushes. */
    @Override
    public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request,
            HttpResponse.BodyHandler<T> handler, HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
        return sendAsync(request, handler);
    }

    /** Provides synchronous access to the same scripted responses and transport failures. */
    @Override
    public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler)
            throws IOException, InterruptedException {
        try {
            return sendAsync(request, handler).get();
        } catch (ExecutionException failure) {
            if (failure.getCause() instanceof IOException io) {
                throw io;
            }
            throw new IOException(failure.getCause());
        }
    }

    /** Uses no cookies. */
    @Override public Optional<CookieHandler> cookieHandler() { return Optional.empty(); }
    /** Has no connection timeout because no connection is opened. */
    @Override public Optional<Duration> connectTimeout() { return Optional.empty(); }
    /** Leaves redirects to the production transport under test. */
    @Override public Redirect followRedirects() { return Redirect.NEVER; }
    /** Uses no proxy. */
    @Override public Optional<ProxySelector> proxy() { return Optional.empty(); }
    /** Exposes the platform SSL context without performing TLS. */
    @Override public SSLContext sslContext() {
        try {
            return SSLContext.getDefault();
        } catch (java.security.NoSuchAlgorithmException failure) {
            throw new IllegalStateException(failure);
        }
    }
    /** Exposes neutral SSL parameters. */
    @Override public SSLParameters sslParameters() { return new SSLParameters(); }
    /** Uses no authentication. */
    @Override public Optional<Authenticator> authenticator() { return Optional.empty(); }
    /** Identifies scripted responses as HTTP/1.1. */
    @Override public Version version() { return Version.HTTP_1_1; }
    /** Performs deliveries on the requesting thread. */
    @Override public Optional<Executor> executor() { return Optional.empty(); }

    private record Reply(int status, HttpHeaders headers, byte[] bytes, Throwable failure) { }

    /** Tracks cancellation so an oversized response stops emitting data immediately. */
    private final class ScriptedSubscription implements Flow.Subscription {
        private boolean cancelled;

        /** Delivery is synchronous; fixture bodies do not need scheduling or demand accounting. */
        @Override public void request(long count) { }
        /** Stops subsequent chunks and completion after production code rejects a body. */
        @Override public void cancel() {
            if (!cancelled) {
                cancelledBodies++;
                cancelled = true;
            }
        }
    }

    private record ScriptedResponse<T>(HttpRequest request, int statusCode, HttpHeaders headers, T body)
            implements HttpResponse<T> {
        /** Scripted responses have no redirect history. */
        @Override public Optional<HttpResponse<T>> previousResponse() { return Optional.empty(); }
        /** No TLS session exists for a scripted response. */
        @Override public Optional<SSLSession> sslSession() { return Optional.empty(); }
        /** Returns the URI actually submitted by the production transport. */
        @Override public URI uri() { return request.uri(); }
        /** Matches the test client's configured protocol. */
        @Override public Version version() { return Version.HTTP_1_1; }
    }
}
