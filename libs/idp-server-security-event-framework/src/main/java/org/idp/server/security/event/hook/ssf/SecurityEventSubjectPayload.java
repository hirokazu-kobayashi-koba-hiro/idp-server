package org.idp.server.security.event.hook.ssf;

import java.util.HashMap;
import java.util.Map;

public class SecurityEventSubjectPayload {
  Map<String, String> values;

  public SecurityEventSubjectPayload() {
    values = new HashMap<>();
  }

  public SecurityEventSubjectPayload(Map<String, String> values) {
    this.values = values;
  }

  public Map<String, String> toMap() {
    return values;
  }
}
