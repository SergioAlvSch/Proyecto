package com.example;

import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.serde.ObjectMapper;
import jakarta.inject.Singleton;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.uri.UriBuilder;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@Singleton
public class SpoonacularService {
    private final String API_KEY = "6b914274211f42b281b0242d60afac98";
    private final String URL = "https://api.spoonacular.com/recipes/findByIngredients";
    private final HttpClient client;
    private final ObjectMapper objectMapper;

    public SpoonacularService(HttpClient client, ObjectMapper objectMapper){
        this.client = client;
        this.objectMapper = objectMapper;
    }

    public Mono<List<Map<String, Object>>> busquedaRecetas(String ingredientes, int numero) {
        String url = UriBuilder.of(URL)
                .queryParam("apiKey", API_KEY)
                .queryParam("ingredients", ingredientes)
                .queryParam("number", numero)
                .build().toString();

        return Mono.from(client.retrieve(HttpRequest.GET(url), String.class))
                .flatMap(response -> {
                    try {
                        List<Map<String, Object>> recetas = objectMapper.readValue(
                                response,
                                Argument.listOf(Argument.mapOf(String.class, Object.class))
                        );
                        return Mono.just(recetas);
                    } catch (Exception e) {
                        return Mono.error(new RuntimeException("Error al procesar la respuesta: " + e.getMessage()));
                    }
                });
    }
}