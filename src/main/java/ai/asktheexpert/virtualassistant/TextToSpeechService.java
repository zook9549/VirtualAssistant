package ai.asktheexpert.virtualassistant;

public interface TextToSpeechService extends CreditService {
    byte[] getTextToSpeech(Persona persona, String text) throws Exception;

}
