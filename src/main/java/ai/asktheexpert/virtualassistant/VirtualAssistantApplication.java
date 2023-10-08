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
import org.springframework.cache.CacheManager;
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

    public VirtualAssistantApplication(FileStore fileStore, ElevenLabsService textToSpeechService, PersonaService personaService, AnswerService answerService, AvatarService avatarService, CacheManager cacheManager) {
        this.fileStore = fileStore;
        this.textToSpeechService = textToSpeechService;
        this.personaService = personaService;
        this.answerService = answerService;
        this.avatarService = avatarService;
        this.cacheManager = cacheManager;
    }

    public static void main(String[] args) {
        SpringApplication.run(VirtualAssistantApplication.class, args);
    }

    @RequestMapping(value = "/askText")
    public String getHumanizedAnswerAsText(String question, @RequestParam(required = false) Persona persona, @RequestParam(required = false, defaultValue = "false") boolean ignoreCache) {
        if (persona == null) {
            persona = availablePersonas.values().iterator().next();
            persona.setCurrentMood(Moods.getRandomMood());
        }
        String answer = answerService.answer(question, persona);
        return answer;
    }

    @RequestMapping(value = "/askWithDetails")
    public AssistantResponse getAnswer(String question, @RequestParam(defaultValue = "evan") String person, @RequestParam(required = false) Moods mood, @RequestParam(required = false, defaultValue = "false") boolean ignoreCache) throws Exception {
        Persona persona = availablePersonas.get(person.toLowerCase());
        persona.setCurrentMood(mood);

        log.debug("Getting answer for {}", persona);
        String answer = getHumanizedAnswerAsText(question, persona, ignoreCache);
        byte[] audio = getTextToSpeech(person, answer);

        AssistantResponse response = new AssistantResponse();
        response.setPerson(person);
        response.setMood(mood);
        response.setOriginalQuestion(question);
        response.setResponse(answer);
        response.setAudio(audio);
        try {
            byte[] video = getVideo(persona, answer, audio);
            response.setVideo(video);
        } catch (Exception ex) {
            log.error("Failed to generate video", ex);
        }
        log.debug("Completed getting details");
        return response;
    }

    @RequestMapping(value = "/tts", produces = "audio/mpeg")
    public byte[] getTextToSpeech(@RequestParam(defaultValue = "evan") String person, @RequestParam String text) throws Exception {
        Persona persona = availablePersonas.get(person.toLowerCase());
        return textToSpeechService.getTextToSpeech(persona, text);
    }

    public byte[] getVideo(Persona persona, String text, byte[] audio) throws Exception {
        String key = (persona.getName() + ":" + text).toLowerCase();
        String audioUrl = fileStore.save((persona.getName().toLowerCase() + "-" + key.hashCode() + ".mp3"), audio);
        URL url = new URL(audioUrl);
        byte[] video = avatarService.getVideo(persona, url);
        log.debug("Done getting video for {}", key);
        return video;
    }

    @RequestMapping(value = "/generateIdleVideo")
    public String createIdleVideo(String person, boolean forceRefresh) throws Exception {
        Persona persona = availablePersonas.get(person.toLowerCase());
        if (forceRefresh || persona.getIdleVideoUrl() == null || !FileStore.existsAtUrl(persona.getIdleVideoUrl())) {
            return createIdleVideo(persona);
        }
        return null;
    }

    public String createIdleVideo(Persona persona) throws Exception {
        String idleVideoUrl = null;
        String person = persona.getName().toLowerCase();
        String videoPath = person.toLowerCase() + ".mp4";
        if (!fileStore.exists(videoPath)) {
            log.debug("Creating idle video for {}", person);
            byte[] video = avatarService.getVideo(persona, "<break time=\"20000ms\"/>");
            idleVideoUrl = fileStore.save(videoPath, video);
        } else if (persona.getIdleVideoUrl() == null) {
            idleVideoUrl = fileStore.getUrl(videoPath);
        }
        persona.setIdleVideoUrl(idleVideoUrl);
        return idleVideoUrl;
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
                try {
                    if (persona.getIdleVideoUrl() == null) {
                        persona.setIdleVideoUrl(createIdleVideo(persona));
                    }
                } catch (Exception ex) {
                    log.warn("Unable to generate idle video for {}", persona, ex);
                    persona.setIdleVideoStatus("error");
                }
                List<String> startingVideos = persona.getStartingVideos();
                for (int i = startingVideos.size() + 1; i <= startingNumber; i++) {
                    try {
                        String fileName = persona.getName().toLowerCase() + "-starting" + i + ".mp4";
                        if (!startingVideos.stream().anyMatch(s -> s.endsWith(fileName))) {
                            log.debug("Creating starting talk {}", fileName);
                            Moods[] moods = {Moods.Anticipation, Moods.Sarcastic, Moods.Love, Moods.Pride, Moods.Enthusiasm};
                            AssistantResponse assistantResponse = getAnswer(startingPrompt, persona.getName(), Moods.getRandomMood(moods), true);
                            fileStore.save(fileName, assistantResponse.getVideo());
                            startingVideos.add(fileStore.getUrl(fileName));
                        }
                    } catch (Exception ex) {
                        log.warn("Unable to generate starting talk for {}", persona, ex);
                    }
                }
                List<String> smallTalkVideos = persona.getSmallTalkVideos();
                for (int i = smallTalkVideos.size() + 1; i <= smallTalkNumber; i++) {
                    try {
                        String fileName = persona.getName().toLowerCase() + "-smalltalk" + i + ".mp4";
                        if (!smallTalkVideos.stream().anyMatch(s -> s.endsWith(fileName))) {
                            log.debug("Creating small talk {}", fileName);
                            AssistantResponse assistantResponse = getAnswer(smallTalkPrompt, persona.getName(), Moods.Enthusiasm, true);
                            fileStore.save(fileName, assistantResponse.getVideo());
                            smallTalkVideos.add(fileStore.getUrl(fileName));
                        }
                    } catch (Exception ex) {
                        log.warn("Unable to generate small talk for {}", persona, ex);
                    }
                }
                List<String> introVideos = persona.getIntroVideos();
                for (int i = introVideos.size() + 1; i <= introNumber; i++) {
                    try {
                        String fileName = persona.getName().toLowerCase() + "-intro" + i + ".mp4";
                        if (!introVideos.stream().anyMatch(s -> s.endsWith(fileName))) {
                            log.debug("Creating intro {}", fileName);
                            Moods[] moods = {Moods.Sarcastic, Moods.Love, Moods.Pride, Moods.Enthusiasm};
                            AssistantResponse assistantResponse = getAnswer(introPrompt, persona.getName(), Moods.getRandomMood(moods), true);
                            fileStore.save(fileName, assistantResponse.getVideo());
                            introVideos.add(fileStore.getUrl(fileName));
                        }
                    } catch (Exception ex) {
                        log.warn("Unable to generate intro for {}", persona, ex);
                    }
                }
            } else {
                log.trace("Persona can't be animated because the profile image doesn't exist: {}", persona);
            }
        }
    }

    private void loadSmallTalkVideos(Persona persona) {
        persona.getSmallTalkVideos().clear();
        for (int i = 1; i <= smallTalkNumber; i++) {
            String fileName = persona.getName().toLowerCase() + "-smalltalk" + i + ".mp4";
            if (fileStore.exists(fileName)) {
                persona.getSmallTalkVideos().add(fileStore.getUrl(fileName));
            }
        }
    }

    private void loadStartingVideo(Persona persona) {
        persona.getStartingVideos().clear();
        for (int i = 1; i <= startingNumber; i++) {
            String fileName = persona.getName().toLowerCase() + "-starting" + i + ".mp4";
            if (fileStore.exists(fileName)) {
                persona.getStartingVideos().add(fileStore.getUrl(fileName));
            }
        }
    }

    private void loadIntroVideos(Persona persona) {
        persona.getIntroVideos().clear();
        for (int i = 1; i <= introNumber; i++) {
            String fileName = persona.getName().toLowerCase() + "-intro" + i + ".mp4";
            if (fileStore.exists(fileName)) {
                persona.getIntroVideos().add(fileStore.getUrl(fileName));
            }
        }
    }

    private void loadIdleVideo(Persona persona) {
        persona.setIdleVideoUrl(null);
        String idleVideoName = persona.getName().toLowerCase() + ".mp4";
        if (fileStore.exists(idleVideoName)) {
            persona.setIdleVideoUrl(fileStore.getUrl(idleVideoName));
            persona.setIdleVideoStatus("done");
        }
    }

    private boolean isPersonaAvailableToAnimate(Persona persona) {
        return fileStore.exists(persona.getAvatarId());
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
    private final CacheManager cacheManager;

    private static final Logger log = LoggerFactory.getLogger(VirtualAssistantApplication.class);

}
