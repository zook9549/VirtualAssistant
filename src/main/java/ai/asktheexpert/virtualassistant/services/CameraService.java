package ai.asktheexpert.virtualassistant.services;

import ai.asktheexpert.virtualassistant.models.Camera;

import java.io.IOException;
import java.util.List;

public interface CameraService {
    String getStream(Camera camera, String id) throws IOException;
    List<Camera> getCameras();
}
