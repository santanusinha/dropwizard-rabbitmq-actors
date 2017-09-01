package io.dropwizard.actors.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.NotEmpty;

import javax.validation.constraints.NotNull;
import java.util.List;

/**
 * Connectivity config for rabbitMQ
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class RMQConfig {

    /**
     * List of brokers
     */
    @NotNull
    @NotEmpty
    private List<Broker> brokers;

    /**
     * The username to connect ot rabbitmq cluster
     */
    @NotEmpty
    @NotNull
    private String userName;

    /**
     * Size of thread pool to be used for connection
     */
    @NotNull
    private int threadPoolSize;

    /**
     * Rabbitmq cluster password
     */
    @NotEmpty
    @NotNull
    private String password;

    /**
     * Enable SSL for connectivity. TODO:: Give proper cert based support
     */
    private boolean secure;

    private String certStorePath;

    private String certPassword;

    private String serverCertStorePath;

    private String serverCertPassword;

}
