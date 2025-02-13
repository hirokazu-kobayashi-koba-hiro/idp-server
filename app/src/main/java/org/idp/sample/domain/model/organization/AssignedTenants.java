package org.idp.sample.domain.model.organization;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.idp.sample.domain.model.tenant.Tenant;

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
}
