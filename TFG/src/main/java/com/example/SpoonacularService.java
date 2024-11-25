package com.example;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import jakarta.inject.Singleton;
import reactor.core.publisher.Flux;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.util.retry.Retry;

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
        return Flux.defer(() -> attemptRequest(url, 0))
                .retryWhen(Retry.backoff(MAX_RETRIES, RETRY_DELAY)
                        .filter(throwable -> throwable instanceof HttpClientResponseException &&
                                ((HttpClientResponseException) throwable).getStatus().getCode() >= 500)
                )
                .onErrorResume(e -> {
                    log.error("Error al realizar la petición a Spoonacular después de {} intentos", MAX_RETRIES, e);
                    return Flux.just("Error al obtener datos de recetas. Por favor, intenta más tarde.");
                });
    }

    private Flux<String> attemptRequest(String url, int attempt) {
        return Flux.from(client.retrieve(HttpRequest.GET(url), String.class))
                .doOnNext(response -> log.info("Respuesta recibida de Spoonacular: {}", response))
                .onErrorResume(throwable -> {
                    if (throwable instanceof HttpClientResponseException) {
                        HttpClientResponseException responseException = (HttpClientResponseException) throwable;
                        int statusCode = responseException.getStatus().getCode();
                        log.warn("Intento {} fallido para URL: {}. Código de estado: {}",
                                attempt + 1, url, statusCode);
                    }
                    return Flux.error(throwable);
                });
    }
}