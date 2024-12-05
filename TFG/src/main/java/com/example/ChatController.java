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

@Controller("/chat")
public class ChatController {
    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    @Inject
    ObjectMapper objectMapper;

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
        String mensaje = peticion.get("texto");
        log.info("Recibida solicitud de chat: {}", mensaje);
        return lmStudioService.procesarTexto(mensaje)
                .defaultIfEmpty("No se recibió respuesta del servicio.")
                .onErrorResume(e -> {
                    log.error("Error al procesar el mensaje en el chat: ", e);
                    return Flux.just("Error: " + e.getMessage());
                });
    }

//    private Flux<String> emitirRespuesta(String mensaje) {
//        Map<String, String> respuesta = new HashMap<>();
//        respuesta.put("respuesta", mensaje);
//        try {
//            String jsonRespuesta = objectMapper.writeValueAsString(respuesta);
//            log.info("Emitiendo respuesta JSON: {}", jsonRespuesta);
//            return Flux.just(jsonRespuesta);
//        } catch (JsonProcessingException e) {
//            log.error("Error al serializar la respuesta", e);
//            return Flux.error(e);
//        } catch (IOException e) {
//            throw new RuntimeException(e);
//        }
//    }
//
//    private Map<String, String> crearRespuestaError(String textoOriginal, Throwable e) {
//        Map<String, String> error = new HashMap<>();
//        error.put("peticion", textoOriginal);
//        error.put("respuesta", "Error: " + e.getMessage());
//        return error;
//    }
}