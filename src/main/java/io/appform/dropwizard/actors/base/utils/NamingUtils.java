package io.appform.dropwizard.actors.base.utils;

public class NamingUtils {

  public static String queueName(String prefix, String name) {
    return String.format("%s.%s", prefix, name);
  }
}
