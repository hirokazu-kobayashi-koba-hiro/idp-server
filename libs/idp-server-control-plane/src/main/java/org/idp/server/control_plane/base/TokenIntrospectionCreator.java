/*
 * Copyright 2025 Hirokazu Kobayashi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
