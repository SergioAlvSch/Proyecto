package com.example;

import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.*;
import io.micronaut.views.View;
import jakarta.inject.Inject;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Controller("/conversation")
public class ConversationController {

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
    @Produces(MediaType.TEXT_PLAIN)
    public Mono<String> procesarPeticion(@Body Map<String, String> peticion) {
        String texto = peticion.get("texto");
        return lmStudioService.procesarTexto("Traduce estos ingredientes al inglés y genera una URL para Spoonacular: " + texto)
                .flatMap(url -> {
                    if (!url.startsWith("https://")) {
                        return Mono.error(new RuntimeException("URL inválida generada por LM Studio: " + url));
                    }
                    return spoonacularService.realizarPeticionPersonalizada(url);
                })
                .flatMap(respuestaSpoonacular ->
                        lmStudioService.procesarTexto("Resume estas recetas en español: " + respuestaSpoonacular))
                .flatMap(resumen ->
                        lmStudioService.procesarTexto("Genera una respuesta amigable en español con este resumen de recetas: " + resumen))
                .onErrorResume(e -> {
                    e.printStackTrace(); // Para logging
                    return Mono.just("Lo siento, hubo un error al procesar tu solicitud. Por favor, intenta de nuevo.");
                });
    }
}
