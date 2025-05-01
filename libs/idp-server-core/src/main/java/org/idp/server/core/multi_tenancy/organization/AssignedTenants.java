package org.idp.server.core.multi_tenancy.organization;

import java.util.*;
import org.idp.server.core.multi_tenancy.tenant.Tenant;

public class AssignedTenants implements Iterable<Tenant> {

  List<Tenant> values;

  public AssignedTenants() {
    this.values = new ArrayList<>();
  }

  public AssignedTenants(List<Tenant> values) {
    this.values = values;
  }

  @Override
  public Iterator<Tenant> iterator() {
    return values.iterator();
  }

  public AssignedTenants add(Tenant tenant) {
    List<Tenant> newValues = new ArrayList<>(values);
    values.add(tenant);
    return new AssignedTenants(newValues);
  }

  public AssignedTenants remove(Tenant tenant) {
    List<Tenant> newValues = new ArrayList<>(values);
    newValues.remove(tenant);
    return new AssignedTenants(newValues);
  }

  public List<Map<String, Object>> toMapList() {
    List<Map<String, Object>> result = new ArrayList<>();
    for (Tenant tenant : values) {
      Map<String, Object> row = new HashMap<>();
      row.put("id", tenant.identifier().value());
      row.put("name", tenant.name().value());
      row.put("type", tenant.type().name());
      result.add(row);
    }
    return result;
  }
}
