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
    private String humanizedQuestion;
    private String response;
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private byte[] audio;
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private byte[] video;
}
