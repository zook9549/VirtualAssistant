package ai.asktheexpert.virtualassistant.services;

import ai.asktheexpert.virtualassistant.models.Assistant;
import ai.asktheexpert.virtualassistant.models.AssistantResponse;

public interface AnswerService {
    AssistantResponse answer(String question, Assistant assistant);

    void save(AssistantResponse assistantResponse);
}
