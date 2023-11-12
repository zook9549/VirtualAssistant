package ai.asktheexpert.virtualassistant.models;

import lombok.*;

@ToString
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode
public class AssistantResponse {
    private Moods mood;
    private String person;
    private String originalQuestion;
    private String response;
    private String detailedResponse;
    private String audioUrl;
    private String videoUrl;
    private String messageId;
    private String threadId;
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private byte[] audio;
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private byte[] video;
}
