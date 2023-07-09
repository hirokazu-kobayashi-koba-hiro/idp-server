package org.idp.server.oauth.rar;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public class CredentialDefinition {

  Map<String, Object> values;

  public CredentialDefinition(Map<String, Object> values) {
    this.values = values;
  }

  public List<String> type() {
    return getListOrEmpty("type");
  }

  public boolean hasType() {
    return values.containsKey("type");
  }

  public List<String> context() {
    return getListOrEmpty("@context");
  }

  public boolean hasContext() {
    return values.containsKey("@context");
  }

  public List<String> getListOrEmpty(String key) {
    Object value = values.get(key);
    if (Objects.isNull(value)) {
      return List.of();
    }
    return (List<String>) value;
  }

  public String getStringOrEmpty(String key) {
    Object value = values.get(key);
    if (Objects.isNull(value)) {
      return "";
    }
    return (String) value;
  }

  public Map<String, Object> getMapOrEmpty(String key) {
    Object value = values.get(key);
    if (Objects.isNull(value)) {
      return Map.of();
    }
    return (Map<String, Object>) value;
  }
}
