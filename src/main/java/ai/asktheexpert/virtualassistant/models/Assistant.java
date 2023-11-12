package ai.asktheexpert.virtualassistant.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

@ToString
@Getter
@Setter
@EqualsAndHashCode
public class Assistant {
    private String name;
    private String description;
    private String instructions;
    private String assistantId;
    private String voiceId;
    private String avatarId;
    private String profilePicture;
    private String idleVideo;
    @EqualsAndHashCode.Exclude
    private List<String> smallTalkVideos = new ArrayList<>();
    @EqualsAndHashCode.Exclude
    private List<String> startingVideos = new ArrayList<>();
    @EqualsAndHashCode.Exclude
    private List<String> introVideos = new ArrayList<>();
    @EqualsAndHashCode.Exclude
    private Moods currentMood;
    @EqualsAndHashCode.Exclude
    @JsonIgnore
    private LinkedHashMap<String, List<AssistantResponse>> threads = new LinkedHashMap<>();
    @EqualsAndHashCode.Exclude
    private String lastRunId;
}
