package org.idp.server.core.oidc.identity;

import java.io.Serializable;
import org.idp.server.basic.json.JsonReadable;

public class UserRole implements Serializable, JsonReadable {
  String roleId;
  String roleName;

  public UserRole() {}

  public UserRole(String roleId, String roleName) {
    this.roleId = roleId;
    this.roleName = roleName;
  }

  public String roleId() {
    return roleId;
  }

  public String roleName() {
    return roleName;
  }
}
