package com.example;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.json.tree.JsonNode;
import io.micronaut.serde.ObjectMapper;
import jakarta.inject.Singleton;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@Singleton
public class LMStudioService {
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
                        String content = contentNode.isString() ? contentNode.getValue().toString() : "";

                        // Extraer la URL de Spoonacular de la respuesta
                        String[] lines = content.split("\n");
                        for (String line : lines) {
                            if (line.startsWith("Spoonacular URL:")) {
                                String url = line.substring("Spoonacular URL:".length()).trim();
                                if (url.startsWith("https://")) {
                                    return url;
                                }
                            }
                        }
                        throw new RuntimeException("No se encontró una URL válida de Spoonacular en la respuesta");
                    } catch (Exception e) {
                        throw new RuntimeException("Error al procesar la respuesta de LM Studio", e);
                    }
                });
    }

    public Mono<String> generarURLSpoonacular(String peticion){
        String prompt ="Genera la URL completa para una petición a Spoonacular API basada en esta solicitud: '" +
                peticion + "'. La URL base es 'https://api.spoonacular.com/recipes' y la API key es " +
                "'6b914274211f42b281b0242d60afac98'." + " Asegúrate de incluir los parámetros necesarios y traducir los " +
                "ingredientes al inglés si es necesario.";
        return procesarTexto(prompt);
    }

}
