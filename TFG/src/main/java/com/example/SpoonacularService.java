package com.example;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import jakarta.inject.Singleton;
import reactor.core.publisher.Flux;
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

    public Flux<String> realizarPeticionPersonalizada(String url) {
        return Flux.defer(() -> attemptRequest(url, 0));
    }

    private Flux<String> attemptRequest(String url, int attempt) {
        return Flux.from(client.retrieve(HttpRequest.GET(url), String.class))
                .onErrorResume(throwable -> {
                    if (throwable instanceof HttpClientResponseException) {
                        HttpClientResponseException responseException = (HttpClientResponseException) throwable;
                        int statusCode = responseException.getStatus().getCode();
                        if (statusCode >= 500 && attempt < MAX_RETRIES - 1) {
                            log.warn("Intento {} fallido para URL: {}. Reintentando en {} segundos...",
                                    attempt + 1, url, RETRY_DELAY.getSeconds());
                            return Flux.just("") // Emite un elemento vacío
                                    .delayElements(RETRY_DELAY) // Retrasa este elemento
                                    .flatMap(__ -> attemptRequest(url, attempt + 1)); // Intenta de nuevo
                        }
                    }
                    return Flux.error(throwable);
                });
    }
}