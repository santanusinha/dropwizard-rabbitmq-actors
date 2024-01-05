/*
 * Copyright (c) 2019 Santanu Sinha <santanu.sinha@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package io.appform.dropwizard.actors.config;

import io.appform.dropwizard.actors.connectivity.ConnectionConfig;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotEmpty;
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

    @Default
    private String virtualHost = "/";

    /**
     * Enable SSL for connectivity. TODO:: Give proper cert based support
     */
    private boolean secure;

    @Min(0)
    @Max(1024)
    private int startupGracePeriodSeconds = 0;

    private String certStorePath;

    private String certPassword;

    private String serverCertStorePath;

    private String serverCertPassword;

    @Valid
    private List<ConnectionConfig> connections;

    @Valid
    private MetricConfig metricConfig;
}
