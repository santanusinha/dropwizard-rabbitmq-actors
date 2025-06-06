/*
 * Copyright (c) 2024 Santanu Sinha <santanu.sinha@gmail.com>
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

package io.appform.dropwizard.actors.utils;

import io.appform.dropwizard.actors.config.Broker;
import io.appform.dropwizard.actors.config.RMQConfig;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.testcontainers.containers.RabbitMQContainer;

import java.util.ArrayList;

/**
 *
 */
@Slf4j
@UtilityClass
public class CommonTestUtils {

    public static RMQConfig getRMQConfig(final RabbitMQContainer rabbitmqContainer) {
        val rmqConfig = new RMQConfig();
        val mappedPort = rabbitmqContainer.getMappedPort(5672);
        val host = rabbitmqContainer.getHost();
        val brokers = new ArrayList<Broker>();
        brokers.add(new Broker(host, mappedPort));
        rmqConfig.setBrokers(brokers);
        rmqConfig.setUserName(RMQContainer.RABBITMQ_USERNAME);
        rmqConfig.setPassword(RMQContainer.RABBITMQ_PASSWORD);
        rmqConfig.setVirtualHost("/");
        log.info("RabbitMQ connection details: {}", rmqConfig);
        return rmqConfig;
    }
}
