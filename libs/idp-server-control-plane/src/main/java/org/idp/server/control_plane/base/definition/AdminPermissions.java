/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.control_plane.base.definition;

import java.util.Set;
import java.util.stream.Collectors;

public class AdminPermissions {
  Set<AdminPermission> values;

  public AdminPermissions(Set<AdminPermission> values) {
    this.values = values;
  }

  public Set<String> valuesAsSetString() {
    return values.stream().map(AdminPermission::value).collect(Collectors.toSet());
  }

  public String valuesAsString() {
    return values.stream().map(AdminPermission::value).collect(Collectors.joining(","));
  }

  public boolean includesAll(Set<String> userPermissions) {
    return userPermissions.containsAll(valuesAsSetString());
  }
}
