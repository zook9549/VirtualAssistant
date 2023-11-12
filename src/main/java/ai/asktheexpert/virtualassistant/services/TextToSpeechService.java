package ai.asktheexpert.virtualassistant.services;

import ai.asktheexpert.virtualassistant.models.Assistant;
import ai.asktheexpert.virtualassistant.models.Persona;

public interface TextToSpeechService extends CreditService {
    byte[] getTextToSpeech(Assistant assistant, String text) throws Exception;

    String save(String name, String role, byte[] audio);

}
