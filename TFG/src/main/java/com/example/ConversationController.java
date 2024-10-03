package com.example;

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

    @Get
    @View("conversation_template")
    public Mono<Map<String, Object>> getConversation(
            @QueryValue(defaultValue = "") String ingredientes,
            @QueryValue(defaultValue = "5") int numero) {
        if (ingredientes.isEmpty()) {
            // Usa un HashMap mutable en lugar de Map.of()
            Map<String, Object> result = new HashMap<>();
            result.put("recetas", Collections.emptyList());
            result.put("busquedaRealizada", false);
            return Mono.just(result);
        }
        return spoonacularService.busquedaRecetas(ingredientes, numero)
                .map(recetas -> {
                    Map<String, Object> result = new HashMap<>();
                    result.put("recetas", recetas);
                    result.put("busquedaRealizada", true);
                    return result;
                });
    }

    @Get("/recipe/{id}")
    @View("detalles_receta")
    public Mono<Map<String, Object>> getDetallesReceta(@PathVariable Integer id) {
        return spoonacularService.getDetallesReceta(id)
                .map(detallesReceta -> {
                    Map<String, Object> model = new HashMap<>();
                    model.put("receta", detallesReceta);
                    return model;
                });
    }
}