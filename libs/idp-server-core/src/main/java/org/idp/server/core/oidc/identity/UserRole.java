/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
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
