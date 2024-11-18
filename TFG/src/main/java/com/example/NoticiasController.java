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
    @Produces(MediaType.APPLICATION_JSON)
    public Flux<String> obtenerNoticias(@Body Map<String, String> peticion) {
        String feedUrl = peticion.get("texto");
        log.info("Recibida solicitud de noticias para URL: {}", feedUrl);
        return Flux.concat(
                emitirRespuesta("Procesando petición..."),
                rssReaderService.readRssFeed(feedUrl)
                        .flatMap(noticias ->
                                lmStudioService.procesarNoticias(noticias)
                                        .flatMap(resumenEnIngles ->
                                                lmStudioService.traducirRespuesta(resumenEnIngles)
                                                        .flatMap(this::emitirRespuesta)
                                        )
                        )
        ).onErrorResume(e -> {
            log.error("Error al obtener noticias: ", e);
            return Flux.just(crearRespuestaError(feedUrl, e).toString());
        });
    }

    private Flux<String> emitirRespuesta(String mensaje) {
        Map<String, String> respuesta = new HashMap<>();
        respuesta.put("respuesta", mensaje);
        try {
            String jsonRespuesta = objectMapper.writeValueAsString(respuesta);
            log.info("Emitiendo respuesta JSON: {}", jsonRespuesta);
            return Flux.just(jsonRespuesta);
        } catch (JsonProcessingException e) {
            log.error("Error al serializar la respuesta", e);
            return Flux.error(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private Map<String, String> crearRespuestaError(String textoOriginal, Throwable e) {
        Map<String, String> error = new HashMap<>();
        error.put("peticion", textoOriginal);
        error.put("respuesta", "Error: " + e.getMessage());
        return error;
    }
}