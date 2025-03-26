package org.idp.server.core.hook;

import java.util.Map;
import org.idp.server.core.type.exception.UnSupportedException;

public class AuthenticationHooks {

  Map<HookTriggerType, HookExecutor> values;

  public AuthenticationHooks(Map<HookTriggerType, HookExecutor> values) {
    this.values = values;
  }

  public HookExecutor get(HookTriggerType type) {

    HookExecutor hookExecutor = values.get(type);

    if (hookExecutor == null) {
      throw new UnSupportedException("No executor registered for type " + type);
    }

    return hookExecutor;
  }
}
