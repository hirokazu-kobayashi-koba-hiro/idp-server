package org.idp.server.type.oauth;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class CustomParams {
  Map<String, String> values;

  public CustomParams() {
    values = new HashMap<>();
  }

  public CustomParams(Map<String, String> values) {
    this.values = values;
  }

  public Map<String, String> values() {
    return values;
  }

  public boolean exists() {
    return Objects.nonNull(values) && !values.isEmpty();
  }
}
