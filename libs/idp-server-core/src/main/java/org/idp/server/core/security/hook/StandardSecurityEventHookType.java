package org.idp.server.core.security.hook;

public enum StandardSecurityEventHookType {
  WEBHOOK, SSF;

  public SecurityEventHookType toHookType() {
    return new SecurityEventHookType(name());
  }
}
