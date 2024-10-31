package com.example;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.HttpClient;
import jakarta.inject.Singleton;
import reactor.core.publisher.Mono;

@Singleton
public class SpoonacularService {
    private final HttpClient client;

    public SpoonacularService(HttpClient client) {
        this.client = client;
    }

    public Mono<String> realizarPeticionPersonalizada(String url) {
        return Mono.from(client.retrieve(HttpRequest.GET(url), String.class));
    }
}