package ai.asktheexpert.virtualassistant.services;

import ai.asktheexpert.virtualassistant.models.Persona;

public interface AnswerService {
    String answer(String question, Persona persona);
}
