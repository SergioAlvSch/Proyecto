package com.example;

import com.fasterxml.jackson.core.type.TypeReference;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.json.tree.JsonNode;
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
    private final String BASE_URL = "https://api.spoonacular.com/recipes";
    private final HttpClient client;
    private final ObjectMapper objectMapper;

    public SpoonacularService(HttpClient client, ObjectMapper objectMapper) {
        this.client = client;
        this.objectMapper = objectMapper;
    }

    public Mono<List<Map<String, Object>>> busquedaRecetas(String ingredientes, int numero) {
        String url = UriBuilder.of(BASE_URL + "/complexSearch")
                .queryParam("apiKey", API_KEY)
                .queryParam("includeIngredients", ingredientes)
                .queryParam("ignorePantry", true)
                .queryParam("number", numero)
                .build().toString();

        return Mono.from(client.retrieve(HttpRequest.GET(url), String.class))
                .flatMap(response -> {
                    try {
                        Map<String, Object> map = objectMapper.readValue(response, Argument.of(Map.class, String.class, Object.class));
                        List<Map<String, Object>> recetas = (List<Map<String, Object>>) map.get("results");
                        return Mono.just(recetas);
                    } catch (Exception e) {
                        e.printStackTrace();
                        return Mono.error(new RuntimeException("Error al procesar la respuesta: " + e.getMessage() + ". Respuesta: " + response, e));
                    }
                });
    }
    public Mono<DetallesReceta> getDetallesReceta(Integer id) {
        String url = UriBuilder.of(BASE_URL + "/" + id + "/information")
                .queryParam("apiKey", API_KEY)
                .build().toString();

        return Mono.from(client.retrieve(HttpRequest.GET(url), String.class))
                .flatMap(response -> {
                    try {
                        DetallesReceta detallesReceta = objectMapper.readValue(response, DetallesReceta.class);
                        return Mono.just(detallesReceta);
                    } catch (Exception e) {
                        e.printStackTrace(); // Imprime la traza de la excepción para depuración
                        return Mono.error(new RuntimeException("Error al procesar los detalles de la receta: " + e.getMessage()));
                    }
                });
    }
}