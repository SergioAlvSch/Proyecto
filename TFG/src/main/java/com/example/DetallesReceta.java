package com.example;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

import java.util.List;

@Introspected
@Serdeable
public class DetallesReceta {
    private Integer id;
    private String title;
    private String image;
    private List<Ingrediente> extendedIngredients;
    private List<Instruccion> analyzedInstructions;

    // Getters y setters
    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getImage() {
        return image;
    }

    public void setImage(String image) {
        this.image = image;
    }

    public List<Ingrediente> getExtendedIngredients() {
        return extendedIngredients;
    }

    public void setExtendedIngredients(List<Ingrediente> extendedIngredients) {
        this.extendedIngredients = extendedIngredients;
    }

    public List<Instruccion> getAnalyzedInstructions() {
        return analyzedInstructions;
    }

    public void setAnalyzedInstructions(List<Instruccion> analyzedInstructions) {
        this.analyzedInstructions = analyzedInstructions;
    }

    @Introspected
    @Serdeable
    public static class Ingrediente {
        private String original;

        public String getOriginal() {
            return original;
        }

        public void setOriginal(String original) {
            this.original = original;
        }
    }

    @Introspected
    @Serdeable
    public static class Instruccion {
        private List<Paso> steps;

        public List<Paso> getSteps() {
            return steps;
        }

        public void setSteps(List<Paso> steps) {
            this.steps = steps;
        }
    }

    @Introspected
    @Serdeable
    public static class Paso {
        private String step;

        public String getStep() {
            return step;
        }

        public void setStep(String step) {
            this.step = step;
        }
    }
}
