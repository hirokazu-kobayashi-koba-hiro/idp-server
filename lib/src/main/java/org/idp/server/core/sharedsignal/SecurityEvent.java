package org.idp.server.core.sharedsignal;

import java.util.HashMap;
import java.util.Map;

public class SecurityEvent {
  SecurityEventType type;
  SecurityEventSubject subject;
  SecurityEventPayload payload;

  public SecurityEvent() {}

  public SecurityEvent(SecurityEventType type, SecurityEventSubject subject) {
    this.type = type;
    this.subject = subject;
  }

  public SecurityEvent(
      SecurityEventType type, SecurityEventSubject subject, SecurityEventPayload payload) {
    this.type = type;
    this.subject = subject;
    this.payload = payload;
  }

  public boolean isDefined() {
    return type.isDefined();
  }

  public SecurityEventType type() {
    return type;
  }

  public SecurityEventSubject subject() {
    return subject;
  }

  public SecurityEventPayload payload() {
    return payload;
  }

  public Map<String, Object> eventAsMap() {
    HashMap<String, Object> events = new HashMap<>();
    events.put("subject", subject.toMap());
    events.putAll(payload.toMap());

    HashMap<String, Object> result = new HashMap<>();
    result.put(type.typeIdentifier().value(), events);

    return result;
  }
}
