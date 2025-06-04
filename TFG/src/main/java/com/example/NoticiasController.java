package com.example;

import com.rometools.rome.feed.synd.SyndEntry;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.*;
import io.micronaut.serde.ObjectMapper;
import io.micronaut.views.View;
import jakarta.inject.Inject;
import reactor.core.publisher.Flux;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

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
    @Produces(MediaType.TEXT_EVENT_STREAM)
    public Flux<String> obtenerNoticias(@Body Map<String, String> peticion) {
        String feedUrl = peticion.get("texto");
        log.info("Recibida solicitud de noticias para URL: {}", feedUrl);

        return rssReaderService.readRssFeed(feedUrl)
                .flatMap(entries -> Flux.fromIterable(entries))
                .take(4) // Solo 4 noticias
                .index()
                .flatMapSequential(tuple2 -> {
                    long idx = tuple2.getT1();
                    SyndEntry entry = tuple2.getT2();
                    // Enviar la lista de originales solo la primera vez
                    Flux<String> originalsFlux = idx == 0
                            ? Flux.just(Map.of(
                                    "originals", rssReaderService.getLastEntries().stream().limit(4).map(NoticiasController::toOriginalMap).collect(Collectors.toList())
                            ))
                            .map(map -> toJson(map))
                            : Flux.empty();
                    // Procesar cada noticia individualmente
                    Flux<String> processedFlux = lmStudioService.procesarNoticiaIndividual(entry)
                            .flatMap(summaryEnIngles -> lmStudioService.traducirNoticias(summaryEnIngles)
                                    .map(resumenEnEspanol -> Map.of("processed", NoticiasController.toProcessedMap(entry, resumenEnEspanol)))
                            )
                            .map(map -> toJson(map));
                    return originalsFlux.concatWith(processedFlux);
                });
    }

    private static Map<String, Object> toOriginalMap(SyndEntry entry) {
        return Map.of(
                "title", entry.getTitle(),
                "description", entry.getDescription() != null ? entry.getDescription().getValue() : "",
                "link", entry.getLink(),
                "image", LMStudioService.extraerImagen(entry)
        );
    }

    private static Map<String, Object> toProcessedMap(SyndEntry entry, String summary) {
        return Map.of(
                "title", entry.getTitle(),
                "link", entry.getLink(),
                "image", LMStudioService.extraerImagen(entry),
                "summary", summary
        );
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj) + "\n";
        } catch (Exception e) {
            return "{\"error\": \"Error al serializar\"}\n";
        }
    }
}