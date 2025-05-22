/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.adapters.datasource.multi_tenancy.organization;

import java.util.*;
import java.util.stream.Collectors;
import org.idp.server.basic.json.JsonNodeWrapper;
import org.idp.server.platform.multi_tenancy.organization.*;

class ModelConvertor {

  static Organization convert(Map<String, String> result) {
    OrganizationIdentifier identifier = new OrganizationIdentifier(result.getOrDefault("id", ""));
    OrganizationName name = new OrganizationName(result.getOrDefault("name", ""));
    OrganizationDescription description =
        new OrganizationDescription(result.getOrDefault("description", ""));
    List<AssignedTenant> assignedTenantList = new ArrayList<>();
    if (result.containsKey("assigned_tenants") && result.get("assigned_tenants").equals("[]")) {
      JsonNodeWrapper jsonNodeWrapper = JsonNodeWrapper.fromString(result.get("assigned_tenants"));
      Collection<AssignedTenant> distinctTenants =
          jsonNodeWrapper.elements().stream()
              .map(
                  node ->
                      new AssignedTenant(
                          node.getValueOrEmptyAsString("id"),
                          node.getValueOrEmptyAsString("name"),
                          node.getValueOrEmptyAsString("type")))
              .collect(
                  Collectors.toCollection(
                      () -> new TreeSet<>(Comparator.comparing(AssignedTenant::id))));
      assignedTenantList.addAll(distinctTenants);
    }
    AssignedTenants assignedTenants = new AssignedTenants(assignedTenantList);

    return new Organization(identifier, name, description, assignedTenants);
  }
}
