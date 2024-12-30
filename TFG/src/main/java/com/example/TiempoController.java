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
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Controller("/tiempo")
public class TiempoController {
    private static final Logger log = LoggerFactory.getLogger(TiempoController.class);

    @Inject
    ObjectMapper objectMapper;

    @Inject
    private LMStudioService lmStudioService;

    @Inject
    private WeatherService weatherService;

    @Get("/")
    @View("tiempo_template")
    public Map<String, Object> getTiempo() {
        return new HashMap<>();
    }

    @Post("/procesar")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Flux<String> procesarConsultaTiempo(@Body Map<String, String> peticion) {
        String texto = peticion.get("texto");
        log.info("Recibida solicitud de tiempo: {}", texto);

        return Flux.concat(
                        emitirRespuesta("Procesando petición..."),
                        lmStudioService.extraerInformacionViaje(texto)
                                .flatMap(infoViaje -> {
                                    log.info("Información de viaje extraída: {}", infoViaje);
                                    int diasTotales = Integer.parseInt(infoViaje.getOrDefault("diasHastaViaje", "0")) +
                                            Integer.parseInt(infoViaje.getOrDefault("duracionViaje", "0"));
                                    return weatherService.obtenerPronostico(infoViaje.get("destino"), diasTotales)
                                            .flatMap(pronostico -> {
                                                try {
                                                    String pronosticoJson = objectMapper.writeValueAsString(pronostico);
                                                    int diasPronosticados = (int) pronostico.getOrDefault("diasPronosticados", 0);
                                                    return lmStudioService.generarConsejoRopa(pronosticoJson, diasPronosticados, diasTotales, infoViaje.get("destino"));
                                                } catch (JsonProcessingException e) {
                                                    return Flux.error(e);
                                                } catch (IOException e) {
                                                    throw new RuntimeException(e);
                                                }
                                            });
                                })
                                .flatMap(consejoEnIngles -> lmStudioService.traducirRespuesta(consejoEnIngles))
                                .flatMap(this::emitirRespuesta)
                )
                .timeout(Duration.ofMinutes(30))
                .onErrorResume(e -> {
                    log.error("Error al procesar la consulta de tiempo: ", e);
                    return Flux.just(crearRespuestaError(texto, e));
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

    private String crearRespuestaError(String textoOriginal, Throwable e) {
        Map<String, String> error = new HashMap<>();
        error.put("peticion", textoOriginal);
        error.put("respuesta", "Error: " + e.getMessage());
        try {
            return objectMapper.writeValueAsString(error);
        } catch (JsonProcessingException jpe) {
            log.error("Error al serializar respuesta de error", jpe);
            return "{\"respuesta\": \"Error interno del servidor\"}";
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }
}