/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.oidc.identity.permission;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class Permissions implements Iterable<Permission> {

  List<Permission> values;

  public Permissions() {
    this.values = new ArrayList<>();
  }

  public Permissions(List<Permission> values) {
    this.values = values;
  }

  public boolean exists() {
    return values != null && !values.isEmpty();
  }

  public boolean contains(Permission permission) {
    return values.stream().anyMatch(value -> value.match((permission)));
  }

  @Override
  public Iterator<Permission> iterator() {
    return values.iterator();
  }

  public Permissions filter(List<String> permissionNames) {
    return new Permissions(
        values.stream().filter(permission -> permissionNames.contains(permission.name())).toList());
  }

  public List<Permission> toList() {
    return values;
  }
}
