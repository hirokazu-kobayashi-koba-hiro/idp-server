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

package org.idp.server.core.openid.token.handler.tokenintrospection.io;

import org.idp.server.core.openid.oauth.type.mtls.ClientCert;
import org.idp.server.core.openid.oauth.type.oauth.AccessTokenEntity;
import org.idp.server.core.openid.token.AuthorizationHeaderHandlerable;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class TokenIntrospectionInternalRequest implements AuthorizationHeaderHandlerable {
  Tenant tenant;
  String authorizationHeaders;
  String clientCert;

  public TokenIntrospectionInternalRequest(
      Tenant tenant, String authorizationHeaders, String clientCert) {
    this.tenant = tenant;
    this.authorizationHeaders = authorizationHeaders;
    this.clientCert = clientCert;
  }

  public String getAuthorizationHeaders() {
    return authorizationHeaders;
  }

  public String getClientCert() {
    return clientCert;
  }

  public Tenant tenant() {
    return tenant;
  }

  public ClientCert toClientCert() {
    return new ClientCert(clientCert);
  }

  public AccessTokenEntity accessToken() {
    return extractAccessToken(authorizationHeaders);
  }
}
