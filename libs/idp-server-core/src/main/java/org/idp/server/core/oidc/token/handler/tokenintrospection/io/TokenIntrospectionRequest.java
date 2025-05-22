/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.oidc.token.handler.tokenintrospection.io;

import java.util.Map;
import org.idp.server.core.oidc.token.tokenintrospection.TokenIntrospectionRequestParameters;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class TokenIntrospectionRequest {
  Tenant tenant;
  Map<String, String[]> params;

  public TokenIntrospectionRequest(Tenant tenant, Map<String, String[]> params) {
    this.tenant = tenant;
    this.params = params;
  }

  public Map<String, String[]> getParams() {
    return params;
  }

  public TokenIntrospectionRequestParameters toParameters() {
    return new TokenIntrospectionRequestParameters(params);
  }

  public Tenant tenant() {
    return tenant;
  }

  public String token() {
    if (hasToken()) {
      return params.get("token")[0];
    }
    return "";
  }

  public boolean hasToken() {
    return params.containsKey("token");
  }
}
