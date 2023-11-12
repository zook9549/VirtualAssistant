package ai.asktheexpert.virtualassistant.services;

import ai.asktheexpert.virtualassistant.models.Assistant;
import ai.asktheexpert.virtualassistant.models.AssistantResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
public class ChatGPTService implements AnswerService, AssistantService {

    @Override
    public Collection<Assistant> getAssistants() {
        HttpHeaders headers = createHeaders();
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(headers);

        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<Map> response = restTemplate.exchange(chatGptUrl + "/assistants", HttpMethod.GET, entity, Map.class);
        List<Map> results = (List<Map>) response.getBody().get("data");

        List<Assistant> assistants = new ArrayList<>();
        for (Map result : results) {
            assistants.add(createAssistantFromMap(result));
        }
        assistants.sort(Comparator.comparing(Assistant::getName));
        return assistants;
    }

    public String save(Assistant assistant) {
        HttpEntity<Map<String, Object>> entity = createAssistantEntity(assistant);

        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<Map> response = restTemplate.exchange(chatGptUrl + "/assistants", HttpMethod.POST, entity, Map.class);

        Map responseBody = response.getBody();
        String id = responseBody.get("id").toString();
        assistant.setAssistantId(id);
        log.debug("Completed adding assistant: {}", id);
        return id;
    }

    public void update(Assistant assistant) {
        HttpEntity<Map<String, Object>> entity = createAssistantEntity(assistant);

        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<Map> response = restTemplate.exchange(chatGptUrl + "/assistants/" + assistant.getAssistantId(), HttpMethod.POST, entity, Map.class);

        Map responseBody = response.getBody();
        String id = responseBody.get("id").toString();
        assistant.setAssistantId(id);
        log.debug("Completed updating assistant: {}", id);
    }

    private HttpEntity<Map<String, Object>> createAssistantEntity(Assistant assistant) {
        HttpHeaders headers = createHeaders();

        HashMap<String, Object> requestBody = new HashMap<>();
        requestBody.put("name", assistant.getName());
        requestBody.put("instructions", assistant.getInstructions());
        requestBody.put("description", assistant.getDescription());
        requestBody.put("model", "gpt-4-1106-preview");

        Map<String, String> metaData = new HashMap<>();
        metaData.put("voiceId", assistant.getVoiceId());
        metaData.put("avatarId", assistant.getAvatarId());
        metaData.put("profilePicture", assistant.getProfilePicture());
        if (assistant.getIdleVideo() != null) {
            metaData.put("idleVideo", assistant.getIdleVideo());
        }
        if (!assistant.getStartingVideos().isEmpty()) {
            metaData.put("startingVideos", String.join(";", assistant.getStartingVideos()));
        }
        if (!assistant.getIntroVideos().isEmpty()) {
            metaData.put("introVideos", String.join(";", assistant.getIntroVideos()));
        }
        if (!assistant.getSmallTalkVideos().isEmpty()) {
            metaData.put("smallTalkVideos", String.join(";", assistant.getSmallTalkVideos()));
        }
        requestBody.put("metadata", metaData);

        return new HttpEntity<>(requestBody, headers);
    }

    @Cacheable(value = "chat", key = "#assistant.assistantId + #assistant.currentMood + #question")
    public AssistantResponse answer(String question, Assistant assistant) {
        log.debug("Asking {}", question);


        HttpHeaders headers = createHeaders();
        HttpEntity<Map<String, Object>> entity = null;
        Map<String, Object> message = createMessage(question);
        RestTemplate restTemplate = new RestTemplate();
        String engineeredPrompt = createEngineeredPrompt(assistant);
        log.debug("Fetching answer with prompt: {}", engineeredPrompt);

        LinkedHashMap<String, List<AssistantResponse>> threads = assistant.getThreads();
        List<AssistantResponse> responses = null;
        String threadId = threads.isEmpty() ? null : new ArrayList<>(threads.keySet()).get(threads.size() - 1);
        if (threadId == null) {
            Map<String, Object> thread = new HashMap<>();
            thread.put("messages", new ArrayList<>(Collections.singletonList(message)));
            entity = new HttpEntity<>(thread, headers);
            threadId = makePostCallAndGetId(restTemplate, chatGptUrl + "/threads", entity);
            responses = new ArrayList<>();
            assistant.getThreads().put(threadId, responses);
        } else {
            responses = assistant.getThreads().get(threadId);
            entity = new HttpEntity<>(message, headers);
            makePostCallAndGetId(restTemplate, chatGptUrl + "/threads/" + threadId + "/messages", entity);
        }

        entity = createHttpEntity(assistant, engineeredPrompt, headers);

        String runId = makePostCallAndGetId(restTemplate, chatGptUrl + "/threads/" + threadId + "/runs", entity);
        waitForCompletion(restTemplate, threadId, runId, entity);
        assistant.setLastRunId(runId);

        ResponseEntity<Map> response = makeRestCall(restTemplate, chatGptUrl + "/threads/" + threadId + "/messages", HttpMethod.GET, entity);
        AssistantResponse assistantResponse = processResponse(response, assistant, question, runId);
        assistantResponse.setThreadId(threadId);
        responses.add(assistantResponse);
        return assistantResponse;
    }

    public void save(AssistantResponse assistantResponse) {
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = createHeaders();
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("audioUrl", assistantResponse.getAudioUrl());
        metadata.put("videoUrl", assistantResponse.getVideoUrl());
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("metadata", metadata);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
        makePostCallAndGetId(restTemplate, chatGptUrl + "/threads/" + assistantResponse.getThreadId() + "/messages/" + assistantResponse.getMessageId(), entity);
        log.debug("Saved metadata for message {}", assistantResponse.getMessageId());
    }

    private String makePostCallAndGetId(RestTemplate restTemplate, String url, HttpEntity<?> entity) {
        ResponseEntity<Map> response = makeRestCall(restTemplate, url, HttpMethod.POST, entity);
        return extractId(response);
    }

    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + chatgptApiKey);
        headers.set("Content-Type", "application/json");
        headers.set("OpenAI-Beta", "assistants=v1");
        return headers;
    }

    private String createEngineeredPrompt(Assistant assistant) {
        return "You are a " + assistant.getInstructions() + ". Your name is " + assistant.getName() +
                ". Respond in 40 words or less with the feeling of " + assistant.getCurrentMood();
    }

    private Map<String, Object> createMessage(String question) {
        Map<String, Object> userMessage = new HashMap<>();
        userMessage.put("role", "user");
        userMessage.put("content", question);
        return userMessage;
    }

    private ResponseEntity<Map> makeRestCall(RestTemplate restTemplate, String url, HttpMethod method, HttpEntity<?> entity) {
        return restTemplate.exchange(url, method, entity, Map.class);
    }

    private String extractId(ResponseEntity<Map> response) {
        Map responseBody = response.getBody();
        return responseBody != null ? responseBody.get("id").toString() : null;
    }

    private void waitForCompletion(RestTemplate restTemplate, String threadId, String runId, HttpEntity<?> entity) {
        boolean isComplete = false;
        while (!isComplete) {
            ResponseEntity<Map> response = makeRestCall(restTemplate, chatGptUrl + "/threads/" + threadId + "/runs/" + runId, HttpMethod.GET, entity);
            Map responseBody = response.getBody();
            String status = responseBody != null ? responseBody.get("status").toString() : null;
            if (PROCESSING_STATUSES.contains(status)) {
                try {
                    Thread.sleep(300);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            } else {
                isComplete = true;
                if (!"completed".equalsIgnoreCase(status)) {
                    throw new RuntimeException("Error status returned from ChatGPT while running conversation thread: " + status + " for run " + runId);
                }
            }
        }
    }

    private AssistantResponse processResponse(ResponseEntity<Map> response, Assistant assistant, String question, String runId) {
        AssistantResponse assistantResponse = new AssistantResponse();
        assistantResponse.setMood(assistant.getCurrentMood());
        assistantResponse.setOriginalQuestion(question);

        Map responseBody = response.getBody();
        List<Map<String, Object>> messages = responseBody != null ? (List<Map<String, Object>>) responseBody.get("data") : null;
        if (messages != null) {
            Optional<Map<String, Object>> firstMatch = messages.stream()
                    .filter(map -> "assistant".equals(map.get("role")) && runId.equals(map.get("run_id")))
                    .findFirst();
            if (firstMatch.isPresent()) {
                processMessage(firstMatch.get(), assistantResponse);
            } else {
                throw new RuntimeException("Unable to find matching response from ChatGPT for runId: " + runId);
            }
        }
        return assistantResponse;
    }

    private void processMessage(Map<String, Object> message, AssistantResponse assistantResponse) {
        assistantResponse.setMessageId(message.get("id").toString());
        List<Map<String, Object>> content = (List<Map<String, Object>>) message.get("content");
        content.forEach(map -> {
            if (map.containsKey("text")) {
                Map<String, Object> text = (Map<String, Object>) map.get("text");
                if (text != null) {
                    String answer = text.get("value").toString();
                    log.debug("Retrieved answer: {}", answer);
                    assistantResponse.setResponse(answer);
                    assistantResponse.setDetailedResponse(answer);
                }
            }
        });
    }

    private HttpEntity<Map<String, Object>> createHttpEntity(Assistant assistant, String engineeredPrompt, HttpHeaders headers) {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("assistant_id", assistant.getAssistantId());
        requestBody.put("instructions", engineeredPrompt);
        return new HttpEntity<>(requestBody, headers);
    }

    private Assistant createAssistantFromMap(Map result) {
        Assistant assistant = new Assistant();
        assistant.setName((String) result.get("name"));
        assistant.setAssistantId((String) result.get("id"));
        assistant.setInstructions((String) result.get("instructions"));
        assistant.setDescription((String) result.get("description"));
        Map<String, String> metaData = (Map<String, String>) result.get("metadata");
        assistant.setProfilePicture(metaData.get("profilePicture"));
        assistant.setVoiceId(metaData.get("voiceId"));
        assistant.setAvatarId(metaData.get("avatarId"));
        assistant.setIdleVideo(metaData.get("idleVideo"));
        if (metaData.containsKey("startingVideos")) {
            String[] videoArray = metaData.get("startingVideos").split(";");
            List<String> videos = Arrays.asList(videoArray);
            assistant.getStartingVideos().addAll(videos);
        }
        if (metaData.containsKey("introVideos")) {
            String[] videoArray = metaData.get("introVideos").split(";");
            List<String> videos = Arrays.asList(videoArray);
            assistant.getIntroVideos().addAll(videos);
        }
        if (metaData.containsKey("smallTalkVideos")) {
            String[] videoArray = metaData.get("smallTalkVideos").split(";");
            List<String> videos = Arrays.asList(videoArray);
            assistant.getSmallTalkVideos().addAll(videos);
        }
        return assistant;
    }

    @Value("${chatgpt.api.key}")
    private String chatgptApiKey;
    @Value("${chatgpt.api.url}")
    private String chatGptUrl;

    private static final Logger log = LoggerFactory.getLogger(ChatGPTService.class);
    private static final List<String> PROCESSING_STATUSES = Arrays.asList("pending", "queued", "in_progress");
}

