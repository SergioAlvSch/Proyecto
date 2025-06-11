package com.example;

import com.rometools.rome.feed.synd.SyndEntry;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.json.JsonSyntaxException;
import io.micronaut.json.tree.JsonNode;
import io.micronaut.serde.ObjectMapper;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Singleton
public class LMStudioService {
    private static final Logger log = LoggerFactory.getLogger(LMStudioService.class);
    private final ObjectMapper objectMapper;
    private final Runtime runtime;
    private String modeloActual;

    @Inject
    @Client("http://localhost:11434")

    HttpClient httpClient;

    public LMStudioService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.runtime = Runtime.getRuntime();
        this.modeloActual = "gemma3";
    }
    public String getModeloActual() {
        return modeloActual;
    }
    public Flux<String> procesarTexto(String prompt) {
        log.info("Enviando prompt a Ollama: {}", prompt);

        Map<String, Object> requestBody = new HashMap<>();
        //requestBody.put("model", "deepseek-r1:14b");
        //requestBody.put("model", "llama3.3:70b-instruct-q4_K_M");
        requestBody.put("model", "gemma3:27b-it-q8_0");
        requestBody.put("prompt", prompt);

        HttpRequest<?> request = HttpRequest.POST("/api/generate", requestBody);

        return Flux.from(httpClient.retrieve(request, String.class))
                .timeout(Duration.ofMinutes(30))
                .doOnNext(rawResponse -> log.info("Respuesta cruda recibida de Ollama: {}", rawResponse))
                .map(this::extractResponseContent)
                .map(this::limpiarRespuesta)
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
                        String response = responseNode.getStringValue();
                        fullResponse.append(response);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error al extraer el contenido de la respuesta: {}", jsonResponse, e);
        }
        return fullResponse.toString();
    }

    private String limpiarRespuesta(String respuesta) {
        // Eliminar el contenido entre <think> y </think>
        respuesta = respuesta.replaceAll("(?s)<think>.*?</think>", "");
        // Eliminar cualquier etiqueta HTML restante
        respuesta = respuesta.replaceAll("<[^>]*>", "");
        // Reemplazar múltiples espacios en blanco por uno solo, pero mantener los saltos de línea
        respuesta = respuesta.replaceAll("(?m)^\\s+", "").replaceAll("\\s+$", "").replaceAll("\\s{2,}", " ");
        return respuesta.trim();
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
                    Pattern pattern = Pattern.compile("\\b[12]\\b");
                    Matcher matcher = pattern.matcher(respuesta);
                    return matcher.find() ? matcher.group() : "0";
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

    public Flux<String> procesarNoticiaIndividual(SyndEntry entry) {
        String prompt = "Resume la siguiente noticia en inglés en un minimo de 3 y un maximo de 10 frases concisas eliminando los sesgos que puedan existir, sin repetir el título ni añadir enlaces ni imágenes:\n" +
                entry.getDescription().getValue();
        return procesarTexto(prompt)
                .map(String::trim);
    }

    public static String extraerImagen(SyndEntry entry) {
        if (entry.getEnclosures() != null && !entry.getEnclosures().isEmpty()) {
            return entry.getEnclosures().get(0).getUrl();
        }

        if (entry.getDescription() != null) {
            String desc = entry.getDescription().getValue();
            Pattern p = Pattern.compile("<img[^>]+src=[\"']([^\"']+)[\"']");
            Matcher m = p.matcher(desc);
            if (m.find()) return m.group(1);
        }

        if (entry.getForeignMarkup() != null) {
            for (org.jdom2.Element el : entry.getForeignMarkup()) {
                String localName = el.getName().toLowerCase();
                if ((localName.equals("content") || localName.equals("thumbnail")) && el.getAttribute("url") != null) {
                    return el.getAttribute("url").getValue();
                }
            }
        }

        return "/images/Logo.png";
    }

    public Flux<String> traducirNoticias(String resumenEnIngles) {
        log.info("Iniciando traducción de noticias");
        String prompt = "Translate the following news summary from English to Spanish. Don't add additional information or change the tone. It literally translates:\n\n" + resumenEnIngles;

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
                    // 1. Elimina comentarios /* ... */
                    String cleanedResponse = respuesta.replaceAll("/\\*.*?\\*/", "");
                    // 2. Elimina lo que no sea el JSON principal: busca el primer { y el último }
                    int primerLlave = cleanedResponse.indexOf('{');
                    int ultimaLlave = cleanedResponse.lastIndexOf('}');
                    if (primerLlave >= 0 && ultimaLlave > primerLlave) {
                        cleanedResponse = cleanedResponse.substring(primerLlave, ultimaLlave + 1).trim();
                    }
                    // 3. Elimina espacios, saltos de línea y otros caracteres extraños al inicio y final
                    cleanedResponse = cleanedResponse.replaceAll("^[\\s\\u00A0\\u2000-\\u200F\\u2028-\\u202F]+|[\\s\\u00A0\\u2000-\\u200F\\u2028-\\u202F]+$", "");
                    log.info("Respuesta limpiada: {}", cleanedResponse);
                    try {
                        // Intenta parsear la respuesta como JSON
                        Map<String, String> info = objectMapper.readValue(cleanedResponse,
                                Argument.mapOf(Argument.STRING, Argument.STRING));

                        validarYCompletarInformacionViaje(info, fechaActual);

                        return info;
                    } catch (JsonSyntaxException e) {
                        log.error("Error de sintaxis JSON al procesar la respuesta: {}", cleanedResponse, e);
                        Map<String, String> errorInfo = new HashMap<>();
                        errorInfo.put("destino", "Error: No se pudo obtener la información del viaje");
                        errorInfo.put("diasHastaViaje", "0");
                        errorInfo.put("duracionViaje", "0");
                        return errorInfo;
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