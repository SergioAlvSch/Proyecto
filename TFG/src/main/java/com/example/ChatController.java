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

@Controller("/chat")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    @Inject
    private LMStudioService lmStudioService;

    @Get("/")
    @View("chat_template")
    public Map<String, Object> getChat() {
        return new HashMap<>();
    }

    @Post("/procesar")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.TEXT_EVENT_STREAM)
    public Flux<String> procesarMensaje(@Body Map<String, String> peticion) {
        long startTime = System.currentTimeMillis();
        String mensaje = peticion.get("texto");
        log.info("Recibida solicitud de chat: {}", mensaje);
        return lmStudioService.procesarTexto(mensaje)
                .defaultIfEmpty("No se recibió respuesta del servicio.")
                .onErrorResume(e -> {
                    log.error("Error al procesar el mensaje en el chat: ", e);
                    return Flux.just("Error: " + e.getMessage());
                })
                .doOnComplete(() -> {
                    long endTime = System.currentTimeMillis();
                    long duration = endTime - startTime;
                    log.info("Tiempo total de procesamiento de la petición: {} ms (modelo: {})", duration, lmStudioService.getModeloActual());
                });
    }
}