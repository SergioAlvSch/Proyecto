package com.example;

import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.*;
import io.micronaut.views.View;
import jakarta.inject.Inject;
import reactor.core.publisher.Mono;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.AbstractMap.SimpleEntry;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

@Controller("/recetas")
public class RecetasController {
    private static final Logger log = LoggerFactory.getLogger(RecetasController.class);

    @Inject
    private SpoonacularService spoonacularService;

    @Inject
    private LMStudioService lmStudioService;

    @Get("/")
    @View("recetas_template")
    public Map<String, Object> getRecetas() {
        return new HashMap<>();
    }

    @Post("/procesar")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Mono<Map<String, String>> procesarPeticionReceta(@Body Map<String, String> peticion) {
        String textoOriginal = peticion.get("texto");
        return lmStudioService.traducirConsulta(textoOriginal)
                .flatMap(consultaTraducida ->
                        lmStudioService.identificarTipoConsulta(consultaTraducida)
                                .flatMap(tipoRespuesta -> {
                                    String tipo = extraerTipoConsulta(tipoRespuesta);
                                    if (tipo == null) {
                                        log.warn("Tipo de consulta no reconocido: {}", tipoRespuesta);
                                        return Mono.error(new IllegalArgumentException("Tipo de consulta no reconocido"));
                                    }
                                    switch (tipo) {
                                        case "1":
                                            return lmStudioService.extraerIngredientes(consultaTraducida)
                                                    .map(ingredientes -> new SimpleEntry<>("ingredientes", String.join(",", ingredientes)));
                                        case "2":
                                            return lmStudioService.extraerNombreReceta(consultaTraducida)
                                                    .map(nombreReceta -> new SimpleEntry<>("receta_especifica", nombreReceta));
                                        default:
                                            return Mono.error(new IllegalArgumentException("Tipo de consulta no reconocido"));
                                    }
                                })
                )
                .flatMap(entry -> {
                    String tipo = entry.getKey();
                    String parametros = entry.getValue();
                    String baseUrl = "https://api.spoonacular.com/recipes/complexSearch";
                    String apiKey = "6b914274211f42b281b0242d60afac98";
                    String url;
                    if ("ingredientes".equals(tipo)) {
                        url = baseUrl + "?includeIngredients=" + URLEncoder.encode(parametros, StandardCharsets.UTF_8) + "&apiKey=" + apiKey;
                    } else {
                        url = baseUrl + "?query=" + URLEncoder.encode(parametros, StandardCharsets.UTF_8) + "&apiKey=" + apiKey;
                    }
                    return spoonacularService.realizarPeticionPersonalizada(url)
                            .map(respuesta -> new SimpleEntry<>(tipo, respuesta));
                })
                .flatMap(entry ->
                        lmStudioService.generarRespuestaRecetas(entry.getKey(), entry.getValue())
                                .flatMap(lmStudioService::traducirRespuesta)
                )
                .map(resumen -> {
                    Map<String, String> resultado = new HashMap<>();
                    resultado.put("peticion", textoOriginal);
                    resultado.put("respuesta", resumen);
                    return resultado;
                })
                .onErrorResume(e -> {
                    log.error("Error al procesar la petición: ", e);
                    Map<String, String> error = new HashMap<>();
                    error.put("peticion", textoOriginal);
                    error.put("respuesta", "Lo siento, hubo un error al procesar tu solicitud: " + e.getMessage());
                    return Mono.just(error);
                });
    }

    private String extraerTipoConsulta(String respuesta) {
        Pattern pattern = Pattern.compile("\\b[12]\\b");
        Matcher matcher = pattern.matcher(respuesta);
        if (matcher.find()) {
            return matcher.group();
        }
        return null;
    }
}