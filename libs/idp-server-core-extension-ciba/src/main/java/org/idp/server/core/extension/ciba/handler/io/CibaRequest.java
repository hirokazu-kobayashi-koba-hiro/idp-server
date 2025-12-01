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

package org.idp.server.core.extension.ciba.handler.io;

import java.util.Map;
import org.idp.server.core.extension.ciba.CibaRequestParameters;
import org.idp.server.core.openid.oauth.type.mtls.ClientCert;
import org.idp.server.core.openid.oauth.type.oauth.ClientSecretBasic;
import org.idp.server.core.openid.oauth.type.oauth.RequestedClientId;
import org.idp.server.core.openid.token.AuthorizationHeaderHandlerable;
import org.idp.server.platform.http.BasicAuth;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class CibaRequest implements AuthorizationHeaderHandlerable {

  Tenant tenant;
  String authorizationHeaders;
  Map<String, String[]> params;
  String clientCert;

  public CibaRequest(Tenant tenant, String authorizationHeaders, Map<String, String[]> params) {
    this.tenant = tenant;
    this.authorizationHeaders = authorizationHeaders;
    this.params = params;
  }

  public CibaRequest setClientCert(String clientCert) {
    this.clientCert = clientCert;
    return this;
  }

  public Map<String, String[]> getParams() {
    return params;
  }

  public String clientCert() {
    return clientCert;
  }

  public Tenant tenant() {
    return tenant;
  }

  public CibaRequestParameters toParameters() {
    return new CibaRequestParameters(params);
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
    CibaRequestParameters parameters = toParameters();
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

  public ClientCert toClientCert() {
    return new ClientCert(clientCert);
  }

  public boolean hasClientId() {
    RequestedClientId requestedClientId = clientId();
    return requestedClientId.exists();
  }
}
