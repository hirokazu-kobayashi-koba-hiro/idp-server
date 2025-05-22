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
