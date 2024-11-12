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

@Controller("/tiempo")
public class TiempoController {
    private static final Logger log = LoggerFactory.getLogger(TiempoController.class);

    @Inject
    private WeatherService weatherService;

    @Inject
    private LMStudioService lmStudioService;

    @Get("/")
    @View("tiempo_template")
    public Map<String, Object> getTiempo() {
        return new HashMap<>();
    }

    @Post("/procesar")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Flux<Map<String, String>> procesarConsultaTiempo(@Body Map<String, String> peticion) {
        String textoOriginal = peticion.get("texto");
        return Flux.concat(
                emitirRespuesta("Extrayendo información del viaje..."),
                lmStudioService.extraerInformacionViaje(textoOriginal)
                        .concatMap(infoViaje ->
                                Flux.concat(
                                        emitirRespuesta("Información extraída: " + infoViaje),
                                        emitirRespuesta("Obteniendo pronóstico del tiempo..."),
                                        weatherService.obtenerPronostico(
                                                        infoViaje.get("destino"),
                                                        Integer.parseInt(infoViaje.get("diasHastaViaje")),
                                                        Integer.parseInt(infoViaje.get("duracionViaje"))
                                                )
                                                .concatMap(pronostico ->
                                                        Flux.concat(
                                                                emitirRespuesta("Pronóstico obtenido"),
                                                                emitirRespuesta("Generando consejo de ropa..."),
                                                                lmStudioService.generarConsejoRopa(pronostico.toString())
                                                                        .concatMap(consejoEnIngles ->
                                                                                Flux.concat(
                                                                                        emitirRespuesta("Consejo generado en inglés"),
                                                                                        emitirRespuesta("Traduciendo consejo al español..."),
                                                                                        lmStudioService.traducirRespuesta(consejoEnIngles)
                                                                                                .map(consejoFinal -> {
                                                                                                    Map<String, String> resultado = new HashMap<>();
                                                                                                    resultado.put("respuesta", consejoFinal);
                                                                                                    return resultado;
                                                                                                })
                                                                                )
                                                                        )
                                                        )
                                                )
                                )
                        )
        ).onErrorResume(e -> {
            log.error("Error al procesar la consulta de tiempo: ", e);
            return Flux.just(crearRespuestaError(textoOriginal, e));
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