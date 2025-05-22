/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.oidc.configuration.authentication;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.basic.json.JsonReadable;

public class AuthenticationResultConditions implements JsonReadable {

  List<AuthenticationResultCondition> anyOf;
  List<AuthenticationResultCondition> allOf;

  public AuthenticationResultConditions() {}

  public AuthenticationResultConditions(
      List<AuthenticationResultCondition> anyOf, List<AuthenticationResultCondition> allOf) {
    this.anyOf = anyOf;
    this.allOf = allOf;
  }

  public List<AuthenticationResultCondition> anyOf() {
    return anyOf;
  }

  public List<AuthenticationResultCondition> allOf() {
    return allOf;
  }

  public boolean hasAnyOf() {
    return anyOf != null && !anyOf.isEmpty();
  }

  public boolean hasAllOf() {
    return allOf != null && !allOf.isEmpty();
  }

  public boolean exists() {
    return hasAnyOf() || hasAllOf();
  }

  public Map<String, Object> toMap() {
    HashMap<String, Object> map = new HashMap<>();
    if (hasAnyOf()) map.put("any_of", anyOf);
    if (hasAllOf()) map.put("all_of", allOf);
    return map;
  }
}
