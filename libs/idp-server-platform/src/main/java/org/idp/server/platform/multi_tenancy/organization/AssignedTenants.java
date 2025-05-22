/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package org.idp.server.platform.multi_tenancy.organization;

import java.util.*;

public class AssignedTenants implements Iterable<AssignedTenant> {

  List<AssignedTenant> values;

  public AssignedTenants() {
    this.values = new ArrayList<>();
  }

  public AssignedTenants(List<AssignedTenant> values) {
    this.values = values;
  }

  @Override
  public Iterator<AssignedTenant> iterator() {
    return values.iterator();
  }

  public AssignedTenants add(AssignedTenant assignedTenant) {
    List<AssignedTenant> newValues = new ArrayList<>(values);
    newValues.add(assignedTenant);
    return new AssignedTenants(newValues);
  }

  public AssignedTenants remove(AssignedTenant tenant) {
    List<AssignedTenant> newValues = new ArrayList<>(values);
    newValues.remove(tenant);
    return new AssignedTenants(newValues);
  }

  public List<Map<String, Object>> toMapList() {
    List<Map<String, Object>> result = new ArrayList<>();
    for (AssignedTenant assignedTenant : values) {
      result.add(assignedTenant.toMap());
    }
    return result;
  }

  public boolean exists() {
    return values != null && !values.isEmpty();
  }
}
