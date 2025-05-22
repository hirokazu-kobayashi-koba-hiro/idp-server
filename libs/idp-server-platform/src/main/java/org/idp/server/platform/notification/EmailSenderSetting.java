package org.idp.server.platform.notification;

import java.util.Map;
import java.util.function.BiConsumer;

public class EmailSenderSetting {

  Map<String, Object> values;

  public EmailSenderSetting(Map<String, Object> values) {
    this.values = values;
  }

  public Map<String, Object> toMap() {
    return values;
  }

  public String getValueAsString(String key) {
    return (String) values.get(key);
  }

  public String optValueAsString(String key, String defaultValue) {
    if (containsKey(key)) {
      return (String) values.get(key);
    }
    return defaultValue;
  }

  public int getValueAsInt(String key) {
    return (int) values.get(key);
  }

  public int optValueAsInt(String key, int defaultValue) {
    if (containsKey(key)) {
      return (int) values.get(key);
    }
    return defaultValue;
  }

  public boolean containsKey(String key) {
    return values.containsKey(key);
  }

  public void forEach(BiConsumer<String, Object> action) {
    values.forEach(action);
  }

  public boolean exists() {
    return values != null && !values.isEmpty();
  }
}
