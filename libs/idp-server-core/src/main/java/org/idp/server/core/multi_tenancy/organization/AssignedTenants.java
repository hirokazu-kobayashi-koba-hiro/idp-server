package org.idp.server.core.multi_tenancy.organization;

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
