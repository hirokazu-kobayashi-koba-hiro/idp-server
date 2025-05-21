package org.idp.server.platform.security.event;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class SecurityEventDetail {
  Map<String, Object> values;

  public SecurityEventDetail() {
    values = new HashMap<>();
  }

  public SecurityEventDetail(Map<String, Object> values) {
    this.values = values;
  }

  public Map<String, Object> toMap() {
    return values;
  }

  public boolean exists() {
    return Objects.nonNull(values) && !values.isEmpty();
  }
}
