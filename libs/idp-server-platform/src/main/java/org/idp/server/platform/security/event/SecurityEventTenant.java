/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package org.idp.server.platform.security.event;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class SecurityEventTenant {

  String id;
  String issuer;
  String name;

  public SecurityEventTenant() {}

  public SecurityEventTenant(String id, String tokenIssuer, String name) {
    this.id = id;
    this.issuer = tokenIssuer;
    this.name = name;
  }

  public Map<String, Object> toMap() {
    HashMap<String, Object> result = new HashMap<>();
    if (id != null) {
      result.put("id", id);
    }
    if (issuer != null) {
      result.put("iss", issuer);
    }
    if (name != null) {
      result.put("name", name);
    }
    return result;
  }

  public String id() {
    return id;
  }

  public String issuer() {
    return issuer;
  }

  public String issuerAsString() {
    return issuer;
  }

  public String name() {
    return name;
  }

  public boolean exists() {
    return Objects.nonNull(id) && !id.isEmpty();
  }
}
