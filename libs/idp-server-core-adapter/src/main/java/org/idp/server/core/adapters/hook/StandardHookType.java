package org.idp.server.core.adapters.hook;

import org.idp.server.core.hook.HookType;

public enum StandardHookType {
  WEBHOOK,
  SLACK,
  DATADOG_LOG;

  public HookType toHookType() {
    return new HookType(name());
  }
}
