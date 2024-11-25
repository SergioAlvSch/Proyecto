package com.example;

import com.fasterxml.jackson.core.type.TypeReference;
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

import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
                .timeout(Duration.ofMinutes(30))
                .onErrorResume(e -> {
                    if (e instanceof TimeoutException) {
                        log.error("Timeout al procesar texto en LM Studio. Prompt: {}", prompt);
                        return Flux.just("Lo siento, la respuesta está tardando demasiado. Por favor, intenta de nuevo.");
                    }
                    log.error("Error al comunicarse con LM Studio", e);
                    return Flux.just("Error de comunicación con el servicio. Por favor, intenta más tarde.");
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
                });
    }

    public Flux<String> traducirConsulta(String consulta) {
        String prompt = "Traduce la siguiente consulta al inglés, manteniendo el significado y la intención original: '" + consulta + "'. Responde SOLO con la traducción, sin texto adicional.";
        return procesarTexto(prompt)
                .map(respuesta -> respuesta.replaceAll("(?i)Response:\\s*", "").trim());
    }

    public Flux<String> identificarTipoConsulta(String consultaTraducida) {
        String prompt = "Analiza la siguiente consulta y determina si se trata de:\n" +
                "1. Buscar múltiples recetas por ingredientes disponibles\n" +
                "2. Obtener detalles y preparación de una receta específica\n" +
                "Consulta: '" + consultaTraducida + "'\n" +
                "Responde SOLO con el número correspondiente (1 o 2).";
        return procesarTexto(prompt)
                .map(respuesta -> {
                    // Extraer solo el primer número (1 o 2) de la respuesta
                    Pattern pattern = Pattern.compile("\\b[12]\\b");
                    Matcher matcher = pattern.matcher(respuesta);
                    return matcher.find() ? matcher.group() : "0"; // Devuelve "0" si no se encuentra 1 o 2
                });
    }

    public Flux<List<String>> extraerIngredientes(String consultaTraducida) {
        String prompt = "Extrae los ingredientes mencionados en la siguiente consulta: '" + consultaTraducida + "'. Responde con una lista de ingredientes separados por comas.";
        return procesarTexto(prompt)
                .map(respuesta -> {
                    String[] partes = respuesta.split(":");
                    String ingredientes = partes.length > 1 ? partes[partes.length - 1] : respuesta;
                    return Arrays.asList(ingredientes.split(","))
                            .stream()
                            .map(String::trim)
                            .collect(Collectors.toList());
                });
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
        log.info("Iniciando traducción de respuesta");
        String prompt = "Traduce la siguiente respuesta al español, manteniendo un tono amigable y conversacional. Mantén los saltos de línea originales. Responde SOLO con la traducción en español, sin incluir el texto original en inglés ni ninguna explicación adicional: '" + respuestaEnIngles + "'";
        return procesarTexto(prompt)
                .collectList()
                .map(lista -> String.join("", lista))
                .map(respuesta -> {
                    int indexOfEnglish = respuesta.toLowerCase().indexOf("(friendly and conversational");
                    if (indexOfEnglish != -1) {
                        respuesta = respuesta.substring(0, indexOfEnglish).trim();
                    }
                    log.info("Traducción completada: {}", respuesta);
                    return respuesta;
                })
                .flux();
    }

    public Flux<String> procesarNoticias(List<SyndEntry> noticias) {
        String prompt = "Summarize the following 5 most recent news in English, leaving a line break between news:\n\n" +
                noticias.stream().limit(5).map(entry -> entry.getTitle() + ": " + entry.getDescription().getValue())
                        .collect(Collectors.joining("\n\n"));
        return procesarTexto(prompt);
    }

    public Flux<Map<String, String>> extraerInformacionViaje(String consulta) {
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy");
        Date fechaActual = new Date();
        String fechaActualStr = sdf.format(fechaActual);

        String prompt = "La fecha actual es " + fechaActualStr + ". " +
                "Extrae la siguiente información de la consulta: '" + consulta + "'. " +
                "1. Ciudad o país de destino. " +
                "2. Número de días hasta el inicio del viaje (contando desde hoy). " +
                "3. Duración del viaje en días. " +
                "Responde SOLO en formato JSON con los campos: destino, diasHastaViaje, duracionViaje.";

        return procesarTexto(prompt)
                .map(respuesta -> {
                    try {
                        Map<String, String> info = objectMapper.readValue(respuesta,
                                Argument.mapOf(Argument.STRING, Argument.STRING));

                        validarYCompletarInformacionViaje(info, fechaActual);

                        return info;
                    } catch (Exception e) {
                        log.error("Error al procesar la respuesta JSON", e);
                        throw new RuntimeException("Error al procesar la respuesta JSON", e);
                    }
                });
    }

    private void validarYCompletarInformacionViaje(Map<String, String> info, Date fechaActual) {
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy");
        Calendar cal = Calendar.getInstance();
        cal.setTime(fechaActual);

        int diasHastaViaje = Integer.parseInt(info.getOrDefault("diasHastaViaje", "3"));
        int duracionViaje = Integer.parseInt(info.getOrDefault("duracionViaje", "7"));

        cal.add(Calendar.DAY_OF_MONTH, diasHastaViaje);
        String fechaInicio = sdf.format(cal.getTime());
        info.put("fechaInicio", fechaInicio);

        cal.add(Calendar.DAY_OF_MONTH, duracionViaje);
        String fechaFin = sdf.format(cal.getTime());
        info.put("fechaFin", fechaFin);

        if (!info.containsKey("destino") || info.get("destino").isEmpty()) {
            info.put("destino", "Paris");
        }

        info.put("diasHastaViaje", String.valueOf(diasHastaViaje));
        info.put("duracionViaje", String.valueOf(duracionViaje));
    }

    public Flux<String> generarConsejoRopa(String informacionMeteorologica, int diasPronosticados, int diasTotalesViaje, String destino) {
        String prompt = "Basándote en esta información meteorológica para los primeros " + diasPronosticados +
                " días de un viaje de " + diasTotalesViaje + " días a " + destino + ": '" + informacionMeteorologica + "', " +
                "aconseja qué ropa llevar al viaje. Ten en cuenta que solo tenemos información para los primeros días, " +
                "pero el viaje dura más tiempo. Da una respuesta amigable y detallada en inglés, considerando las " +
                "diferentes condiciones que pueden presentarse y sugiriendo opciones versátiles para el resto del viaje.";
        return procesarTexto(prompt);
    }
}