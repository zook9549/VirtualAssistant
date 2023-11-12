package ai.asktheexpert.virtualassistant.services;

import ai.asktheexpert.virtualassistant.models.Assistant;

public interface TextToSpeechService extends CreditService {
    byte[] getTextToSpeech(Assistant assistant, String text) throws Exception;

    String save(String name, String role, byte[] audio);

}
