package com.example;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.json.tree.JsonNode;
import io.micronaut.serde.ObjectMapper;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;


@Singleton
public class LMStudioService {
    private static final Logger log = LoggerFactory.getLogger(LMStudioService.class);
    private final HttpClient client;
    private final String URL = "http://localhost:1234/v1";
    private final ObjectMapper objectMapper;

    public LMStudioService(HttpClient client, ObjectMapper objectMapper) {
        this.client = client;
        this.objectMapper = objectMapper;
    }

    public Mono<String> procesarTexto(String prompt) {
        Map<String, Object> body = Map.of(
                "model", "local-model",
                "messages", List.of(Map.of("role", "user", "content", prompt))
        );

        return Mono.from(client.retrieve(
                        HttpRequest.POST(URL + "/chat/completions", body)
                                .header("Content-Type", "application/json"),
                        String.class
                ))
                .map(response -> {
                    try {
                        JsonNode jsonNode = objectMapper.readValue(response, JsonNode.class);
                        JsonNode contentNode = jsonNode.get("choices").get(0).get("message").get("content");
                        return contentNode.isString() ? contentNode.getValue().toString() : "";
                    } catch (Exception e) {
                        throw new RuntimeException("Error al procesar la respuesta de LM Studio", e);
                    }
                });
    }

    public Mono<String> traducirConsulta(String consulta) {
        String prompt = "Traduce la siguiente consulta al inglés, manteniendo el significado y la intención original: '" + consulta + "'. Responde SOLO con la traducción, sin texto adicional.";

        return procesarTexto(prompt)
                .map(String::trim)
                .onErrorResume(e -> {
                    log.error("Error al traducir la consulta: ", e);
                    return Mono.error(new RuntimeException("No se pudo traducir la consulta"));
                });
    }

    public Mono<String> generarURLSpoonacular(String consultaTraducida) {
        String prompt = "Genera una URL para la API de Spoonacular basada en esta consulta en inglés: '" + consultaTraducida + "'. Sigue estas instrucciones:\n" +
                "1. URL base: https://api.spoonacular.com/\n" +
                "2. Si la consulta es sobre buscar recetas por ingredientes, usa 'recipes/complexSearch' y el parámetro 'includeIngredients'.\n" +
                "3. Si la consulta es sobre obtener información de una receta específica, usa 'recipes/{id}/information'.\n" +
                "4. Para otras consultas, elige el endpoint más apropiado de la documentación de Spoonacular.\n" +
                "5. Siempre incluye: apiKey=6b914274211f42b281b0242d60afac98\n" +
                "6. Codifica los parámetros correctamente para URL.\n" +
                "Responde SOLO con la URL completa generada.";

        return procesarTexto(prompt)
                .map(String::trim)
                .filter(url -> url.startsWith("https://api.spoonacular.com/"))
                .switchIfEmpty(Mono.error(new IllegalArgumentException("URL generada inválida")))
                .onErrorResume(e -> {
                    log.error("Error al generar la URL: ", e);
                    return Mono.error(new RuntimeException("No se pudo generar la URL de Spoonacular"));
                });
    }
}