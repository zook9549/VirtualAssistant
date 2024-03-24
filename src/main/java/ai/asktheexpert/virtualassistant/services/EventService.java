package ai.asktheexpert.virtualassistant.services;

import ai.asktheexpert.virtualassistant.models.Event;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class EventService {

    @Cacheable("events")
    public List<Event> getAllEvents() {
        return events;
    }

    public void save(Event event) {
        events.add(event);
    }

    public Event getEvent(String cameraId, String eventId) {
        Optional<Event> result = events.stream()
                .filter(obj -> obj.getCamera().getId().equals(cameraId) && obj.getEventId().equals(eventId))
                .findFirst();

        return result.orElse(null);
    }

    private final List<Event> events = new ArrayList<>();
}
