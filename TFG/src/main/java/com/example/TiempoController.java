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
    @Produces(MediaType.TEXT_EVENT_STREAM)
    public Flux<String> procesarConsultaTiempo(@Body Map<String, String> peticion) {
        String texto = peticion.get("texto");
        log.info("Recibida solicitud de tiempo: {}", texto);

        return lmStudioService.extraerInformacionViaje(texto)
                .flatMap(infoViaje -> {
                    if (infoViaje.containsKey("error")) {
                        return Flux.just(crearJson("error", infoViaje.get("error")));
                    }

                    int diasTotales = Integer.parseInt(infoViaje.getOrDefault("duracionViaje", "0"));
                    return weatherService.obtenerPronostico(infoViaje.get("destino"), diasTotales)
                            .flatMap(pronostico -> lmStudioService.generarConsejoRopa(
                                            pronostico.toString(),
                                            ((Number) pronostico.get("diasPronosticados")).intValue(),
                                            diasTotales,
                                            infoViaje.get("destino"))
                                    .flatMap(consejoEnIngles -> lmStudioService.traducirRespuesta(consejoEnIngles)
                                            .map(consejoEnEspanol -> crearJsonCompleto(pronostico, consejoEnEspanol))
                                    )
                            );
                })
                .onErrorResume(e -> Flux.just(crearJson("error", "Error: " + e.getMessage())));
    }

    private String crearJsonCompleto(Object pronostico, String consejo) {
        Map<String, Object> resultado = new HashMap<>();
        resultado.put("pronostico", pronostico);
        resultado.put("consejo", consejo);
        try {
            return objectMapper.writeValueAsString(resultado);
        } catch (JsonProcessingException e) {
            log.error("Error al serializar el resultado", e);
            return "{\"error\": \"Error al procesar el tiempo\"}";
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private String crearJson(String tipo, String contenido) {
        Map<String, String> resultado = new HashMap<>();
        resultado.put(tipo, contenido);
        try {
            return objectMapper.writeValueAsString(resultado);
        } catch (JsonProcessingException e) {
            log.error("Error al serializar el resultado", e);
            return "{\"error\": \"Error al procesar la respuesta\"}";
        } catch (IOException e) {
            throw new RuntimeException("Error de E/S", e);
        }
    }
}
