package ai.asktheexpert.virtualassistant.services;

import ai.asktheexpert.virtualassistant.models.Assistant;

import java.net.URL;

public interface AvatarService extends CreditService {
    byte[] getVideo(Assistant assistant, URL audioUrl) throws Exception;
    byte[] getVideo(Assistant assistant, String text) throws Exception;
}
