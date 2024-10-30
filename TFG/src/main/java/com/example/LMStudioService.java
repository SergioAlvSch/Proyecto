package com.example;

import com.rometools.rome.feed.synd.SyndEntry;
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
import java.util.Arrays;
import java.util.stream.Collectors;

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
        return procesarTexto(prompt).map(String::trim);
    }

    public Mono<String> identificarTipoConsulta(String consultaTraducida) {
        String prompt = "Determina si la siguiente consulta se trata de buscar múltiples recetas por ingredientes, obtener detalles de una receta específica, o cómo preparar una receta específica: '" + consultaTraducida + "'. Responde SOLO con 'ingredientes', 'detalles_receta', o 'preparacion_receta'.";
        return procesarTexto(prompt).map(String::trim);
    }

    public Mono<List<String>> extraerIngredientes(String consultaTraducida) {
        String prompt = "Extrae los ingredientes mencionados en la siguiente consulta: '" + consultaTraducida + "'. Responde con una lista de ingredientes separados por comas.";
        return procesarTexto(prompt)
                .map(respuesta -> Arrays.asList(respuesta.split(",")))
                .map(ingredientes -> ingredientes.stream().map(String::trim).collect(Collectors.toList()));
    }

    public Mono<String> extraerNombreReceta(String consultaTraducida) {
        String prompt = "Extrae el nombre de la receta mencionada en la siguiente consulta: '" + consultaTraducida + "'. Responde SOLO con el nombre de la receta.";
        return procesarTexto(prompt).map(String::trim);
    }

    public Mono<String> generarRespuestaRecetas(String tipo, String respuestaSpoonacular) {
        String prompt;
        if ("ingredientes".equals(tipo)) {
            prompt = "Genera una respuesta amigable en inglés para esta lista de recetas basadas en ingredientes: " + respuestaSpoonacular;
        } else if ("detalles_receta".equals(tipo)) {
            prompt = "Genera una respuesta amigable en inglés con los detalles de esta receta específica: " + respuestaSpoonacular;
        } else {
            prompt = "Genera una respuesta amigable en inglés explicando cómo preparar esta receta: " + respuestaSpoonacular;
        }
        return procesarTexto(prompt);
    }

    public Mono<String> traducirRespuesta(String respuestaEnIngles) {
        String prompt = "Traduce la siguiente respuesta al español, manteniendo un tono amigable y conversacional: '" + respuestaEnIngles + "'";
        return procesarTexto(prompt);
    }

    public Mono<String> procesarNoticias(List<SyndEntry> noticias) {
        String prompt = "Summarize the following 5 most recent news in English, leaving a line break between news:\n\n" +
                noticias.stream().limit(5).map(entry -> entry.getTitle() + ": " + entry.getDescription().getValue())
                        .collect(Collectors.joining("\n\n"));
        return procesarTexto(prompt);
    }
}