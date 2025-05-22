/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package org.idp.server.platform.multi_tenancy.tenant;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

public class TenantFeature {
  String name;
  String configId;
  boolean enabled;
  LocalDateTime createdAt;
  LocalDateTime updatedAt;

  public TenantFeature() {}

  public TenantFeature(
      String name,
      String configId,
      boolean enabled,
      LocalDateTime createdAt,
      LocalDateTime updatedAt) {
    this.name = name;
    this.configId = configId;
    this.enabled = enabled;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
  }

  public String name() {
    return name;
  }

  public String configId() {
    return configId;
  }

  public boolean enabled() {
    return enabled;
  }

  public LocalDateTime createdAt() {
    return createdAt;
  }

  public LocalDateTime updatedAt() {
    return updatedAt;
  }

  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<>();
    map.put("name", name);
    map.put("config_id", configId);
    map.put("enabled", enabled);
    map.put("created_at", createdAt.toString());
    map.put("updated_at", updatedAt.toString());
    return map;
  }
}
