package com.example;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.json.tree.JsonNode;
import io.micronaut.serde.ObjectMapper;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import reactor.core.publisher.Flux;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.util.retry.Retry;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Singleton
public class SpoonacularService {
    private static final Logger log = LoggerFactory.getLogger(SpoonacularService.class);
    private final HttpClient client;
    private static final int MAX_RETRIES = 3;
    private static final Duration RETRY_DELAY = Duration.ofSeconds(5);
    @Inject
    ObjectMapper objectMapper;

    public SpoonacularService(HttpClient client) {
        this.client = client;
    }

    public Flux<Map<String, Object>> realizarPeticionPersonalizada(String url) {
        return Flux.defer(() -> attemptRequest(url, 0))
                .map(this::parseSpoonacularResponse)
                .retryWhen(Retry.backoff(MAX_RETRIES, RETRY_DELAY)
                        .filter(throwable -> throwable instanceof HttpClientResponseException &&
                                ((HttpClientResponseException) throwable).getStatus().getCode() >= 500)
                )
                .onErrorResume(e -> {
                    log.error("Error al realizar la petición a Spoonacular después de {} intentos", MAX_RETRIES, e);
                    return Flux.just(Map.of("error", "Error al obtener datos de recetas. Por favor, intenta más tarde."));
                });
    }

    private Map<String, Object> parseSpoonacularResponse(String jsonResponse) {
        try {
            log.info("Respuesta cruda de Spoonacular: {}", jsonResponse);
            JsonNode root = objectMapper.readValue(jsonResponse, JsonNode.class);
            List<Map<String, String>> recetas = new ArrayList<>();

            JsonNode results = root.get("results");
            if (results != null && results.isArray() && results.size() > 0) {
                for (int i = 0; i < results.size(); i++) {
                    JsonNode recipe = results.get(i);
                    if (recipe != null && recipe.get("title") != null && recipe.get("title").isString()
                            && recipe.get("image") != null && recipe.get("image").isString()
                            && recipe.get("id") != null && recipe.get("id").isNumber()) {

                        Map<String, String> receta = new HashMap<>();
                        receta.put("titulo", recipe.get("title").getStringValue());
                        receta.put("imagen", recipe.get("image").getStringValue());
                        receta.put("enlace", "https://spoonacular.com/recipes/" + recipe.get("id").getNumberValue().toString());
                        recetas.add(receta);
                    }
                }
            } else {
                log.warn("No se encontró el campo 'results' o está vacío en la respuesta de Spoonacular.");
            }
            log.info("Recetas extraídas: {}", recetas);
            return Map.of(
                    "recetas", recetas,
                    "raw", jsonResponse
            );
        } catch (Exception e) {
            log.error("Error parsing Spoonacular response", e);
            return Map.of("recetas", new ArrayList<>(), "raw", jsonResponse, "error", "Error al procesar recetas");
        }
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