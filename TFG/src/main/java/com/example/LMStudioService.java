package com.example;

import com.rometools.rome.feed.synd.SyndEntry;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.json.tree.JsonNode;
import io.micronaut.serde.ObjectMapper;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Arrays;
import java.util.concurrent.TimeoutException;
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
    public Flux<String> procesarTexto(String prompt) {
        log.info("Enviando prompt a LMStudio: {}", prompt);
        Map<String, Object> body = Map.of(
                "model", "local-model",
                "messages", List.of(Map.of("role", "user", "content", prompt))
        );

        return Flux.from(client.retrieve(
                        HttpRequest.POST(URL + "/chat/completions", body)
                                .header("Content-Type", "application/json"),
                        String.class
                ))
                .timeout(Duration.ofSeconds(1600))
                .onErrorResume(e -> {
                    if (e instanceof TimeoutException) {
                        log.error("Timeout al procesar texto en LM Studio. Prompt: {}", prompt);
                    }
                    return Flux.error(e);
                })
                .map(response -> {
                    try {
                        JsonNode jsonNode = objectMapper.readValue(response, JsonNode.class);
                        JsonNode contentNode = jsonNode.get("choices").get(0).get("message").get("content");
                        String result = contentNode.isString() ? contentNode.getValue().toString() : "";
                        log.info("Respuesta procesada de LMStudio: {}", result);
                        return result;
                    } catch (Exception e) {
                        log.error("Error al procesar la respuesta de LM Studio", e);
                        return "Error al procesar la respuesta";
                    }
                })
                .onErrorResume(e -> {
                    log.error("Error al comunicarse con LM Studio", e);
                    return Flux.just("Error de comunicación con el servicio");
                });
    }

    public Flux<String> traducirConsulta(String consulta) {
        String prompt = "Traduce la siguiente consulta al inglés, manteniendo el significado y la intención original: '" + consulta + "'. Responde SOLO con la traducción, sin texto adicional.";
        return procesarTexto(prompt).map(String::trim);
    }

    public Flux<String> identificarTipoConsulta(String consultaTraducida) {
        String prompt = "Analiza la siguiente consulta y determina si se trata de:\n" +
                "1. Buscar múltiples recetas por ingredientes disponibles\n" +
                "2. Obtener detalles y preparación de una receta específica\n" +
                "Consulta: '" + consultaTraducida + "'\n" +
                "Responde SOLO con el número correspondiente (1 o 2).";
        return procesarTexto(prompt).map(String::trim);
    }

    public Flux<List<String>> extraerIngredientes(String consultaTraducida) {
        String prompt = "Extrae los ingredientes mencionados en la siguiente consulta: '" + consultaTraducida + "'. Responde con una lista de ingredientes separados por comas.";
        return procesarTexto(prompt)
                .map(respuesta -> Arrays.asList(respuesta.split(",")))
                .map(ingredientes -> ingredientes.stream().map(String::trim).collect(Collectors.toList()));
    }

    public Flux<String> extraerNombreReceta(String consultaTraducida) {
        String prompt = "Extrae el nombre de la receta mencionada en la siguiente consulta: '" + consultaTraducida + "'. Responde SOLO con el nombre de la receta.";
        return procesarTexto(prompt).map(String::trim);
    }

    public Flux<String> generarRespuestaRecetas(String tipo, String respuestaSpoonacular) {
        String prompt;
        if ("1".equals(tipo)) {
            prompt = "Genera una respuesta amigable en inglés listando los nombres de las recetas que se pueden hacer con estos ingredientes: " + respuestaSpoonacular;
        } else {
            prompt = "Genera una respuesta amigable en inglés con los detalles y la preparación de esta receta específica: " + respuestaSpoonacular;
        }
        return procesarTexto(prompt);
    }

    public Flux<String> traducirRespuesta(String respuestaEnIngles) {
        String prompt = "Traduce la siguiente respuesta al español, manteniendo un tono amigable y conversacional: '" + respuestaEnIngles + "'";
        return procesarTexto(prompt);
    }

    public Flux<String> procesarNoticias(List<SyndEntry> noticias) {
        String prompt = "Summarize the following 5 most recent news in English, leaving a line break between news:\n\n" +
                noticias.stream().limit(5).map(entry -> entry.getTitle() + ": " + entry.getDescription().getValue())
                        .collect(Collectors.joining("\n\n"));
        return procesarTexto(prompt);
    }

    public Flux<Map<String, String>> extraerInformacionViaje(String consulta) {
        String prompt = "Extrae la siguiente información de la consulta: '" + consulta + "'. " +
                "1. Ciudad o país de destino. " +
                "2. Número de días hasta el viaje. " +
                "3. Duración del viaje en días. " +
                "Responde en formato JSON con los campos: destino, diasHastaViaje, duracionViaje.";
        return procesarTexto(prompt)
                .map(respuesta -> {
                    try {
                        return objectMapper.readValue(respuesta, Argument.mapOf(String.class, String.class));
                    } catch (Exception e) {
                        log.error("Error al procesar la respuesta JSON", e);
                        throw new RuntimeException("Error al procesar la respuesta JSON", e);
                    }
                });
    }

    public Flux<String> generarConsejoRopa(String informacionMeteorologica) {
        String prompt = "Basándote en esta información meteorológica: '" + informacionMeteorologica + "', " +
                "aconseja qué ropa llevar al viaje. Ten en cuenta la temperatura, precipitaciones y condiciones generales. " +
                "Da una respuesta amigable y detallada.";
        return procesarTexto(prompt);
    }
}