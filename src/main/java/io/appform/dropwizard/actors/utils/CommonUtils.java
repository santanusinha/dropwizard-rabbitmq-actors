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

package io.appform.dropwizard.actors.utils;

import com.google.common.base.Strings;
import com.rabbitmq.client.AMQP;
import io.appform.dropwizard.actors.common.Constants;
import io.appform.dropwizard.actors.observers.ConsumeObserverContext;
import io.appform.dropwizard.actors.observers.PublishObserverContext;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.slf4j.MDC;

import java.util.*;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class CommonUtils {

    public static boolean isEmpty(Collection<?> collection) {
        return null == collection || collection.isEmpty();
    }

    public static boolean isEmpty(Map<?, ?> map) {
        return null == map || map.isEmpty();
    }

    public static boolean isEmpty(String s) {
        return Strings.isNullOrEmpty(s);
    }

    public static boolean isRetriable(Set<String> retriableExceptions, Throwable exception) {
        return CommonUtils.isEmpty(retriableExceptions)
                || (null != exception
                && retriableExceptions.contains(exception.getClass().getSimpleName()));
    }


    private AMQP.BasicProperties getCloneBasicProperties(AMQP.BasicProperties basicProperties) {
        return new AMQP.BasicProperties.Builder()
                .appId(basicProperties.getAppId())
                .clusterId(basicProperties.getClusterId())
                .contentEncoding(basicProperties.getContentEncoding())
                .contentType(basicProperties.getContentType())
                .correlationId(basicProperties.getCorrelationId())
                .deliveryMode(basicProperties.getDeliveryMode())
                .expiration(basicProperties.getExpiration())
                .messageId(basicProperties.getMessageId())
                .priority(basicProperties.getPriority())
                .replyTo(basicProperties.getReplyTo())
                .timestamp(basicProperties.getTimestamp())
                .type(basicProperties.getType())
                .userId(basicProperties.getUserId())
                .headers(getTracingMap(basicProperties.getHeaders())).build();
    }

    public static Map<String, Object> getTracingMap(Map<String, Object> headers) {
        Map<String, Object> outputHeaders = new HashMap<>();
        if (headers != null) {
            outputHeaders.putAll(headers);
        }
        outputHeaders.put(Constants.TRACE_ID, getTraceId());
        return outputHeaders;
    }

    private static String getTraceId() {
        return Optional.ofNullable(MDC.get(Constants.TRACE_ID))
                .orElse(UUID.randomUUID().toString().replace("-", ""));
    }
    public static void populateTraceId(ConsumeObserverContext consumeObserverContext) {
        if(consumeObserverContext.getHeaders()!=null && consumeObserverContext.getHeaders().containsKey(Constants.TRACE_ID)){
            MDC.put(Constants.TRACE_ID, String.valueOf(consumeObserverContext.getHeaders().get(Constants.TRACE_ID)));
        }
    }
}
