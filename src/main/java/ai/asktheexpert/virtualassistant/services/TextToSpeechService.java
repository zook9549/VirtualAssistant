package ai.asktheexpert.virtualassistant.services;

import ai.asktheexpert.virtualassistant.models.Persona;

public interface TextToSpeechService extends CreditService {
    byte[] getTextToSpeech(Persona persona, String text) throws Exception;

}
