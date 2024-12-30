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
                        .flatMap(resumenEnIngles -> lmStudioService.traducirRespuesta(resumenEnIngles))
                        .defaultIfEmpty("No se encontraron noticias para procesar.")
                        .doOnNext(respuesta -> log.info("Respuesta final: {}", respuesta))
                        .onErrorResume(e -> {
                            log.error("Error al obtener noticias: ", e);
                            return Flux.just("Error: " + e.getMessage());
                        }))
                .subscribeOn(Schedulers.boundedElastic());
    }
}


//    private Flux<String> emitirRespuesta(String mensaje) {
//        Map<String, String> respuesta = new HashMap<>();
//        respuesta.put("respuesta", mensaje);
//        try {
//            return Flux.just(objectMapper.writeValueAsString(respuesta));
//        } catch (IOException e) {
//            log.error("Error al serializar la respuesta", e);
//            return Flux.error(e);
//        }
//    }
//
//    private String crearRespuestaError(String textoOriginal, Throwable e) {
//        Map<String, String> error = new HashMap<>();
//        error.put("peticion", textoOriginal);
//        error.put("respuesta", "Error: " + e.getMessage());
//        try {
//            return objectMapper.writeValueAsString(error);
//        } catch (JsonProcessingException jpe) {
//            log.error("Error al serializar respuesta de error", jpe);
//            return "{\"respuesta\": \"Error interno del servidor\"}";
//        } catch (IOException ex) {
//            throw new RuntimeException(ex);
//        }
//    }