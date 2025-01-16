package com.example;

import com.fasterxml.jackson.core.type.TypeReference;
import com.rometools.rome.feed.synd.SyndEntry;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.json.tree.JsonNode;
import io.micronaut.serde.ObjectMapper;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Singleton
public class LMStudioService {
    private static final Logger log = LoggerFactory.getLogger(LMStudioService.class);
    private final ObjectMapper objectMapper;
    private final Runtime runtime;

    @Inject
    @Client("http://localhost:11434")
    HttpClient httpClient;

    public LMStudioService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.runtime = Runtime.getRuntime();
    }

    public Flux<String> procesarTexto(String prompt) {
        log.info("Enviando prompt a Ollama: {}", prompt);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", "llama2");
        requestBody.put("prompt", prompt);

        HttpRequest<?> request = HttpRequest.POST("/api/generate", requestBody);

        return Flux.from(httpClient.retrieve(request, String.class))
                .timeout(Duration.ofSeconds(600))
                .doOnNext(rawResponse -> log.info("Respuesta cruda recibida de Ollama: {}", rawResponse))
                .map(this::extractResponseContent)
                .filter(response -> !response.isEmpty())
                .doOnError(e -> log.error("Error en procesarTexto", e))
                .onErrorResume(e -> Flux.just("Error en procesamiento: " + e.getMessage()));
    }


    private String extractResponseContent(String jsonResponse) {
        StringBuilder fullResponse = new StringBuilder();
        try {
            String[] jsonParts = jsonResponse.split("\n");
            for (String part : jsonParts) {
                if (!part.trim().isEmpty()) {
                    JsonNode jsonNode = objectMapper.readValue(part, JsonNode.class);
                    JsonNode responseNode = jsonNode.get("response");
                    if (responseNode != null && responseNode.isString()) {
                        fullResponse.append(responseNode.getStringValue());
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error al extraer el contenido de la respuesta: {}", jsonResponse, e);
        }
        return fullResponse.toString();
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

    public Flux<String> procesarNoticias(List<SyndEntry> noticias) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy");
        String prompt = "Summarize these specific news articles in English, maintaining their original content and source. Include the date at the beginning and the link at the end of each summary:\n\n" +
                noticias.stream().limit(5).map(entry -> {
                    String date = dateFormat.format(entry.getPublishedDate());
                    String title = entry.getTitle();
                    String description = entry.getDescription().getValue();
                    String link = entry.getLink();
                    return String.format("[%s] %s: %s (%s)", date, title, description, link);
                }).collect(Collectors.joining("\n\n")) +
                "\n\nProvide a summary for each news item, keeping the date at the beginning and the link at the end in parentheses.";

        log.info("Enviando prompt a Ollama para procesar noticias");
        return procesarTexto(prompt)
                .timeout(Duration.ofSeconds(600))
                .map(this::formatearRespuestaNoticias)
                .doOnNext(response -> log.info("Respuesta formateada de Ollama para noticias: {}", response))
                .doOnError(e -> log.error("Error al procesar noticias con Ollama", e))
                .onErrorResume(e -> Flux.just("Error al procesar noticias: " + e.getMessage()));
    }

    private String formatearRespuestaNoticias(String respuesta) {
        String[] noticias = respuesta.split("\n\n");
        StringBuilder resultado = new StringBuilder();
        for (String noticia : noticias) {
            String fechaFormateada = extraerFecha(noticia);
            String enlaceFormateado = extraerEnlace(noticia);
            String contenido = noticia.replaceAll("\\[.*?\\]", "").replaceAll("\\(http.*?\\)", "").trim();
            resultado.append(fechaFormateada).append("\n")
                    .append(contenido).append("\n")
                    .append(enlaceFormateado).append("\n\n");
        }
        return resultado.toString().trim();
    }

    private String extraerFecha(String noticia) {
        Pattern pattern = Pattern.compile("\\[(\\d{2}/\\d{2}/\\d{4})\\]");
        Matcher matcher = pattern.matcher(noticia);
        return matcher.find() ? matcher.group(1) : "";
    }

    private String extraerEnlace(String noticia) {
        Pattern pattern = Pattern.compile("\\((https.*?)\\)");
        Matcher matcher = pattern.matcher(noticia);
        return matcher.find() ? matcher.group(1) : "";
    }

    public Flux<String> traducirNoticias(String resumenEnIngles) {
        log.info("Iniciando traducción de noticias");
        String prompt = "Translate the following news summary from English to Spanish. Keep the original formatting, including dates, links, and paragraph structure. Don't add additional information or change the tone. It literally translates:\n\n" + resumenEnIngles;

        return procesarTexto(prompt)
                .timeout(Duration.ofSeconds(600))
                .doOnNext(traduccion -> log.info("Traducción de noticias completada: {}", traduccion))
                .doOnError(e -> log.error("Error al traducir noticias", e))
                .onErrorResume(e -> Flux.just("Error al traducir: " + e.getMessage()));
    }

    public Flux<String> traducirRespuesta(String respuestaEnIngles) {
        log.info("Iniciando traducción de respuesta");
        String prompt = "Traduce la siguiente respuesta al español, manteniendo un tono amigable y conversacional: '" + respuestaEnIngles + "'";
        return procesarTexto(prompt)
                .timeout(Duration.ofSeconds(600))
                .doOnNext(traduccion -> log.info("Traducción completada: {}", traduccion))
                .doOnError(e -> log.error("Error al traducir respuesta", e))
                .onErrorResume(e -> Flux.just("Error al traducir: " + e.getMessage()));
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