package io.appform.dropwizard.actors.base.utils;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class NamingUtils {

  public static String queueName(String prefix, String name) {
    return String.format("%s.%s", prefix, name);
  }

  public static String sanitizeMetricName(String metric) {
    return metric == null ? null : metric.replaceAll("[^A-Za-z\\-0-9]", "").toLowerCase();
  }
}
