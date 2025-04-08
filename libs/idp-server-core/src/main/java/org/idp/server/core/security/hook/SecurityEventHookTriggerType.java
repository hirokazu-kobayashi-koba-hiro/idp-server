package org.idp.server.core.security.hook;

import java.util.Set;
import org.idp.server.core.security.event.DefaultSecurityEventType;

public enum SecurityEventHookTriggerType {
  POST_LOGIN(
      Set.of(
          DefaultSecurityEventType.login.name(),
          DefaultSecurityEventType.login_with_session.name())),
  UNDEFINED(Set.of());

  Set<String> eventTypes;

  SecurityEventHookTriggerType(Set<String> eventTypes) {
    this.eventTypes = eventTypes;
  }

  public static SecurityEventHookTriggerType of(String type) {

    for (SecurityEventHookTriggerType securityEventHookTriggerType :
        SecurityEventHookTriggerType.values()) {
      if (securityEventHookTriggerType.eventTypes.contains(type)) {
        return securityEventHookTriggerType;
      }
    }
    return UNDEFINED;
  }

  public boolean isDefined() {
    return this != UNDEFINED;
  }
}
