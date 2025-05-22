/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.control_plane.base;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AuthorizationDetailRequestBuilder {
  Map<String, Object> detail;

  public AuthorizationDetailRequestBuilder() {
    this.detail = new HashMap<>();
  }

  public AuthorizationDetailRequestBuilder addType(String type) {
    detail.put("type", type);
    return this;
  }

  public AuthorizationDetailRequestBuilder addActions(List<String> actions) {
    detail.put("actions", actions);
    return this;
  }

  public AuthorizationDetailRequestBuilder addLocations(List<String> locations) {
    detail.put("locations", locations);
    return this;
  }

  public AuthorizationDetailRequestBuilder add(String key, Object value) {
    this.detail.put(key, value);
    return this;
  }

  public Map<String, Object> build() {
    return detail;
  }
}
