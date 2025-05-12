package org.idp.server.control_plane.management.authentication.io;

import java.util.Map;

public class AuthenticationConfigManagementResponse {
  AuthenticationConfigManagementStatus status;
  Map<String, Object> contents;

  public AuthenticationConfigManagementResponse(
      AuthenticationConfigManagementStatus status, Map<String, Object> contents) {
    this.status = status;
    this.contents = contents;
  }

  public AuthenticationConfigManagementStatus status() {
    return status;
  }

  public int statusCode() {
    return status.statusCode();
  }

  public Map<String, Object> contents() {
    return contents;
  }
}
