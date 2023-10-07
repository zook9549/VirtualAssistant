package ai.asktheexpert.virtualassistant;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;

@ToString
@Getter
@Setter
@EqualsAndHashCode
public class Persona {
    private String name;
    private String role;
    private String voiceId;
    private String idleVideoId;
    private String idleVideoUrl;
    private String idleVideoStatus;
    private String avatarId;
    @EqualsAndHashCode.Exclude
    private Moods currentMood;
    @EqualsAndHashCode.Exclude
    private List<String> smallTalkVideos = new ArrayList<>();
    @EqualsAndHashCode.Exclude
    private List<String> startingVideos = new ArrayList<>();
    @EqualsAndHashCode.Exclude
    private List<String> introVideos = new ArrayList<>();
}
