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

@Controller("/")
public class ConversationController {
    private static final Logger log = LoggerFactory.getLogger(ConversationController.class);

    @Inject
    private SpoonacularService spoonacularService;

    @Inject
    private LMStudioService lmStudioService;

    @Inject
    private RssReaderService rssReaderService;

    @Get
    @View("principal")
    public Map<String, Object> principal() {
        return new HashMap<>();
    }

    @Get("/conversation")
    @View("conversation_template")
    public Map<String, Object> getConversation() {
        return new HashMap<>();
    }

    @Get("/noticias")
    @View("noticias_template")
    public Map<String, Object> getNoticias() {
        return new HashMap<>();
    }

    @Post("/conversation/procesar")
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

    @Get("/conversation/noticias")
    @Produces(MediaType.APPLICATION_JSON)
    public Mono<Map<String, String>> obtenerNoticias(@QueryValue String feedUrl) {
        return rssReaderService.readRssFeed(feedUrl)
                .flatMap(noticias -> lmStudioService.procesarNoticias(noticias))
                .map(resumen -> {
                    Map<String, String> resultado = new HashMap<>();
                    resultado.put("resumen", resumen);
                    return resultado;
                })
                .onErrorResume(e -> {
                    log.error("Error al obtener noticias: ", e);
                    Map<String, String> error = new HashMap<>();
                    error.put("resumen", "Lo siento, hubo un error al obtener las noticias: " + e.getMessage());
                    return Mono.just(error);
                });
    }
}