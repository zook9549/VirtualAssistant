package ai.asktheexpert.virtualassistant.services;

import ai.asktheexpert.virtualassistant.models.Persona;

import java.io.IOException;
import java.util.Collection;

public interface PersonaService {

    Collection<Persona> getPersonas();
    Persona addPersona(String personaName, String personaRole, byte[] audio, byte[] profilePicture) throws IOException;
}
