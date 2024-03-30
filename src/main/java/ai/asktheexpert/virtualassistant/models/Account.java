package ai.asktheexpert.virtualassistant.models;

import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@Data
public class Account {
    private String region;
    private String accountId;
    private String clientId;
    private String authToken;
    private boolean pinRequired;
    private String uniqueId;
}
