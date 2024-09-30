package com.example;

import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Singleton
public class ConversationService {
    private List<Map<String, String>> historial = new ArrayList<>();

    public void addConversation(String fraseUsuario, String respuesta) {
        Map<String, String> entry = new HashMap<>();
        entry.put("frase", fraseUsuario);
        entry.put("respuesta", respuesta);
        historial.add(entry);
    }

    public List<Map<String, String>> getHistorial() {
        return historial;
    }
}
