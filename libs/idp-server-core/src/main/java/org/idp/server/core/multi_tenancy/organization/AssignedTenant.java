package org.idp.server.core.multi_tenancy.organization;

import java.util.HashMap;
import java.util.Map;

public class AssignedTenant {
  String id;
  String name;
  String type;

  public AssignedTenant() {}

  public AssignedTenant(String id, String name, String type) {
    this.id = id;
    this.name = name;
    this.type = type;
  }

  public String id() {
    return id;
  }

  public String name() {
    return name;
  }

  public String type() {
    return type;
  }

  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<>();
    map.put("id", id);
    map.put("name", name);
    map.put("type", type);

    return map;
  }
}
