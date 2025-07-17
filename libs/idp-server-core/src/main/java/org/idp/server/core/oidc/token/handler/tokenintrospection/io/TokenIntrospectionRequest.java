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

package org.idp.server.core.oidc.token.handler.tokenintrospection.io;

import java.util.Map;
import org.idp.server.core.oidc.token.AuthorizationHeaderHandlerable;
import org.idp.server.core.oidc.token.tokenintrospection.TokenIntrospectionRequestParameters;
import org.idp.server.core.oidc.type.mtls.ClientCert;
import org.idp.server.core.oidc.type.oauth.ClientSecretBasic;
import org.idp.server.core.oidc.type.oauth.RequestedClientId;
import org.idp.server.core.oidc.type.oauth.Scopes;
import org.idp.server.platform.http.BasicAuth;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class TokenIntrospectionRequest implements AuthorizationHeaderHandlerable {
  Tenant tenant;
  String authorizationHeaders;
  Map<String, String[]> params;
  String clientCert;

  public TokenIntrospectionRequest(
      Tenant tenant, String authorizationHeaders, Map<String, String[]> params) {
    this.tenant = tenant;
    this.authorizationHeaders = authorizationHeaders;
    this.params = params;
  }

  public TokenIntrospectionRequest setClientCert(String clientCert) {
    this.clientCert = clientCert;
    return this;
  }

  public String getAuthorizationHeaders() {
    return authorizationHeaders;
  }

  public Map<String, String[]> getParams() {
    return params;
  }

  public String getClientCert() {
    return clientCert;
  }

  public TokenIntrospectionRequestParameters toParameters() {
    return new TokenIntrospectionRequestParameters(params);
  }

  public Tenant tenant() {
    return tenant;
  }

  public RequestedClientId clientId() {
    TokenIntrospectionRequestParameters parameters = toParameters();

    if (isBasicAuth(authorizationHeaders)) {
      BasicAuth basicAuth = convertBasicAuth(authorizationHeaders);
      return new RequestedClientId(basicAuth.username());
    }

    if (parameters.hasClientId()) {
      return parameters.clientId();
    }

    return new RequestedClientId();
  }

  public ClientSecretBasic clientSecretBasic() {
    if (isBasicAuth(authorizationHeaders)) {
      return new ClientSecretBasic(convertBasicAuth(authorizationHeaders));
    }
    return new ClientSecretBasic();
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

  public Scopes scopes() {
    if (hasScope()) {
      String scopes = params.get("scope")[0];

      return new Scopes(scopes);
    }
    return new Scopes();
  }

  public boolean hasScope() {
    return params.containsKey("scope");
  }

  public boolean hasClientCert() {
    return params.containsKey("client_cert");
  }

  public ClientCert toClientCert() {
    return new ClientCert(clientCert);
  }
}
