package ai.asktheexpert.virtualassistant.services;

import ai.asktheexpert.virtualassistant.models.Event;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

@Service
public class EventService {

    @Cacheable("events")
    public Collection<Event> getAllEvents() {
        return events;
    }

    public void save(Event event) {
        events.add(event);
    }

    public boolean delete(Event event) {
        return events.remove(event);
    }

    public Event getEvent(String cameraId, String eventId) {
        Optional<Event> result = events.stream()
                .filter(obj -> obj.getCamera().getId().equals(cameraId) && obj.getEventId().equals(eventId))
                .findFirst();

        return result.orElse(null);
    }

    private final Set<Event> events = new HashSet<>();
}
