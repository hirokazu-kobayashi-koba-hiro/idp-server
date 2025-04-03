package org.idp.server.core.security.ssf;

import java.util.HashMap;
import java.util.Map;

public class SecurityEventSubject {
  SecuritySubjectFormat format;
  SecurityEventSubjectPayload payload;

  public SecurityEventSubject() {}

  public SecurityEventSubject(SecuritySubjectFormat format, SecurityEventSubjectPayload payload) {
    this.format = format;
    this.payload = payload;
  }

  public SecuritySubjectFormat format() {
    return format;
  }

  public SecurityEventSubjectPayload payload() {
    return payload;
  }

  public Map<String, String> toMap() {
    Map<String, String> map = new HashMap<>();
    map.put("format", format.name());
    map.putAll(payload.toMap());
    return map;
  }
}
