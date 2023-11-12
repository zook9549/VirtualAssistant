package ai.asktheexpert.virtualassistant.services;

import ai.asktheexpert.virtualassistant.models.Assistant;

import java.io.IOException;
import java.util.Collection;

public interface AssistantService {

    Collection<Assistant> getAssistants();

    String save(Assistant assistant);

    void update(Assistant assistant);
}
