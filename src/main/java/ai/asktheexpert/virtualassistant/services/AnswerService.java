package ai.asktheexpert.virtualassistant.services;

import ai.asktheexpert.virtualassistant.models.Assistant;
import ai.asktheexpert.virtualassistant.models.AssistantResponse;
import ai.asktheexpert.virtualassistant.models.Event;

import java.io.IOException;

public interface AnswerService {
    AssistantResponse answer(String question, Assistant assistant);

    void save(AssistantResponse assistantResponse);

    String narrate(Event event, Assistant assistant) throws IOException;
}
