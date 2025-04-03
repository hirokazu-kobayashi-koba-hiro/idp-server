package org.idp.server.core.hook;

import java.util.Set;
import org.idp.server.core.security.event.DefaultSecurityEventType;

public enum HookTriggerType {
  POST_LOGIN(Set.of(DefaultSecurityEventType.login.name(), DefaultSecurityEventType.login_with_session.name())),
  UNDEFINED(Set.of());

  Set<String> eventTypes;

  HookTriggerType(Set<String> eventTypes) {
    this.eventTypes = eventTypes;
  }

  public static HookTriggerType of(String type) {

    for (HookTriggerType hookTriggerType : HookTriggerType.values()) {
      if (hookTriggerType.eventTypes.contains(type)) {
        return hookTriggerType;
      }
    }
    return UNDEFINED;
  }

  public boolean isDefined() {
    return this != UNDEFINED;
  }
}
