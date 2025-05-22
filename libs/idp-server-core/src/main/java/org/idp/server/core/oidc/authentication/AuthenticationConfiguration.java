/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.oidc.authentication;

import java.util.Map;

public class AuthenticationConfiguration {
  String id;
  String type;
  Map<String, Object> payload;

  public AuthenticationConfiguration() {}

  public AuthenticationConfiguration(String id, String type, Map<String, Object> payload) {
    this.id = id;
    this.type = type;
    this.payload = payload;
  }

  public String id() {
    return id;
  }

  public AuthenticationConfigurationIdentifier identifier() {
    return new AuthenticationConfigurationIdentifier(id);
  }

  public String type() {
    return type;
  }

  public Map<String, Object> payload() {
    return payload;
  }

  public boolean exists() {
    return id != null && !id.isEmpty();
  }
}
