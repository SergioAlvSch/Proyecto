package com.example;

import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.*;
import io.micronaut.views.View;
import jakarta.inject.Inject;
import reactor.core.publisher.Flux;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
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
    public Flux<Map<String, String>> procesarPeticionReceta(@Body Map<String, String> peticion) {
        String textoOriginal = peticion.get("texto");
        return Flux.concat(
                emitirRespuesta("Traduciendo consulta..."),
                lmStudioService.traducirConsulta(textoOriginal)
                        .concatMap(consultaTraducida ->
                                Flux.concat(
                                        emitirRespuesta("Consulta traducida: " + consultaTraducida),
                                        emitirRespuesta("Identificando tipo de consulta..."),
                                        lmStudioService.identificarTipoConsulta(consultaTraducida)
                                                .concatMap(tipoRespuesta -> {
                                                    String tipo = extraerTipoConsulta(tipoRespuesta);
                                                    return Flux.concat(
                                                            emitirRespuesta("Tipo de consulta identificado: " + tipo),
                                                            procesarConsultaSegunTipo(tipo, consultaTraducida)
                                                    );
                                                })
                                )
                        )
        ).onErrorResume(e -> {
            log.error("Error al procesar la petición: ", e);
            return Flux.just(crearRespuestaError(textoOriginal, e));
        });
    }

    private Flux<Map<String, String>> procesarConsultaSegunTipo(String tipo, String consultaTraducida) {
        if ("1".equals(tipo)) {
            return procesarConsultaIngredientes(consultaTraducida);
        } else if ("2".equals(tipo)) {
            return procesarConsultaRecetaEspecifica(consultaTraducida);
        } else {
            return Flux.just(crearRespuestaError(consultaTraducida, new IllegalArgumentException("Tipo de consulta no reconocido")));
        }
    }

    private Flux<Map<String, String>> procesarConsultaIngredientes(String consultaTraducida) {
        return Flux.concat(
                emitirRespuesta("Extrayendo ingredientes..."),
                lmStudioService.extraerIngredientes(consultaTraducida)
                        .concatMap(ingredientes -> {
                            String ingredientesStr = String.join(",", ingredientes);
                            return Flux.concat(
                                    emitirRespuesta("Ingredientes extraídos: " + ingredientesStr),
                                    emitirRespuesta("Buscando recetas en Spoonacular..."),
                                    realizarPeticionSpoonacular("ingredientes", ingredientesStr)
                            );
                        })
        );
    }

    private Flux<Map<String, String>> procesarConsultaRecetaEspecifica(String consultaTraducida) {
        return Flux.concat(
                emitirRespuesta("Extrayendo nombre de la receta..."),
                lmStudioService.extraerNombreReceta(consultaTraducida)
                        .concatMap(nombreReceta ->
                                Flux.concat(
                                        emitirRespuesta("Nombre de la receta extraído: " + nombreReceta),
                                        emitirRespuesta("Buscando receta en Spoonacular..."),
                                        realizarPeticionSpoonacular("receta_especifica", nombreReceta)
                                )
                        )
        );
    }

    private Flux<Map<String, String>> realizarPeticionSpoonacular(String tipo, String parametros) {
        String baseUrl = "https://api.spoonacular.com/recipes/complexSearch";
        String apiKey = "6b914274211f42b281b0242d60afac98";
        String url = baseUrl + "?" + (tipo.equals("ingredientes") ? "includeIngredients=" : "query=")
                + URLEncoder.encode(parametros, StandardCharsets.UTF_8) + "&apiKey=" + apiKey;

        return spoonacularService.realizarPeticionPersonalizada(url)
                .concatMap(respuestaSpoonacular ->
                        Flux.concat(
                                emitirRespuesta("Respuesta de Spoonacular recibida"),
                                emitirRespuesta("Generando respuesta final..."),
                                lmStudioService.generarRespuestaRecetas(tipo, respuestaSpoonacular)
                                        .concatMap(respuestaEnIngles ->
                                                Flux.concat(
                                                        emitirRespuesta("Respuesta generada en inglés"),
                                                        emitirRespuesta("Traduciendo respuesta al español..."),
                                                        lmStudioService.traducirRespuesta(respuestaEnIngles)
                                                                .map(respuestaFinal -> {
                                                                    Map<String, String> resultado = new HashMap<>();
                                                                    resultado.put("respuesta", respuestaFinal);
                                                                    return resultado;
                                                                })
                                                )
                                        )
                        )
                );
    }

    private Flux<Map<String, String>> emitirRespuesta(String mensaje) {
        Map<String, String> respuesta = new HashMap<>();
        respuesta.put("respuesta", mensaje);
        return Flux.just(respuesta);
    }

    private Map<String, String> crearRespuestaError(String textoOriginal, Throwable e) {
        Map<String, String> error = new HashMap<>();
        error.put("peticion", textoOriginal);
        error.put("respuesta", "Error: " + e.getMessage());
        return error;
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