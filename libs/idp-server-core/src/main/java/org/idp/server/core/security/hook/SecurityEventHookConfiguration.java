package org.idp.server.core.security.hook;

import java.util.List;
import java.util.Map;
import org.idp.server.core.basic.http.*;
import org.idp.server.core.basic.json.JsonReadable;

public class SecurityEventHookConfiguration implements JsonReadable {

  String trigger;
  String type;
  Map<String, Object> detail;

  public SecurityEventHookConfiguration() {}

  public SecurityEventHookTriggerType triggerType() {
    return SecurityEventHookTriggerType.valueOf(trigger);
  }

  public SecurityEventHookType hookType() {
    return new SecurityEventHookType(type);
  }

  public Map<String, Object> detail() {
    return detail;
  }

  public boolean exists() {
    return trigger != null && !trigger.isEmpty() && type != null && !type.isEmpty();
  }
}
