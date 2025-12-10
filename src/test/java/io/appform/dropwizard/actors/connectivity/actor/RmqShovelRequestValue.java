package io.appform.dropwizard.actors.connectivity.actor;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import javax.validation.constraints.NotEmpty;
import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

@Data
@Builder
@Jacksonized
public class RmqShovelRequestValue {

    @NotEmpty
    @JsonProperty("src-delete-after")
    private final String srcDeleteAfter = "queue-length";

    @NotEmpty
    @JsonProperty("src-queue")
    private final String sourceQueue;

    @NotEmpty
    @JsonProperty("dest-queue")
    private final String destinationQueue;

    @NotEmpty
    @JsonIgnore
    private String virtualHost;

    @NotEmpty
    @JsonIgnore
    private String user;

    @JsonProperty("ack-mode")
    private final String ackMode = "on-confirm";

    @JsonProperty("dest-add-forward-headers")
    private final boolean addForwardingHeader = true;

    @JsonProperty("dest-add-timestamp-header")
    private final boolean addForwardingTimestampHeader = true;

    @JsonProperty("src-uri")
    public String getSrcUri() {
        return "amqp://" + URLEncoder.encode(user, StandardCharsets.UTF_8) + "@/" + URLEncoder.encode(virtualHost,
                StandardCharsets.UTF_8);
    }

    @JsonProperty("dest-uri")
    public String getDestUri() {
        return "amqp://" + URLEncoder.encode(user, StandardCharsets.UTF_8) + "@/" + URLEncoder.encode(virtualHost,
                StandardCharsets.UTF_8);
    }

    @JsonProperty("src-protocol")
    private final String srcProtocol = "amqp091";

    @JsonProperty("dest-protocol")
    private final String destProtocol = "amqp091";
}