package org.idp.server.platform.security.hook;

public enum StandardSecurityEventHookType {
  WEBHOOK,
  SSF;

  public SecurityEventHookType toHookType() {
    return new SecurityEventHookType(name());
  }
}
