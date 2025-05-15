package org.idp.server.core.identity.role;

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
