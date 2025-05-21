package org.idp.server.core.adapters.datasource.multi_tenancy.organization;

import java.util.*;
import java.util.stream.Collectors;
import org.idp.server.basic.json.JsonNodeWrapper;
import org.idp.server.core.multi_tenancy.organization.*;

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
