package com.example;

import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.*;
import io.micronaut.serde.ObjectMapper;
import io.micronaut.views.View;
import jakarta.inject.Inject;
import reactor.core.publisher.Mono;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Controller("/tiempo")
public class TiempoController {
    private static final Logger log = LoggerFactory.getLogger(TiempoController.class);

    @Inject
    private WeatherService weatherService;

    @Inject
    private LMStudioService lmStudioService;

    @Inject
    private ObjectMapper objectMapper;

    @Get("/")
    @View("tiempo_template")
    public Map<String, Object> getTiempo() {
        return new HashMap<>();
    }

    @Post("/procesar")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Mono<Map<String, String>> procesarConsultaTiempo(@Body Map<String, String> peticion) {
        String textoOriginal = peticion.get("texto");
        return lmStudioService.extraerInformacionViaje(textoOriginal)
                .flatMap(infoViaje -> {
                    String destino = infoViaje.get("destino");
                    int diasHastaViaje = Integer.parseInt(infoViaje.get("diasHastaViaje"));
                    int duracionViaje = Integer.parseInt(infoViaje.get("duracionViaje"));
                    return weatherService.obtenerPronostico(destino, diasHastaViaje, duracionViaje);
                })
                .flatMap(pronostico -> {
                    try {
                        String pronosticoJson = objectMapper.writeValueAsString(pronostico);
                        return lmStudioService.generarConsejoRopa(pronosticoJson);
                    } catch (IOException e) {
                        log.error("Error al serializar el pronóstico", e);
                        return Mono.error(new RuntimeException("Error al procesar el pronóstico del tiempo", e));
                    }
                })
                .flatMap(lmStudioService::traducirRespuesta)
                .map(consejo -> {
                    Map<String, String> resultado = new HashMap<>();
                    resultado.put("peticion", textoOriginal);
                    resultado.put("respuesta", consejo);
                    return resultado;
                })
                .onErrorResume(e -> {
                    log.error("Error al procesar la consulta de tiempo: ", e);
                    Map<String, String> error = new HashMap<>();
                    error.put("peticion", textoOriginal);
                    error.put("respuesta", "Lo siento, hubo un error al procesar tu consulta de tiempo: " + e.getMessage());
                    return Mono.just(error);
                });
    }
}