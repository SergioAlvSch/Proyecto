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

@Controller("/conversation")
public class ConversationController {
    private static final Logger log = LoggerFactory.getLogger(ConversationController.class);

    @Inject
    private SpoonacularService spoonacularService;

    @Inject
    private LMStudioService lmStudioService;

    @Get
    @View("conversation_template")
    public Map<String, Object> getConversation() {
        Map<String, Object> model = new HashMap<>();
        model.put("busquedaRealizada", false);
        return model;
    }

    @Get("/recipe/{id}")
    @View("detalles_receta")
    public Mono<Map<String, Object>> getDetallesReceta(@PathVariable Integer id) {
        String url = "https://api.spoonacular.com/recipes/" + id + "/information?apiKey=6b914274211f42b281b0242d60afac98";
        return spoonacularService.realizarPeticionPersonalizada(url)
                .flatMap(spoonacularService::procesarDetallesReceta)
                .map(detallesReceta -> {
                    Map<String, Object> model = new HashMap<>();
                    model.put("receta", detallesReceta);
                    return model;
                });
    }

    @Post("/procesar")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Mono<Map<String, String>> procesarPeticion(@Body Map<String, String> peticion) {
        String textoOriginal = peticion.get("texto");
        return lmStudioService.traducirConsulta(textoOriginal)
                .flatMap(consultaTraducida -> lmStudioService.generarURLSpoonacular(consultaTraducida))
                .flatMap(url -> spoonacularService.realizarPeticionPersonalizada(url))
                .flatMap(respuestaSpoonacular ->
                        lmStudioService.procesarTexto("Resume esta información de Spoonacular en español: " + respuestaSpoonacular))
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
                    error.put("respuesta", "Lo siento, hubo un error al procesar tu solicitud. Por favor, intenta de nuevo.");
                    return Mono.just(error);
                });
    }
}