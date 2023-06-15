package org.idp.server.oauth.rar;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class AuthorizationDetail {
  Map<String, Object> values;

  public AuthorizationDetail() {
    this.values = new HashMap<>();
  }

  public AuthorizationDetail(Map<String, Object> values) {
    this.values = values;
  }

  public String type() {
    return getStringOrEmpty("type");
  }

  public List<String> locations() {
    return getListOrEmpty("locations");
  }

  public List<String> actions() {
    return getListOrEmpty("actions");
  }

  public List<String> datatypes() {
    return getListOrEmpty("datatypes");
  }

  public String identifier() {
    return getStringOrEmpty("identifier");
  }

  public List<String> privileges() {
    return getListOrEmpty("privileges");
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

  public boolean hasType() {
    return !type().isEmpty();
  }

  public Map<String, Object> values() {
    return values;
  }
}
