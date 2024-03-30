package ai.asktheexpert.virtualassistant.models;

import lombok.*;

import java.util.ArrayList;
import java.util.List;

@ToString
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode
public class Camera {
    private String id;
    private String name;
    private String type;
    private String networkId;
    private boolean streamable;
    private List<Protocol> availableProtocols = new ArrayList<>();
    public enum Protocol {
        WEB_RTC,
        RTSP
    }
}
