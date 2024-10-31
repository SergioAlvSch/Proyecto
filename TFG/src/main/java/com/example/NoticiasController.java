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

@Controller("/noticias")
public class NoticiasController {
    private static final Logger log = LoggerFactory.getLogger(NoticiasController.class);

    @Inject
    private RssReaderService rssReaderService;

    @Inject
    private LMStudioService lmStudioService;

    @Get("/")
    @View("noticias_template")
    public Map<String, Object> getNoticias() {
        return new HashMap<>();
    }

    @Get("/procesar")
    @Produces(MediaType.APPLICATION_JSON)
    public Mono<Map<String, String>> obtenerNoticias(@QueryValue String feedUrl) {
        return rssReaderService.readRssFeed(feedUrl)
                .flatMap(noticias -> lmStudioService.procesarNoticias(noticias))
                .flatMap(lmStudioService::traducirRespuesta)
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