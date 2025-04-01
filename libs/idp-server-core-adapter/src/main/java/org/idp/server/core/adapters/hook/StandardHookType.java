package org.idp.server.core.adapters.hook;

import org.idp.server.core.hook.HookType;

public enum StandardHookType {
  WEBHOOK,
  SLACK;


  public HookType toHookType() {
    return new HookType(name());
  }
}
