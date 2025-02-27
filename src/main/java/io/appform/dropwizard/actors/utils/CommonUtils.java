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
import io.appform.dropwizard.actors.actor.MessageMetadata;
import io.appform.opentracing.FunctionData;
import io.appform.opentracing.TracingHandler;
import io.appform.opentracing.util.TracerUtil;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.val;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

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

    public static void startTracing(MessageMetadata messageMetadata, final FunctionData functionData) {
        val properties = new AMQP.BasicProperties.Builder()
                .headers(messageMetadata.getHeaders())
                .build();
        TracerUtil.populateTracingFromQueue(properties);
        if(TracerUtil.isTracePresent()) {
            TracerUtil.populateMDCTracing(TracingHandler.startSpan(functionData, ""));
        }
    }


    public static Map<String, Object> getTracingMap() {
        if(TracerUtil.isTracePresent()) {
            return Map.of(TracerUtil.TRACE_ID, TracerUtil.getMDCTraceId(), TracerUtil.SPAN_ID, TracerUtil.getMDCSpanId());
        }
        return Map.of();
    }


}
