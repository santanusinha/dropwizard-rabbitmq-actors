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
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Slf4j
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

    public static <T> Optional<T> extractMessagePropertiesHeader(final Map<String, Object> headers,
                                                          final String key,
                                                          final Class<T> clazz) {
        if (null == headers || !headers.containsKey(key)) {
            return Optional.empty();
        }

        return Optional.ofNullable(headers.get(key))
                .filter(value -> {
                    if (clazz == value.getClass()) {
                        return true;
                    }
                    log.warn("Cannot cast {} to class {}", value, clazz);
                    return false;
                })
                .map(clazz::cast);
    }
}
