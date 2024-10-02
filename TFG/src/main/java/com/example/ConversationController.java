package com.example;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.views.View;
import reactor.core.publisher.Mono;
import io.micronaut.http.annotation.*;
import jakarta.inject.Inject;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Controller("/conversation")
public class ConversationController {

    @Inject
    private SpoonacularService spoonacularService;

    @Get
    @View("conversation_template")
    public Mono<MutableHttpResponse<?>> getConversation(
            @QueryValue(defaultValue = "") String ingredientes,
            @QueryValue(defaultValue = "5") int numero) {
        if (ingredientes.isEmpty()) {
            return Mono.just(HttpResponse.ok(Collections.emptyMap()));
        }
        return spoonacularService.busquedaRecetas(ingredientes, numero)
                .map(recetas -> HttpResponse.ok(Map.of("recetas", recetas)));
    }
}