package com.example;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import jakarta.inject.Singleton;
import reactor.core.publisher.Mono;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

@Singleton
public class SpoonacularService {
    private static final Logger log = LoggerFactory.getLogger(SpoonacularService.class);
    private final HttpClient client;
    private static final int MAX_RETRIES = 3;
    private static final Duration RETRY_DELAY = Duration.ofSeconds(5);

    public SpoonacularService(HttpClient client) {
        this.client = client;
    }

    public Mono<String> realizarPeticionPersonalizada(String url) {
        return Mono.defer(() -> attemptRequest(url, 0));
    }

    private Mono<String> attemptRequest(String url, int attempt) {
        return Mono.from(client.retrieve(HttpRequest.GET(url), String.class))
                .onErrorResume(throwable -> {
                    if (throwable instanceof HttpClientResponseException) {
                        HttpClientResponseException responseException = (HttpClientResponseException) throwable;
                        int statusCode = responseException.getStatus().getCode();
                        if (statusCode >= 500 && attempt < MAX_RETRIES - 1) {
                            log.warn("Intento {} fallido para URL: {}. Reintentando en {} segundos...",
                                    attempt + 1, url, RETRY_DELAY.getSeconds());
                            return Mono.delay(RETRY_DELAY)
                                    .flatMap(__ -> attemptRequest(url, attempt + 1));
                        }
                    }
                    return Mono.error(throwable);
                });
    }
}