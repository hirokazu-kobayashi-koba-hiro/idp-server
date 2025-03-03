package org.idp.server.core.basic.http;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class QueryParams {
  Map<String, String> values;

  public QueryParams() {
    this.values = new HashMap<>();
  }

  public QueryParams(Map<String, String> values) {
    this.values = values;
  }

  public void add(String key, String value) {
    String urlEncodedValue = URLEncoder.encode(value, StandardCharsets.UTF_8);
    values.put(key, urlEncodedValue);
  }

  public String params() {
    StringBuilder stringBuilder = new StringBuilder();
    Set<Map.Entry<String, String>> entries = values.entrySet();
    entries.forEach(
        entry -> {
          if (Objects.nonNull(entry.getValue()) && !entry.getValue().isEmpty()) {
            if (!stringBuilder.toString().isEmpty()) {
              stringBuilder.append("&");
            }
            stringBuilder.append(entry.getKey());
            stringBuilder.append("=");
            stringBuilder.append(entry.getValue());
          }
        });
    return stringBuilder.toString();
  }
}
