package ai.asktheexpert.virtualassistant.models;

import lombok.*;

import java.time.LocalDateTime;

@ToString
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode
public class Event {
    private Camera camera;
    private String eventId;
    private LocalDateTime eventDateTime;
    private String videoUrl;
    private String narration;
}
