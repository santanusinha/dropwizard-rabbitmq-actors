package io.dropwizard.actors.utils;

import com.google.common.base.Strings;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

public interface CommonUtils {

    static boolean isEmpty(Collection collection) {
        return null == collection || collection.isEmpty();
    }

    static boolean isEmpty(Map map) {
        return null == map || map.isEmpty();
    }

    static boolean isEmpty(String s) {
        return Strings.isNullOrEmpty(s);
    }

    static boolean isRetriable(Set<String> retriableExceptions, Throwable exception) {
        return CommonUtils.isEmpty(retriableExceptions)
                || (null != exception
                && retriableExceptions.contains(exception.getClass().getSimpleName()));
    }
}
