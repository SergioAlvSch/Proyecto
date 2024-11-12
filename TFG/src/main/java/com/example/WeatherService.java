package com.example;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.json.tree.JsonNode;
import io.micronaut.serde.ObjectMapper;
import jakarta.inject.Singleton;
import reactor.core.publisher.Flux;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

@Singleton
public class WeatherService {
    private static final Logger log = LoggerFactory.getLogger(WeatherService.class);
    private final HttpClient client;
    private final ObjectMapper objectMapper;
    private final String apiKey = "17dede6290b64cc8b5274628242709";
    private final String baseUrl = "http://api.weatherapi.com/v1";

    public WeatherService(HttpClient client, ObjectMapper objectMapper) {
        this.client = client;
        this.objectMapper = objectMapper;
    }

    public Flux<Map<String, Object>> obtenerPronostico(String destino, int diasHastaViaje, int duracionViaje) {
        String destinoEncoded = URLEncoder.encode(destino, StandardCharsets.UTF_8);
        String url = baseUrl + "/forecast.json?key=" + apiKey + "&q=" + destinoEncoded + "&days=" + (diasHastaViaje + duracionViaje);
        return Flux.from(client.retrieve(HttpRequest.GET(url), String.class))
                .onErrorResume(e -> {
                    log.error("Error al obtener el pronóstico para " + destino, e);
                    return Flux.just("No se pudo obtener el pronóstico para " + destino);
                })
                .map(this::procesarRespuesta);
    }

    private Map<String, Object> procesarRespuesta(String respuestaJson) {
        try {
            JsonNode jsonNode = objectMapper.readValue(respuestaJson, JsonNode.class);
            Map<String, Object> resultado = new HashMap<>();

            // Información de la ubicación
            resultado.put("ciudad", jsonNode.get("location").get("name").getStringValue());
            resultado.put("pais", jsonNode.get("location").get("country").getStringValue());

            // Pronóstico para los próximos días
            List<Map<String, Object>> pronosticoDiario = new ArrayList<>();
            JsonNode forecastDays = jsonNode.get("forecast").get("forecastday");
            if (forecastDays.isArray()) {
                for (int i = 0; i < forecastDays.size(); i++) {
                    JsonNode day = forecastDays.get(i);
                    Map<String, Object> infoDia = new HashMap<>();
                    infoDia.put("fecha", day.get("date").getStringValue());
                    infoDia.put("maxtemp_c", day.get("day").get("maxtemp_c").getNumberValue().doubleValue());
                    infoDia.put("mintemp_c", day.get("day").get("mintemp_c").getNumberValue().doubleValue());
                    infoDia.put("avgtemp_c", day.get("day").get("avgtemp_c").getNumberValue().doubleValue());
                    infoDia.put("condicion", day.get("day").get("condition").get("text").getStringValue());
                    infoDia.put("probabilidad_lluvia", day.get("day").get("daily_chance_of_rain").getNumberValue().intValue());
                    pronosticoDiario.add(infoDia);
                }
            }
            resultado.put("pronostico", pronosticoDiario);

            return resultado;
        } catch (Exception e) {
            log.error("Error al procesar la respuesta JSON del pronóstico", e);
            throw new RuntimeException("Error al procesar el pronóstico del tiempo", e);
        }
    }
}