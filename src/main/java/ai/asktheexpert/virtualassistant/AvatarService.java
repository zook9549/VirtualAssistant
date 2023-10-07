package ai.asktheexpert.virtualassistant;

import java.net.URL;

public interface AvatarService {
    byte[] getVideo(Persona persona, URL audioUrl) throws Exception;
    byte[] getVideo(Persona persona, String text) throws Exception;
    int creditsRemaining();
}
