package com.example;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.rometools.rome.feed.synd.SyndEntry;
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
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
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
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.TEXT_EVENT_STREAM)
    public Flux<String> obtenerNoticias(@Body Map<String, String> peticion) {
        String feedUrl = peticion.get("texto");
        log.info("Recibida solicitud de noticias para URL: {}", feedUrl);

        return Flux.defer(() -> rssReaderService.readRssFeed(feedUrl)
                .flatMap(noticias -> {
                    String noticiasOriginales = formatearNoticiasOriginales(noticias);

                    return lmStudioService.procesarNoticias(noticias)
                            .flatMap(resumenEnIngles -> lmStudioService.traducirNoticias(resumenEnIngles))
                            .map(resumenTraducido -> crearJsonCompleto(noticiasOriginales, resumenTraducido))
                            .defaultIfEmpty(crearJson("error", "Error al procesar noticias"));
                })
                .defaultIfEmpty(crearJson("error", "No se encontraron noticias para procesar."))
                .onErrorResume(e -> Flux.just(crearJson("error", "Error: " + e.getMessage())))
                .subscribeOn(Schedulers.boundedElastic()));
    }

    private String crearJsonCompleto(String original, String procesado) {
        Map<String, String> resultado = new HashMap<>();
        resultado.put("original", original);
        resultado.put("procesado", procesado);
        try {
            return objectMapper.writeValueAsString(resultado);
        } catch (JsonProcessingException e) {
            log.error("Error al serializar el resultado", e);
            return "{\"error\": \"Error al procesar las noticias\"}";
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private String formatearNoticiasOriginales(List<SyndEntry> noticias) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy");
        return noticias.stream().limit(5)
                .map(entry -> {
                    String date = dateFormat.format(entry.getPublishedDate());
                    String title = entry.getTitle();
                    String description = entry.getDescription().getValue();
                    String link = entry.getLink();
                    return String.format("[%s] %s\n%s\n%s\n", date, title, description, link);
                })
                .collect(Collectors.joining("\n"));
    }

    private String crearJson(String tipo, String contenido) {
        Map<String, String> resultado = new HashMap<>();
        resultado.put(tipo, contenido);
        try {
            return objectMapper.writeValueAsString(resultado);
        } catch (JsonProcessingException e) {
            log.error("Error al serializar el resultado", e);
            return "{\"error\": \"Error al procesar las noticias\"}";
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}