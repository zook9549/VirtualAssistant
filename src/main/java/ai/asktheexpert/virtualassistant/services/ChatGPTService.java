package ai.asktheexpert.virtualassistant.services;

import ai.asktheexpert.virtualassistant.models.Persona;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ChatGPTService implements AnswerService {

    public String answer(String question, Persona persona) {
        log.debug("Asking {}", question);

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + chatgptApiKey);
        headers.set("Content-Type", "application/json");

        // Create a conversation payload
        List<Map<String, String>> conversation = new ArrayList<>();
        Map<String, String> systemMessage = new HashMap<>();
        systemMessage.put("role", "system");
        String engineeredPrompt = "You are a " + persona.getRole() + ". Your name is " + persona.getName() + ". You are feeling " + persona.getCurrentMood() + ". Respond within 1-2 sentences.";
        log.debug("Fetching answer with prompt: {}", engineeredPrompt);
        systemMessage.put("content", engineeredPrompt);
        conversation.add(systemMessage);
        Map<String, String> userMessage = new HashMap<>();
        userMessage.put("role", "user");
        userMessage.put("content", question);
        conversation.add(userMessage);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("max_tokens", 100);
        requestBody.put("temperature", 0.5);
        requestBody.put("model", "gpt-4");
        requestBody.put("messages", conversation);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<Map> response = restTemplate.exchange(chatGptUrl, HttpMethod.POST, entity, Map.class);

        Map responseBody = response.getBody();
        String answer = ((Map<?, ?>) ((Map<?, ?>) ((List<?>) responseBody.get("choices")).get(0)).get("message")).get("content").toString();
        log.debug("Completed getting answer: {}", answer);
        return answer;
    }

    @Value("${chatgpt.api.key}")
    private String chatgptApiKey;
    @Value("${chatgpt.api.url}")
    private String chatGptUrl;
    private static final Logger log = LoggerFactory.getLogger(ChatGPTService.class);
}
