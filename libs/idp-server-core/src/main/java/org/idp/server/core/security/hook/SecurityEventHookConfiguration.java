package org.idp.server.core.security.hook;

import java.util.List;
import java.util.Map;
import org.idp.server.basic.json.JsonReadable;

public class SecurityEventHookConfiguration implements JsonReadable {

  String type;
  List<String> triggers;
  Map<String, Object> details;

  public SecurityEventHookConfiguration() {}

  public SecurityEventHookType hookType() {
    return new SecurityEventHookType(type);
  }

  public Map<String, Object> details() {
    return details;
  }

  public boolean hasTrigger(String trigger) {
    return triggers.contains(trigger);
  }

  public boolean exists() {
    return type != null && !type.isEmpty();
  }
}
