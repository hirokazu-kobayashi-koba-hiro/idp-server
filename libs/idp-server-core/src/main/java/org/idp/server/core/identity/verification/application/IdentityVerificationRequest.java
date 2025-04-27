package org.idp.server.core.identity.verification.application;

import java.util.Map;
import java.util.function.BiConsumer;

public class IdentityVerificationRequest {
  Map<String, Object> values;

  public IdentityVerificationRequest() {}

  public IdentityVerificationRequest(Map<String, Object> values) {
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
    return (boolean) values.getOrDefault(key, Boolean.FALSE);
  }

  public Integer optValueAsInteger(String key, int defaultValue) {
    return (Integer) values.getOrDefault(key, defaultValue);
  }

  public Long optValueAsLong(String key, long defaultValue) {
    return (Long) values.getOrDefault(key, defaultValue);
  }

  public Map<String, Object> getValueAsMap(String key) {
    return (Map<String, Object>) values.get(key);
  }

  public Map<String, Object> optValueAsMap(String key, Map<String, Object> defaultValue) {
    return (Map<String, Object>) values.getOrDefault(key, defaultValue);
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
