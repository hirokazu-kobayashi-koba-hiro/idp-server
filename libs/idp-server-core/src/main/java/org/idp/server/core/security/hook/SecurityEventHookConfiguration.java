package org.idp.server.core.security.hook;

import java.util.List;
import java.util.Map;
import org.idp.server.basic.json.JsonReadable;

public class SecurityEventHookConfiguration implements JsonReadable {

  String id;
  String type;
  List<String> triggers;
  int executionOrder;
  Map<String, Object> payload;

  public SecurityEventHookConfiguration() {}

  public SecurityEventHookConfiguration(String id, String type, List<String> triggers, Map<String, Object> payload) {
    this.id = id;
    this.type = type;
    this.triggers = triggers;
    this.payload = payload;
  }

  public SecurityEventHookConfigurationIdentifier identifier() {
    return new SecurityEventHookConfigurationIdentifier(id);
  }

  public SecurityEventHookType hookType() {
    return new SecurityEventHookType(type);
  }

  public Map<String, Object> payload() {
    return payload;
  }

  public List<String> triggers() {
    return triggers;
  }

  public boolean hasTrigger(String trigger) {
    return triggers.contains(trigger);
  }

  public boolean exists() {
    return id != null && !id.isEmpty();
  }

  public int executionOrder() {
    return executionOrder;
  }
}
