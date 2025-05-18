package org.idp.server.control_plane.management.organization.invitation.io;

import java.util.Map;

public class TenantInvitationManagementRequest {
  Map<String, Object> values;

  public TenantInvitationManagementRequest(Map<String, Object> values) {
    this.values = values;
  }

  public Map<String, Object> toMap() {
    return values;
  }

  public Object get(String key) {
    return values.get(key);
  }

  public String getValueAsString(String key) {
    return (String) values.get(key);
  }
}
