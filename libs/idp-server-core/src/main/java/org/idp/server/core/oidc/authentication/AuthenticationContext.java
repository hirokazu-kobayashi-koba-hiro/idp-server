/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.oidc.authentication;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.basic.type.oauth.Scopes;
import org.idp.server.basic.type.oidc.AcrValues;

public class AuthenticationContext {
  AcrValues acrValues;
  Scopes scopes;

  public AuthenticationContext() {}

  public AuthenticationContext(String acrValues, String scopes) {
    this.acrValues = new AcrValues(acrValues);
    this.scopes = new Scopes(scopes);
  }

  public AuthenticationContext(AcrValues acrValues, Scopes scopes) {
    this.acrValues = acrValues;
    this.scopes = scopes;
  }

  public AcrValues acrValues() {
    return acrValues;
  }

  public Scopes scopes() {
    return scopes;
  }

  public Map<String, Object> toMap() {
    HashMap<String, Object> map = new HashMap<>();
    map.put("acr_values", acrValues.toStringValues());
    map.put("scopes", scopes.toStringValues());
    return map;
  }
}
