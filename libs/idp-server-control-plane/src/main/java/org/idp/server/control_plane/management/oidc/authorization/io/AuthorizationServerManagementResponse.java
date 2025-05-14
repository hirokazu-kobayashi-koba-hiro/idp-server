package org.idp.server.control_plane.management.oidc.authorization.io;

import java.util.Map;

public class AuthorizationServerManagementResponse {
  AuthorizationServerManagementStatus status;
  Map<String, Object> contents;

  public AuthorizationServerManagementResponse(
      AuthorizationServerManagementStatus status, Map<String, Object> contents) {
    this.status = status;
    this.contents = contents;
  }

  public AuthorizationServerManagementStatus status() {
    return status;
  }

  public int statusCode() {
    return status.statusCode();
  }

  public Map<String, Object> contents() {
    return contents;
  }
}
