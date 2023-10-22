package ai.asktheexpert.virtualassistant;

import ai.asktheexpert.virtualassistant.models.AssistantResponse;
import ai.asktheexpert.virtualassistant.models.Modes;
import ai.asktheexpert.virtualassistant.models.Moods;
import ai.asktheexpert.virtualassistant.models.Persona;
import ai.asktheexpert.virtualassistant.repositories.FileStore;
import ai.asktheexpert.virtualassistant.services.*;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.net.URL;
import java.util.*;

@SpringBootApplication
@RestController
@EnableScheduling
@EnableCaching
public class VirtualAssistantApplication {

    public VirtualAssistantApplication(FileStore fileStore, ElevenLabsService textToSpeechService, PersonaService personaService, AnswerService answerService, AvatarService avatarService) {
        this.fileStore = fileStore;
        this.textToSpeechService = textToSpeechService;
        this.personaService = personaService;
        this.answerService = answerService;
        this.avatarService = avatarService;
    }

    public static void main(String[] args) {
        SpringApplication.run(VirtualAssistantApplication.class, args);
    }

    @RequestMapping(value = "/askText")
    public String getHumanizedAnswerAsText(String question, @RequestParam(required = false) Persona persona) {
        if (persona == null) {
            persona = availablePersonas.values().iterator().next();
            persona.setCurrentMood(Moods.getRandomMood());
        }
        return answerService.answer(question, persona);
    }

    @RequestMapping(value = "/askWithDetails")
    public AssistantResponse getAnswer(String question, @RequestParam(defaultValue = "evan") String person, @RequestParam(required = false) Moods mood) throws Exception {
        Persona persona = availablePersonas.get(person.toLowerCase());
        persona.setCurrentMood(mood);

        log.debug("Getting answer for {}", persona);
        String answer = getHumanizedAnswerAsText(question, persona);

        AssistantResponse response = new AssistantResponse();
        response.setPerson(person);
        response.setMood(mood);
        response.setOriginalQuestion(question);
        response.setResponse(answer);

        String audioUrl = getTextToSpeech(person, answer);
        response.setAudioUrl(audioUrl);
        try {
            getVideo(persona, answer, audioUrl);
            String videoUrl = fileStore.getUrl(persona.getName(), FileStore.MediaType.MP4, answer);
            response.setVideoUrl(videoUrl);
        } catch (Exception ex) {
            log.error("Failed to generate video", ex);
        }
        log.debug("Completed getting details");
        return response;
    }

    @RequestMapping(value = "/tts", produces = "audio/mpeg")
    public String getTextToSpeech(@RequestParam(defaultValue = "evan") String person, @RequestParam String text) throws Exception {
        Persona persona = availablePersonas.get(person.toLowerCase());
        String url = fileStore.getUrl(persona.getName(), FileStore.MediaType.MP3, text);
        if (url == null) {
            byte[] audio = textToSpeechService.getTextToSpeech(persona, text);
            url = fileStore.cache(audio, persona.getName(), FileStore.MediaType.MP3, text);
        }
        log.debug("Done getting audio for {}", url);
        return url;
    }

    private byte[] getVideo(Persona persona, String answer, String audioUrl) throws Exception {
        if (!fileStore.exists(persona.getName(), FileStore.MediaType.MP4, answer)) {
            log.debug("Generating new video for {}", persona.getName());
            byte[] video = avatarService.getVideo(persona, new URL(audioUrl));
            fileStore.save(video, persona.getName(), FileStore.MediaType.MP4, answer);
            return video;
        } else {
            byte[] video = fileStore.get(persona.getName(), FileStore.MediaType.MP4, answer);
            log.debug("Done getting existing video for {}", persona.getName());
            return video;
        }
    }

    @RequestMapping(value = "/generateIdleVideo")
    public String createIdleVideo(String person, boolean forceRefresh) {
        Persona persona = availablePersonas.get(person.toLowerCase());
        if (forceRefresh || persona.getIdleVideoUrl() == null || !FileStore.existsAtUrl(persona.getIdleVideoUrl())) {
            createIdleVideo(persona);
        }
        return persona.getIdleVideoUrl();
    }

    @PostMapping(value = "/addPersona")
    private Persona addPersona(@RequestParam("personaName") String personaName, @RequestParam("personaRole") String personaRole,
                               @RequestParam(required = false, name = "audioSample") MultipartFile audioSample, @RequestParam(required = false, name = "voiceUpload") MultipartFile voiceUpload, @RequestParam("profilePicture") MultipartFile profilePicture) throws Exception {
        String name = personaName.trim();
        byte[] audio;
        if (audioSample != null && !audioSample.isEmpty()) {
            audio = audioSample.getBytes();
        } else {
            audio = voiceUpload.getBytes();
        }
        Persona persona = personaService.addPersona(name, personaRole, audio, profilePicture.getBytes());
        availablePersonas.put(name.toLowerCase(), persona);
        return persona;
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

    @RequestMapping(value = "/listPersonas")
    private Collection<Persona> getPersonas(boolean refresh) {
        if (refresh || availablePersonas.isEmpty()) {
            availablePersonas.clear();
            Collection<Persona> personas = personaService.getPersonas();
            for (Persona persona : personas) {
                loadIdleVideo(persona);
                loadIntroVideos(persona);
                loadSmallTalkVideos(persona);
                loadStartingVideo(persona);
                availablePersonas.put(persona.getName().toLowerCase(), persona);
            }
        }
        return availablePersonas.values();
    }

    @RequestMapping(value = "/listMoods")
    public Moods[] listMoods() {
        return Moods.values();
    }

    @Scheduled(fixedRate = 2000)
    public void getPersonaVideos() {
        for (Persona persona : availablePersonas.values()) {
            if (isPersonaAvailableToAnimate(persona)) {
                createIdleVideo(persona);
                createStartingVideos(persona);
                createSmallTalkVideos(persona);
                createIntroVideos(persona);
            } else {
                log.trace("Persona can't be animated because the profile image doesn't exist: {}", persona);
            }
        }
    }

    private void createIdleVideo(Persona persona) {
        try {
            if (persona.getIdleVideoUrl() == null) {
                String idleVideoUrl = null;
                String person = persona.getName().toLowerCase();
                if (!fileStore.exists(persona.getName(), FileStore.MediaType.MP4)) {
                    log.debug("Creating idle video for {}", person);
                    byte[] video = avatarService.getVideo(persona, "<break time=\"20000ms\"/>");
                    idleVideoUrl = fileStore.save(video, persona.getName(), FileStore.MediaType.MP4);
                } else if (persona.getIdleVideoUrl() == null) {
                    idleVideoUrl = fileStore.getUrl(persona.getName(), FileStore.MediaType.MP4);
                }
                persona.setIdleVideoUrl(idleVideoUrl);
            }
        } catch (Exception ex) {
            log.warn("Unable to generate idle video for {}", persona, ex);
            persona.setIdleVideoStatus("error");
        }
    }

    private byte[] getVideo(String prompt, Moods[] moods, Persona persona) throws Exception {
        persona.setCurrentMood(Moods.getRandomMood(moods));
        String answer = getHumanizedAnswerAsText(prompt, persona);
        String audioUrl = getTextToSpeech(persona.getName(), answer);
        return getVideo(persona, answer, audioUrl);
    }

    private void createStartingVideos(Persona persona) {
        List<String> startingVideos = persona.getStartingVideos();
        for (int i = startingVideos.size() + 1; i <= startingNumber; i++) {
            try {
                String suffix = "-starting" + i;
                if (startingVideos.stream().noneMatch(s -> s.contains(suffix))) {
                    String fileName = persona.getName().toLowerCase() + suffix;
                    log.debug("Creating starting talk {}", fileName);
                    Moods[] moods = {Moods.Anticipation, Moods.Sarcastic, Moods.Enthusiasm};
                    byte[] video = getVideo(startingPrompt, moods, persona);
                    fileStore.save(video, fileName, FileStore.MediaType.MP4);
                    startingVideos.add(fileStore.getUrl(fileName, FileStore.MediaType.MP4));
                }
            } catch (Exception ex) {
                log.warn("Unable to generate starting talk for {}", persona, ex);
            }
        }
    }

    private void createSmallTalkVideos(Persona persona) {
        List<String> smallTalkVideos = persona.getSmallTalkVideos();
        for (int i = smallTalkVideos.size() + 1; i <= smallTalkNumber; i++) {
            try {
                String suffix = "-smalltalk" + i;
                if (smallTalkVideos.stream().noneMatch(s -> s.contains(suffix))) {
                    String fileName = persona.getName().toLowerCase() + suffix;
                    log.debug("Creating small talk {}", fileName);
                    byte[] video = getVideo(smallTalkPrompt, new Moods[]{Moods.Enthusiasm}, persona);
                    fileStore.save(video, fileName, FileStore.MediaType.MP4);
                    smallTalkVideos.add(fileStore.getUrl(fileName, FileStore.MediaType.MP4));
                }
            } catch (Exception ex) {
                log.warn("Unable to generate small talk for {}", persona, ex);
            }
        }
    }

    private void createIntroVideos(Persona persona) {
        List<String> introVideos = persona.getIntroVideos();
        for (int i = introVideos.size() + 1; i <= introNumber; i++) {
            try {
                String suffix = "-intro" + i;
                if (introVideos.stream().noneMatch(s -> s.contains(suffix))) {
                    String fileName = persona.getName().toLowerCase() + suffix;
                    log.debug("Creating intro {}", fileName);
                    Moods[] moods = {Moods.Sarcastic, Moods.Enthusiasm};
                    byte[] video = getVideo(introPrompt, moods, persona);
                    fileStore.save(video, fileName, FileStore.MediaType.MP4);
                    introVideos.add(fileStore.getUrl(fileName, FileStore.MediaType.MP4));
                }
            } catch (Exception ex) {
                log.warn("Unable to generate intro for {}", persona, ex);
            }
        }
    }

    private void loadSmallTalkVideos(Persona persona) {
        persona.getSmallTalkVideos().clear();
        for (int i = 1; i <= smallTalkNumber; i++) {
            String fileName = persona.getName().toLowerCase() + "-smalltalk" + i;
            if (fileStore.exists(fileName, FileStore.MediaType.MP4)) {
                persona.getSmallTalkVideos().add(fileStore.getUrl(fileName, FileStore.MediaType.MP4));
            }
        }
    }

    private void loadStartingVideo(Persona persona) {
        persona.getStartingVideos().clear();
        for (int i = 1; i <= startingNumber; i++) {
            String fileName = persona.getName().toLowerCase() + "-starting" + i;
            if (fileStore.exists(fileName, FileStore.MediaType.MP4)) {
                persona.getStartingVideos().add(fileStore.getUrl(fileName, FileStore.MediaType.MP4));
            }
        }
    }

    private void loadIntroVideos(Persona persona) {
        persona.getIntroVideos().clear();
        for (int i = 1; i <= introNumber; i++) {
            String fileName = persona.getName().toLowerCase() + "-intro" + i;
            if (fileStore.exists(fileName, FileStore.MediaType.MP4)) {
                persona.getIntroVideos().add(fileStore.getUrl(fileName, FileStore.MediaType.MP4));
            }
        }
    }

    private void loadIdleVideo(Persona persona) {
        persona.setIdleVideoUrl(null);
        String idleVideoName = persona.getName().toLowerCase();
        if (fileStore.exists(idleVideoName, FileStore.MediaType.MP4)) {
            persona.setIdleVideoUrl(fileStore.getUrl(idleVideoName, FileStore.MediaType.MP4));
            persona.setIdleVideoStatus("done");
        }
    }

    private boolean isPersonaAvailableToAnimate(Persona persona) {
        return fileStore.exists(persona.getName(), FileStore.MediaType.JPG);
    }

    @PostConstruct
    public void loadPersonas() {
        getPersonas(true);
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


    private final Map<String, Persona> availablePersonas = new TreeMap<>();
    private final FileStore fileStore;
    private final TextToSpeechService textToSpeechService;
    private final PersonaService personaService;
    private final AnswerService answerService;
    private final AvatarService avatarService;

    private static final Logger log = LoggerFactory.getLogger(VirtualAssistantApplication.class);

}
