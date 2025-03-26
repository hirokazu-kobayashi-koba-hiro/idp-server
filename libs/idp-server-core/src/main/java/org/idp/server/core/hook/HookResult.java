package org.idp.server.core.hook;

import java.util.Map;

public class HookResult {
  Map<String, Object> contents;

  public HookResult() {}

  public HookResult(Map<String, Object> contents) {
    this.contents = contents;
  }

  public Map<String, Object> contents() {
    return contents;
  }
}
