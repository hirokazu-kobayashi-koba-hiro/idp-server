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
import java.util.Map;
import org.idp.server.core.oidc.token.AuthorizationHeaderHandlerable;
import org.idp.server.core.oidc.token.handler.tokenintrospection.io.TokenIntrospectionRequest;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class TokenIntrospectionCreator implements AuthorizationHeaderHandlerable {

  Tenant tenant;
  String authorizationHeader;

  public TokenIntrospectionCreator(Tenant tenant, String authorizationHeader) {
    this.tenant = tenant;
    this.authorizationHeader = authorizationHeader;
  }

  public TokenIntrospectionRequest create() {
    var accessToken = extractAccessToken(authorizationHeader);
    Map<String, String[]> map = new HashMap<>();
    map.put("token", new String[] {accessToken.value()});
    return new TokenIntrospectionRequest(tenant, map);
  }
}
