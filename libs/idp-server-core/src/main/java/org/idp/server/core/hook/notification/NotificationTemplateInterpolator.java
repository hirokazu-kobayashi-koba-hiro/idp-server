package org.idp.server.core.hook.notification;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NotificationTemplateInterpolator {

  String template;
  Map<String, Object> context;

  public NotificationTemplateInterpolator(String template, Map<String, Object> context) {
    this.template = template;
    this.context = context;
  }

  public String interpolate() {
    String result = this.template;

    for (String placeholder : extractPlaceholders(template)) {
      Object value = resolveValue(context, placeholder);
      result = result.replace("${" + placeholder + "}", value != null ? value.toString() : "");
    }

    return result;
  }

  private Set<String> extractPlaceholders(String template) {
    Set<String> placeholders = new HashSet<>();
    Matcher matcher = Pattern.compile("\\$\\{([^}]+)}").matcher(template);
    while (matcher.find()) {
      placeholders.add(matcher.group(1));
    }
    return placeholders;
  }

  private static Object resolveValue(Map<String, Object> map, String path) {
    String[] parts = path.split("\\.");
    Object current = map;
    for (String part : parts) {
      if (!(current instanceof Map)) {
        return null;
      }
      current = ((Map<?, ?>) current).get(part);
      if (current == null) {
        return null;
      }
    }
    return current;
  }
}
