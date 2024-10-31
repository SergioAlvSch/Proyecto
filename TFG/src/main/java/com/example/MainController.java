package com.example;

import io.micronaut.http.annotation.*;
import io.micronaut.views.View;

import java.util.HashMap;
import java.util.Map;

@Controller("/")
public class MainController {

    @Get
    @View("principal")
    public Map<String, Object> principal() {
        return new HashMap<>();
    }
}