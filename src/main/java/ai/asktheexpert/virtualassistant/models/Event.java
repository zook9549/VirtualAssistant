package ai.asktheexpert.virtualassistant.models;

import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@ToString
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(of = {"camera", "eventId"})
public class Event {

    private Camera camera;
    private String eventId;
    private LocalDateTime eventDateTime;
    private String videoUrl;
    @ToString.Exclude
    private List<String> encodedImages = new ArrayList();
    private String narration;
    private Status status;
    private boolean temporary;

    public enum Status {
        IN_PROGRESS,
        DONE
    }
}
