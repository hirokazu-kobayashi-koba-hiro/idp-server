package org.idp.server.core.basic.http;

import java.util.Map;
import java.util.function.BiConsumer;

public class HttpRequestHeaders {

  Map<String, String> values;

  public HttpRequestHeaders(Map<String, String> values) {
    this.values = values;
  }

  public Map<String, String> toMap() {
    return values;
  }

  public String optValueAsString(String key, String defaultValue) {
    if (containsKey(key)) {
      return values.get(key);
    }
    return defaultValue;
  }

  public boolean containsKey(String key) {
    return values.containsKey(key);
  }

  public void forEach(BiConsumer<String, String> action) {
    values.forEach(action);
  }

  public String getValueAsString(String key) {
    return values.get(key);
  }
}
