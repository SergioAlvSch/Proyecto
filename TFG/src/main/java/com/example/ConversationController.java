package com.example;

import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.views.View;
import jakarta.inject.Inject;

import java.util.HashMap;
import java.util.Map;


@Controller("/conversation")
public class ConversationController {

    @Inject
    private ConversationService conversationService;

    @View("conversation_template")
    @Get
    public Map<String, Object> conversation(@QueryValue(defaultValue = "") String fraseUsuario) {
        Map<String, Object> model = new HashMap<>();

        if (!fraseUsuario.isEmpty()) {
            conversationService.addConversation(fraseUsuario, reversePhrase(fraseUsuario));
        }

        model.put("historial", conversationService.getHistorial());
        return model;
    }

    private String reversePhrase(String frase) {
        return new StringBuilder(frase).reverse().toString();
    }
}
