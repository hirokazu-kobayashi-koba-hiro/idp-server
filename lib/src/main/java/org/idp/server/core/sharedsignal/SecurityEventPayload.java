package org.idp.server.core.sharedsignal;

import java.util.HashMap;
import java.util.Map;

public class SecurityEventPayload {
  Map<String, Object> values;

  public SecurityEventPayload() {
    values = new HashMap<>();
  }

  public SecurityEventPayload(Map<String, Object> values) {
    this.values = values;
  }

  public Map<String, Object> toMap() {
    return values;
  }
}
