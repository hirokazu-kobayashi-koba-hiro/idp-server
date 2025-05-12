package org.idp.server.control_plane.management.identity.user.io;

import java.util.Map;

public class UserManagementResponse {
  UserManagementStatus status;
  Map<String, Object> contents;

  public UserManagementResponse(UserManagementStatus status, Map<String, Object> contents) {
    this.status = status;
    this.contents = contents;
  }

  public UserManagementStatus status() {
    return status;
  }

  public int statusCode() {
    return status.statusCode();
  }

  public Map<String, Object> contents() {
    return contents;
  }
}
