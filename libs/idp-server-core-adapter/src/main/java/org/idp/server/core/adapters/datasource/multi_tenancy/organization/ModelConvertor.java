/*
 * Copyright 2025 Hirokazu Kobayashi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.idp.server.core.adapters.datasource.multi_tenancy.organization;

import java.util.*;
import java.util.stream.Collectors;
import org.idp.server.platform.json.JsonNodeWrapper;
import org.idp.server.platform.multi_tenancy.organization.*;

class ModelConvertor {

  static Organization convert(Map<String, String> result) {
    OrganizationIdentifier identifier = new OrganizationIdentifier(result.getOrDefault("id", ""));
    OrganizationName name = new OrganizationName(result.getOrDefault("name", ""));
    OrganizationDescription description =
        new OrganizationDescription(result.getOrDefault("description", ""));
    boolean enabled = Boolean.parseBoolean(result.getOrDefault("enabled", "true"));
    List<AssignedTenant> assignedTenantList = new ArrayList<>();
    if (result.containsKey("tenants") && !result.get("tenants").equals("[]")) {
      JsonNodeWrapper jsonNodeWrapper = JsonNodeWrapper.fromString(result.get("tenants"));
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

    return new Organization(identifier, name, description, assignedTenants, enabled);
  }
}
