package ai.asktheexpert.virtualassistant.services;

import ai.asktheexpert.virtualassistant.models.Persona;

import java.net.URL;

public interface AvatarService extends CreditService {
    byte[] getVideo(Persona persona, URL audioUrl) throws Exception;
    byte[] getVideo(Persona persona, String text) throws Exception;
}