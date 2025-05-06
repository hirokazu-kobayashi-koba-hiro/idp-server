package org.idp.server.core.security.hook;

import java.util.Map;
import java.util.UUID;

public class SecurityEventHookResult {

  SecurityEventHookResultIdentifier identifier;
  SecurityEventHookStatus status;
  SecurityEventHookType type;
  Map<String, Object> contents;

  public SecurityEventHookResult() {}

  public static SecurityEventHookResult success(SecurityEventHookType type, Map<String, Object> contents) {
    SecurityEventHookResultIdentifier identifier = new SecurityEventHookResultIdentifier(UUID.randomUUID().toString());
    return new SecurityEventHookResult(identifier, SecurityEventHookStatus.SUCCESS, type, contents);
  }

  public static SecurityEventHookResult failure(SecurityEventHookType type, Map<String, Object> contents) {
    SecurityEventHookResultIdentifier identifier = new SecurityEventHookResultIdentifier(UUID.randomUUID().toString());
    return new SecurityEventHookResult(identifier, SecurityEventHookStatus.FAILURE, type, contents);
  }

  public SecurityEventHookResult(SecurityEventHookResultIdentifier identifier, SecurityEventHookStatus status, SecurityEventHookType type, Map<String, Object> contents) {
    this.identifier = identifier;
    this.status = status;
    this.type = type;
    this.contents = contents;
  }

  public SecurityEventHookResultIdentifier identifier() {
    return identifier;
  }

  public SecurityEventHookStatus status() {
    return status;
  }

  public SecurityEventHookType type() {
    return type;
  }

  public Map<String, Object> contents() {
    return contents;
  }
}
