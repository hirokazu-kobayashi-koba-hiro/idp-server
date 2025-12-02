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

package org.idp.server.core.openid.token.handler.token.io;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.core.openid.oauth.type.extension.CustomProperties;
import org.idp.server.core.openid.oauth.type.mtls.ClientCert;
import org.idp.server.core.openid.oauth.type.oauth.ClientSecretBasic;
import org.idp.server.core.openid.oauth.type.oauth.RequestedClientId;
import org.idp.server.core.openid.token.AuthorizationHeaderHandlerable;
import org.idp.server.core.openid.token.TokenRequestParameters;
import org.idp.server.platform.http.BasicAuth;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class TokenRequest implements AuthorizationHeaderHandlerable {
  Tenant tenant;
  String authorizationHeaders;
  Map<String, String[]> params;
  String clientCert;
  Map<String, Object> customProperties = new HashMap<>();

  public TokenRequest(Tenant tenant, String authorizationHeaders, Map<String, String[]> params) {
    this.tenant = tenant;
    this.authorizationHeaders = authorizationHeaders;
    this.params = params;
  }

  public TokenRequest setClientCert(String clientCert) {
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

  public Map<String, Object> customProperties() {
    return customProperties;
  }

  public TokenRequest setCustomProperties(Map<String, Object> customProperties) {
    this.customProperties = customProperties;
    return this;
  }

  /**
   * Extracts the client_id from the request.
   *
   * <p>Per RFC 7521 Section 4.2, when using JWT-based client authentication (private_key_jwt or
   * client_secret_jwt), the client_id parameter is OPTIONAL because the client can be identified by
   * the issuer (iss) claim within the client_assertion JWT.
   *
   * <p>The extraction follows this priority order:
   *
   * <ol>
   *   <li>Explicit client_id parameter in request body
   *   <li>HTTP Basic Authentication header (username)
   *   <li>Issuer (iss) claim from client_assertion JWT (RFC 7523)
   * </ol>
   *
   * @return the extracted client_id, or empty if not found
   * @see <a href="https://datatracker.ietf.org/doc/html/rfc7521#section-4.2">RFC 7521 Section
   *     4.2</a>
   * @see <a href="https://datatracker.ietf.org/doc/html/rfc7523#section-3">RFC 7523 Section 3</a>
   */
  public RequestedClientId clientId() {
    TokenRequestParameters parameters = toParameters();
    if (parameters.hasClientId()) {
      return parameters.clientId();
    }
    if (isBasicAuth(authorizationHeaders)) {
      BasicAuth basicAuth = convertBasicAuth(authorizationHeaders);
      return new RequestedClientId(basicAuth.username());
    }
    // RFC 7521/7523: Extract client_id from client_assertion JWT's iss claim
    if (parameters.hasClientAssertion()) {
      String issuer = parameters.clientAssertion().extractIssuer();
      if (!issuer.isEmpty()) {
        return new RequestedClientId(issuer);
      }
    }
    return new RequestedClientId();
  }

  public ClientSecretBasic clientSecretBasic() {
    if (isBasicAuth(authorizationHeaders)) {
      return new ClientSecretBasic(convertBasicAuth(authorizationHeaders));
    }
    return new ClientSecretBasic();
  }

  public Tenant tenant() {
    return tenant;
  }

  public TokenRequestParameters toParameters() {
    return new TokenRequestParameters(params);
  }

  public CustomProperties toCustomProperties() {
    return new CustomProperties(customProperties);
  }

  public ClientCert toClientCert() {
    return new ClientCert(clientCert);
  }

  public boolean isRefreshTokenGrant() {
    TokenRequestParameters parameters = toParameters();
    return parameters.isRefreshTokenGrant();
  }
}
