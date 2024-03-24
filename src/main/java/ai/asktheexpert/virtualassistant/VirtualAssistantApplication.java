package ai.asktheexpert.virtualassistant;

import ai.asktheexpert.virtualassistant.models.*;
import ai.asktheexpert.virtualassistant.repositories.FileStore;
import ai.asktheexpert.virtualassistant.services.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProvider;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.annotation.RegisteredOAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.net.URL;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

@SpringBootApplication
@RestController
@EnableScheduling
@EnableCaching
public class VirtualAssistantApplication {

    public VirtualAssistantApplication(ObjectMapper objectMapper, FileStore fileStore, ElevenLabsService textToSpeechService, @Qualifier("chatGPTService") AssistantService assistantService, AnswerService answerService, AvatarService avatarService, CameraService cameraService, EventService eventService) {
        this.objectMapper = objectMapper;
        this.fileStore = fileStore;
        this.textToSpeechService = textToSpeechService;
        this.assistantService = assistantService;
        this.answerService = answerService;
        this.cameraService = cameraService;
        this.avatarService = avatarService;
        this.eventService = eventService;
    }

    public static void main(String[] args) {
        SpringApplication.run(VirtualAssistantApplication.class, args);
    }

    @RequestMapping(value = "/askText")
    public AssistantResponse getHumanizedAnswerAsText(String question, @RequestParam(required = false) Assistant assistant) {
        if (assistant == null) {
            assistant = availableAssistants.values().iterator().next();
            assistant.setCurrentMood(Moods.getRandomMood());
        }
        if (question.startsWith("*")) {
            AssistantResponse response = new AssistantResponse();
            response.setPerson(assistant.getName());
            response.setMood(assistant.getCurrentMood());
            response.setOriginalQuestion(question);
            response.setResponse(question.substring(1));
            response.setDetailedResponse(question.substring(1));
            return response;
        } else {
            return answerService.answer(question, assistant);
        }
    }

    @RequestMapping(value = "/askWithDetails")
    public AssistantResponse getAnswer(String question, String assistantId, @RequestParam(required = false) Moods mood) throws Exception {
        Assistant assistant = availableAssistants.get(assistantId);
        assistant.setCurrentMood(mood);

        log.debug("Getting answer for {}", assistant);

        AssistantResponse response = getHumanizedAnswerAsText(question, assistant);
        String answer = response.getResponse();

        String audioUrl = getTextToSpeech(assistantId, answer);
        response.setAudioUrl(audioUrl);
        try {
            getVideo(assistant, answer, audioUrl, true);
            String videoUrl = fileStore.getUrl(assistant.getAssistantId(), FileStore.MediaType.MP4, answer);
            response.setVideoUrl(videoUrl);
            answerService.save(response);
        } catch (Exception ex) {
            log.error("Failed to generate video", ex);
        }
        log.debug("Completed getting details");
        return response;
    }

    @RequestMapping(value = "/tts", produces = "audio/mpeg")
    public String getTextToSpeech(String assistantId, @RequestParam String text) throws Exception {
        Assistant assistant = availableAssistants.get(assistantId);
        String url = fileStore.getUrl(assistantId, FileStore.MediaType.MP3, text);
        if (url == null) {
            byte[] audio = textToSpeechService.getTextToSpeech(assistant, text);
            url = fileStore.cache(audio, assistantId, FileStore.MediaType.MP3, text);
        }
        log.debug("Done getting audio for {}", url);
        return url;
    }

    private byte[] getVideo(Assistant assistant, String answer, String audioUrl, boolean cache) throws Exception {
        if (!fileStore.exists(assistant.getAssistantId(), FileStore.MediaType.MP4, answer)) {
            log.debug("Generating new video for {}", assistant.getName());
            byte[] video = avatarService.getVideo(assistant, new URL(audioUrl));
            if (cache) {
                fileStore.cache(video, assistant.getAssistantId(), FileStore.MediaType.MP4, answer);
            } else {
                fileStore.save(video, assistant.getAssistantId(), FileStore.MediaType.MP4, answer);
            }
            return video;
        } else {
            byte[] video = fileStore.get(assistant.getAssistantId(), FileStore.MediaType.MP4, answer);
            log.debug("Done getting existing video for {}", assistant.getName());
            return video;
        }
    }

    @PostMapping(value = "/addAssistant")
    private Assistant addAssistant(@RequestParam("assistantName") String assistantName, @RequestParam("role") String role,
                                   @RequestParam(required = false, name = "audioSample") MultipartFile audioSample, @RequestParam(required = false, name = "voiceUpload") MultipartFile voiceUpload, @RequestParam("profilePicture") MultipartFile profilePicture) throws Exception {
        String name = assistantName.trim();
        log.debug("Adding assistant {}", name);

        Assistant assistant = new Assistant();
        assistant.setDescription(role);
        assistant.setInstructions(role);
        assistant.setName(name);
        assistant.setAvatarId(assistant.getName().toLowerCase());
        assistantService.save(assistant);

        byte[] audio;
        if (audioSample != null && !audioSample.isEmpty()) {
            audio = audioSample.getBytes();
        } else {
            audio = voiceUpload.getBytes();
        }
        String profilePicUrl = fileStore.save(profilePicture.getBytes(), assistant.getAssistantId(), FileStore.MediaType.JPG);
        String voiceId = textToSpeechService.save(assistant.getAssistantId(), role, audio);
        assistant.setVoiceId(voiceId);
        assistant.setProfilePicture(profilePicUrl);
        assistantService.update(assistant);
        availableAssistants.put(assistant.getAssistantId(), assistant);
        return assistant;
    }

    @RequestMapping(value = "/listAvailableModes")
    public List<Modes> getAvailableModes() {
        List<Modes> modes = new ArrayList<>();
        modes.add(Modes.Text);
        if (avatarService.creditsRemaining() > 0) {
            modes.add(Modes.Video);
        }
        if (textToSpeechService.creditsRemaining() > 0) {
            modes.add(Modes.Voice);
        }
        return modes;
    }

    @RequestMapping(value = "/listAssistants")
    private Collection<Assistant> getAssistants(boolean refresh) {
        if (refresh || availableAssistants.isEmpty()) {
            availableAssistants.clear();
            Collection<Assistant> assistants = assistantService.getAssistants();
            for (Assistant assistant : assistants) {
                log.debug("Initializing assistant {}", assistant.getName());
                loadIdleVideo(assistant);
                loadIntroVideos(assistant);
                loadSmallTalkVideos(assistant);
                loadStartingVideo(assistant);
                availableAssistants.put(assistant.getAssistantId(), assistant);
            }
        }
        List<Assistant> results = new ArrayList<>(availableAssistants.values());
        results.sort(Comparator.comparing(Assistant::getName));
        return results;
    }

    @RequestMapping(value = "/listMoods")
    public Moods[] listMoods() {
        return Moods.values();
    }

    @Scheduled(fixedRate = 2000)
    public void getPersonaVideos() {
        for (Assistant assistant : availableAssistants.values()) {
            if (isPersonaAvailableToAnimate(assistant)) {
                createIdleVideo(assistant);
                createStartingVideos(assistant);
                createSmallTalkVideos(assistant);
                createIntroVideos(assistant);
            } else {
                log.trace("Persona can't be animated because the profile image doesn't exist: {}", assistant);
            }
        }
    }

    private void createIdleVideo(Assistant assistant) {
        try {
            if (assistant.getIdleVideo() == null) {
                String idleVideoUrl = null;
                String person = assistant.getAssistantId();
                if (!fileStore.exists(assistant.getName(), FileStore.MediaType.MP4)) {
                    log.debug("Creating idle video for {}", person);
                    byte[] video = avatarService.getVideo(assistant, "<break time=\"20000ms\"/>");
                    idleVideoUrl = fileStore.save(video, person, FileStore.MediaType.MP4);
                } else if (assistant.getIdleVideo() == null) {
                    idleVideoUrl = fileStore.getUrl(person, FileStore.MediaType.MP4);
                }
                assistant.setIdleVideo(idleVideoUrl);
            }
        } catch (Exception ex) {
            log.warn("Unable to generate idle video for {}", assistant, ex);
        }
    }

    private byte[] getVideo(String prompt, Moods[] moods, Assistant assistant) throws Exception {
        assistant.setCurrentMood(Moods.getRandomMood(moods));
        AssistantResponse assistantResponse = getHumanizedAnswerAsText(prompt, assistant);
        String answer = assistantResponse.getResponse();
        String audioUrl = getTextToSpeech(assistant.getAssistantId(), answer);
        return getVideo(assistant, answer, audioUrl, false);
    }

    private void createStartingVideos(Assistant assistant) {
        List<String> startingVideos = assistant.getStartingVideos();
        for (int i = startingVideos.size() + 1; i <= startingNumber; i++) {
            try {
                String suffix = "-starting" + i;
                if (startingVideos.stream().noneMatch(s -> s.contains(suffix))) {
                    String fileName = assistant.getAssistantId() + suffix;
                    log.debug("Creating starting talk {}", fileName);
                    Moods[] moods = {Moods.Anticipation, Moods.Sarcastic, Moods.Enthusiasm};
                    byte[] video = getVideo(startingPrompt, moods, assistant);
                    fileStore.save(video, fileName, FileStore.MediaType.MP4);
                    startingVideos.add(fileStore.getUrl(fileName, FileStore.MediaType.MP4));
                }
            } catch (Exception ex) {
                log.warn("Unable to generate starting talk for {}", assistant, ex);
            }
        }
    }

    private void createSmallTalkVideos(Assistant assistant) {
        List<String> smallTalkVideos = assistant.getSmallTalkVideos();
        for (int i = smallTalkVideos.size() + 1; i <= smallTalkNumber; i++) {
            try {
                String suffix = "-smalltalk" + i;
                if (smallTalkVideos.stream().noneMatch(s -> s.contains(suffix))) {
                    String fileName = assistant.getAssistantId() + suffix;
                    log.debug("Creating small talk {}", fileName);
                    byte[] video = getVideo(smallTalkPrompt, new Moods[]{Moods.Enthusiasm}, assistant);
                    fileStore.save(video, fileName, FileStore.MediaType.MP4);
                    smallTalkVideos.add(fileStore.getUrl(fileName, FileStore.MediaType.MP4));
                }
            } catch (Exception ex) {
                log.warn("Unable to generate small talk for {}", assistant, ex);
            }
        }
    }

    private void createIntroVideos(Assistant assistant) {
        List<String> introVideos = assistant.getIntroVideos();
        for (int i = introVideos.size() + 1; i <= introNumber; i++) {
            try {
                String suffix = "-intro" + i;
                if (introVideos.stream().noneMatch(s -> s.contains(suffix))) {
                    String fileName = assistant.getAssistantId() + suffix;
                    log.debug("Creating intro {}", fileName);
                    Moods[] moods = {Moods.Sarcastic, Moods.Enthusiasm};
                    byte[] video = getVideo(introPrompt, moods, assistant);
                    fileStore.save(video, fileName, FileStore.MediaType.MP4);
                    introVideos.add(fileStore.getUrl(fileName, FileStore.MediaType.MP4));
                }
            } catch (Exception ex) {
                log.warn("Unable to generate intro for {}", assistant, ex);
            }
        }
    }

    private void loadSmallTalkVideos(Assistant assistant) {
        assistant.getSmallTalkVideos().clear();
        for (int i = 1; i <= smallTalkNumber; i++) {
            String fileName = assistant.getAssistantId() + "-smalltalk" + i;
            if (fileStore.exists(fileName, FileStore.MediaType.MP4)) {
                assistant.getSmallTalkVideos().add(fileStore.getUrl(fileName, FileStore.MediaType.MP4));
            }
        }
    }

    private void loadStartingVideo(Assistant assistant) {
        assistant.getStartingVideos().clear();
        for (int i = 1; i <= startingNumber; i++) {
            String fileName = assistant.getAssistantId() + "-starting" + i;
            if (fileStore.exists(fileName, FileStore.MediaType.MP4)) {
                assistant.getStartingVideos().add(fileStore.getUrl(fileName, FileStore.MediaType.MP4));
            }
        }
    }

    private void loadIntroVideos(Assistant assistant) {
        assistant.getIntroVideos().clear();
        for (int i = 1; i <= introNumber; i++) {
            String fileName = assistant.getAssistantId() + "-intro" + i;
            if (fileStore.exists(fileName, FileStore.MediaType.MP4)) {
                assistant.getIntroVideos().add(fileStore.getUrl(fileName, FileStore.MediaType.MP4));
            }
        }
    }

    private void loadIdleVideo(Assistant assistant) {
        String idleVideoName = assistant.getAssistantId();
        if (fileStore.exists(idleVideoName, FileStore.MediaType.MP4)) {
            assistant.setIdleVideo(fileStore.getUrl(idleVideoName, FileStore.MediaType.MP4));
        }
    }

    private boolean isPersonaAvailableToAnimate(Assistant assistant) {
        return fileStore.exists(assistant.getName(), FileStore.MediaType.JPG);
    }

    @PostConstruct
    public void loadPersonas() {
        getAssistants(true);
    }

    @RequestMapping(value = "/auth/stream", produces = "application/json")
    public Event getStream(String cameraId) throws Exception {
        Optional<Camera> match = cameraService.getCameras().stream().filter(camera -> camera.getId().equals(cameraId)).findFirst();
        if (match.isPresent()) {
            Camera camera = match.get();
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime roundedDownToMinute = now.truncatedTo(ChronoUnit.MINUTES);
            long minutesToSubtract = roundedDownToMinute.getMinute() % 5;
            LocalDateTime roundedDownToFiveMinutes = roundedDownToMinute.minusMinutes(minutesToSubtract);

            LocalDateTime timeLastAccessed = lastAccessed.put(camera, roundedDownToFiveMinutes);
            if (timeLastAccessed == null || roundedDownToFiveMinutes.isAfter(timeLastAccessed)) {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd:HH:mm");
                String bufferedTime = roundedDownToFiveMinutes.format(formatter);

                Event existingEvent = eventService.getEvent(cameraId, bufferedTime);
                if (existingEvent != null) {
                    log.debug("Returning recent event: {}", existingEvent);
                    return existingEvent;
                } else {
                    log.debug("Processing event for camera: {}", camera);
                    Event event = new Event();
                    event.setCamera(camera);
                    event.setEventDateTime(now);
                    event.setEventId(bufferedTime);
                    String url = cameraService.getStream(camera, event.getEventId());
                    if (url != null) {
                        event.setVideoUrl(url);
                        String narration = answerService.narrate(event);
                        event.setNarration(narration);
                        eventService.save(event);
                        log.info("Completed processing event: {}", event);
                    } else {
                        log.debug("Unable to create stream for camera {}", camera);
                    }
                    return event;
                }
            } else {
                log.debug("Camera was accessed in the last 5 minutes - {}.  Ignoring event", timeLastAccessed);
            }
        } else {
            log.debug("No matching camera found for {}", cameraId);
        }
        return null;
    }

    @RequestMapping(value = "/auth/cameras", produces = "application/json")
    public List<Camera> getCameras(@RegisteredOAuth2AuthorizedClient("google") OAuth2AuthorizedClient authorizedClient) {
        return cameraService.getCameras();
    }

    @RequestMapping(value = "/events", produces = "application/json")
    public List<Event> getEvents() {
        return eventService.getAllEvents();
    }

    @RequestMapping(value = "/narrate", produces = "application/json")
    public AssistantResponse narrateEvent(String cameraId, String eventId, String assistantId, @RequestParam(required = false) Moods mood) throws Exception {
        Event event = eventService.getEvent(cameraId, eventId);
        if(event != null) {
            AssistantResponse response = getAnswer(narrationAssistant + " {" + event.getNarration() + "}", assistantId, mood);
            response.setTrigger(event);
            return response;
        } else {
            log.debug("Event no longer exists: {}", eventId);
            return null;
        }
    }

    @RequestMapping(value = "/pubsub/event")
    public ResponseEntity<?> receiveMessage(HttpServletRequest req) {
        try {
            JsonNode bodyNode = objectMapper.readTree(req.getReader());
            if (bodyNode != null && bodyNode.get("message") != null && bodyNode.get("message").get("data") != null) {
                String base64Data = bodyNode.get("message")
                        .get("data").asText();
                String cameraInfo = new String(Base64.getDecoder().decode(base64Data));
                log.debug("Received event: " + cameraInfo);
                JsonNode cameraNode = objectMapper.readTree(cameraInfo);
                String utcTimestamp = cameraNode.path("timestamp").asText();
                Instant timestampInstant = Instant.parse(utcTimestamp);
                long minutesDifference = ChronoUnit.MINUTES.between(timestampInstant, Instant.now());
                if (minutesDifference < 5) {
                    JsonNode resourceUpdateNode = cameraNode.path("resourceUpdate");
                    String resource = resourceUpdateNode.path("name").asText();
                    getStream(resource);
                } else {
                    log.debug("Skipped processing since event occurred {} minutes ago", minutesDifference);
                }
            }
        } catch (Exception ex) {
            log.info("Unable to process event", ex);
        }
        return new ResponseEntity<>(HttpStatus.OK);
    }

    @Bean
    public OAuth2AuthorizedClientManager authorizedClientManager(
            ClientRegistrationRepository clientRegistrationRepository,
            OAuth2AuthorizedClientRepository authorizedClientRepository) {

        OAuth2AuthorizedClientProvider authorizedClientProvider =
                OAuth2AuthorizedClientProviderBuilder.builder()
                        .authorizationCode()
                        .refreshToken()
                        .build();

        DefaultOAuth2AuthorizedClientManager authorizedClientManager =
                new DefaultOAuth2AuthorizedClientManager(
                        clientRegistrationRepository, authorizedClientRepository);
        authorizedClientManager.setAuthorizedClientProvider(authorizedClientProvider);

        return authorizedClientManager;
    }

    @Value("${starting.prompt}")
    private String startingPrompt;
    @Value("${intro.prompt}")
    private String introPrompt;
    @Value("${smalltalk.prompt}")
    private String smallTalkPrompt;
    @Value("${smalltalk.number}")
    private int smallTalkNumber;
    @Value("${starting.number}")
    private int startingNumber;
    @Value("${intro.number}")
    private int introNumber;
    @Value("${narration.assistant}")
    private String narrationAssistant;
    private final ObjectMapper objectMapper;

    private final Map<String, Assistant> availableAssistants = new HashMap<>();
    private final FileStore fileStore;
    private final TextToSpeechService textToSpeechService;
    private final AssistantService assistantService;
    private final AnswerService answerService;
    private final AvatarService avatarService;
    private final CameraService cameraService;
    private final EventService eventService;
    private final Map<Camera, LocalDateTime> lastAccessed = new HashMap<>();

    private static final Logger log = LoggerFactory.getLogger(VirtualAssistantApplication.class);

}
