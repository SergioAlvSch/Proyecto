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

    @Post("/procesar")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Flux<Map<String, String>> obtenerNoticias(@Body Map<String, String> peticion) {
        String feedUrl = peticion.get("texto");
        return Flux.concat(
                emitirRespuesta("Leyendo feed RSS..."),
                rssReaderService.readRssFeed(feedUrl)
                        .concatMap(noticias ->
                                Flux.concat(
                                        emitirRespuesta("Feed RSS leído. Procesando noticias..."),
                                        lmStudioService.procesarNoticias(noticias)
                                                .concatMap(resumenEnIngles ->
                                                        Flux.concat(
                                                                emitirRespuesta("Resumen en inglés generado"),
                                                                emitirRespuesta("Traduciendo resumen al español..."),
                                                                lmStudioService.traducirRespuesta(resumenEnIngles)
                                                                        .map(resumenFinal -> {
                                                                            Map<String, String> resultado = new HashMap<>();
                                                                            resultado.put("respuesta", resumenFinal);
                                                                            return resultado;
                                                                        })
                                                        )
                                                )
                                )
                        )
        ).onErrorResume(e -> {
            log.error("Error al obtener noticias: ", e);
            return Flux.just(crearRespuestaError(feedUrl, e));
        });
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
}