package org.idp.server.core.security.hook;

import java.util.Map;

public class SecurityEventHookResult {
  Map<String, Object> contents;

  public SecurityEventHookResult() {}

  public SecurityEventHookResult(Map<String, Object> contents) {
    this.contents = contents;
  }

  public Map<String, Object> contents() {
    return contents;
  }
}
