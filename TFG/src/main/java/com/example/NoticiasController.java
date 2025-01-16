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
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Controller("/noticias")
public class NoticiasController {
    private static final Logger log = LoggerFactory.getLogger(NoticiasController.class);

    @Inject
    ObjectMapper objectMapper;

    @Inject
    private LMStudioService lmStudioService;

    @Inject
    private RssReaderService rssReaderService;

    @Get("/")
    @View("noticias_template")
    public Map<String, Object> getNoticias() {
        return new HashMap<>();
    }

    @Post("/procesar")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.TEXT_EVENT_STREAM)
    public Flux<String> obtenerNoticias(@Body Map<String, String> peticion) {
        String feedUrl = peticion.get("texto");
        log.info("Recibida solicitud de noticias para URL: {}", feedUrl);

        return Flux.defer(() -> rssReaderService.readRssFeed(feedUrl)
                        .flatMap(noticias -> lmStudioService.procesarNoticias(noticias))
                        .flatMap(resumenEnIngles -> lmStudioService.traducirNoticias(resumenEnIngles))
                        .defaultIfEmpty("No se encontraron noticias para procesar.")
                        .doOnNext(respuesta -> log.info("Respuesta final: {}", respuesta))
                        .onErrorResume(e -> {
                            log.error("Error al obtener noticias: ", e);
                            return Flux.just("Error: " + e.getMessage());
                        }))
                .subscribeOn(Schedulers.boundedElastic())
                .take(1);   // Toma solo la primera respuesta
    }
}