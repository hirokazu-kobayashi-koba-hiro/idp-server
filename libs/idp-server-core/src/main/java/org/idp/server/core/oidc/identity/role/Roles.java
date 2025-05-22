/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.oidc.identity.role;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class Roles implements Iterable<Role> {

  List<Role> values;

  public Roles() {
    this.values = new ArrayList<>();
  }

  public Roles(List<Role> values) {
    this.values = values;
  }

  public boolean exists() {
    return values != null && !values.isEmpty();
  }

  public boolean contains(Role role) {
    return values.stream().anyMatch(value -> value.match((role)));
  }

  @Override
  public Iterator<Role> iterator() {
    return values.iterator();
  }

  public List<String> toStringList() {
    return values.stream().map(Role::name).toList();
  }

  public List<Role> toList() {
    return values;
  }
}
