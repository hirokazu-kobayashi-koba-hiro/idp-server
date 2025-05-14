package org.idp.server.control_plane.management.security.hook.io;

import java.util.Map;

public class SecurityEventHookConfigManagementResponse {
  SecurityEventHookConfigManagementStatus status;
  Map<String, Object> contents;

  public SecurityEventHookConfigManagementResponse(
      SecurityEventHookConfigManagementStatus status, Map<String, Object> contents) {
    this.status = status;
    this.contents = contents;
  }

  public SecurityEventHookConfigManagementStatus status() {
    return status;
  }

  public int statusCode() {
    return status.statusCode();
  }

  public Map<String, Object> contents() {
    return contents;
  }
}
