package org.idp.server.core.authentication.sms;

import java.util.Map;
import java.util.function.BiConsumer;

public class SmsAuthenticationExecutionRequest {

  Map<String, Object> values;

  public static SmsAuthenticationExecutionRequest empty() {
    return new SmsAuthenticationExecutionRequest(Map.of());
  }

  public SmsAuthenticationExecutionRequest(Map<String, Object> values) {
    this.values = values;
  }

  public Map<String, Object> toMap() {
    return values;
  }

  public String optValueAsString(String key, String defaultValue) {
    if (containsKey(key)) {
      return (String) values.get(key);
    }
    return defaultValue;
  }

  public String getValueAsString(String key) {
    return (String) values.get(key);
  }

  public boolean getValueAsBoolean(String key) {
    return (boolean) values.get(key);
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

  public Object getValue(String key) {
    return values.get(key);
  }
}
