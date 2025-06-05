package com.example;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.*;
import io.micronaut.serde.ObjectMapper;
import io.micronaut.views.View;
import jakarta.inject.Inject;
import reactor.core.publisher.Flux;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

@Controller("/recetas")
public class RecetasController {
    private static final Logger log = LoggerFactory.getLogger(RecetasController.class);

    @Inject
    ObjectMapper objectMapper;

    @Inject
    private LMStudioService lmStudioService;

    @Inject
    private SpoonacularService spoonacularService;

    @Get("/")
    @View("recetas_template")
    public Map<String, Object> getRecetas() {
        return new HashMap<>();
    }

    @Post("/procesar")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.TEXT_EVENT_STREAM)
    public Flux<String> procesarPeticionReceta(@Body Map<String, String> peticion) {
        String texto = peticion.get("texto");
        log.info("Recibida solicitud de receta: {}", texto);
        return Flux.concat(
                emitirRespuesta("Procesando petición..."),
                lmStudioService.traducirConsulta(texto)
                        .flatMap(consultaTraducida ->
                                lmStudioService.identificarTipoConsulta(consultaTraducida)
                                        .flatMap(tipo -> {
                                            log.info("Tipo de consulta identificado: {}", tipo);
                                            return procesarConsultaSegunTipo(tipo, consultaTraducida);
                                        })
                        )
        ).onErrorResume(e -> {
            log.error("Error al procesar la petición de receta: ", e);
            return Flux.just(crearRespuestaError(texto, e));
        });
    }

    private Flux<String> procesarConsultaSegunTipo(String tipo, String consultaTraducida) {
        if ("1".equals(tipo)) {
            return procesarConsultaIngredientes(consultaTraducida);
        } else if ("2".equals(tipo)) {
            return procesarConsultaRecetaEspecifica(consultaTraducida);
        } else {
            return Flux.just(crearRespuestaError(consultaTraducida, new IllegalArgumentException("Tipo de consulta no reconocido")).toString());
        }
    }

    private Flux<String> procesarConsultaIngredientes(String consultaTraducida) {
        return lmStudioService.extraerIngredientes(consultaTraducida)
                .flatMap(ingredientes -> {
                    String ingredientesStr = String.join(",", ingredientes);
                    return realizarPeticionSpoonacular("ingredientes", ingredientesStr);
                });
    }

    private Flux<String> procesarConsultaRecetaEspecifica(String consultaTraducida) {
        return lmStudioService.extraerNombreReceta(consultaTraducida)
                .flatMap(nombreReceta -> realizarPeticionSpoonacular("receta_especifica", nombreReceta));
    }

    private Flux<String> realizarPeticionSpoonacular(String tipo, String parametros) {
        String baseUrl = "https://api.spoonacular.com/recipes/complexSearch";
        String apiKey = "6b914274211f42b281b0242d60afac98"; // ¡Cambia por tu clave!
        String url = baseUrl + "?" + (tipo.equals("ingredientes") ? "includeIngredients=" : "query=")
                + URLEncoder.encode(parametros, StandardCharsets.UTF_8) + "&apiKey=" + apiKey;

        String tipoFinal = tipo.equals("ingredientes") ? "1" : "2";

        return spoonacularService.realizarPeticionPersonalizada(url)
                .flatMap(respuestaSpoonacular -> {
                    List<Map<String, String>> recetas = (List<Map<String, String>>) respuestaSpoonacular.get("recetas");
                    String rawData = (String) respuestaSpoonacular.get("raw");

                    return lmStudioService.generarRespuestaRecetas(tipoFinal, rawData)
                            .flatMap(respuestaEnIngles ->
                                    lmStudioService.traducirRespuesta(respuestaEnIngles)
                                            .map(traduccion -> crearRespuestaConImagenes(traduccion, recetas))
                            );
                });
    }
    private String crearRespuestaConImagenes(String texto, List<Map<String, String>> recetas) {
        try {
            Map<String, Object> respuesta = new HashMap<>();
            respuesta.put("respuesta", texto);
            respuesta.put("imagenes", recetas.stream()
                    .map(r -> Map.of(
                            "titulo", r.get("titulo"),
                            "imagen", r.get("imagen"),
                            "enlace", r.get("enlace")
                    ))
                    .collect(Collectors.toList()));
            log.info("Respuesta final con imágenes: {}", objectMapper.writeValueAsString(respuesta));
            return objectMapper.writeValueAsString(respuesta);
        } catch (JsonProcessingException e) {
            return "{\"error\": \"Error al formatear respuesta\"}";
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    private Flux<String> emitirRespuesta(String mensaje) {
        Map<String, String> respuesta = new HashMap<>();
        respuesta.put("respuesta", mensaje);
        try {
            return Flux.just(objectMapper.writeValueAsString(respuesta));
        } catch (JsonProcessingException e) {
            log.error("Error al serializar la respuesta", e);
            return Flux.error(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private String crearRespuestaError(String textoOriginal, Throwable e) {
        Map<String, String> error = new HashMap<>();
        error.put("peticion", textoOriginal);
        error.put("respuesta", "Error: " + e.getMessage());
        try {
            return objectMapper.writeValueAsString(error);
        } catch (JsonProcessingException ex) {
            log.error("Error al serializar el error", ex);
            return "{\"respuesta\": \"Error interno del servidor\"}";
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    @Post("/detalle")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.TEXT_EVENT_STREAM)
    public Flux<String> obtenerDetalleReceta(@Body Map<String, String> peticion) {
        String nombreReceta = peticion.get("texto");
        log.info("Buscando detalles de receta: {}", nombreReceta);
        return realizarPeticionSpoonacular("receta_especifica", nombreReceta);
    }
}