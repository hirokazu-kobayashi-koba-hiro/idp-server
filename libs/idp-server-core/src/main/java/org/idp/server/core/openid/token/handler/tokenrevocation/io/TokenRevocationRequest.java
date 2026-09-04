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

package org.idp.server.core.openid.token.handler.tokenrevocation.io;

import java.util.Map;
import org.idp.server.core.openid.oauth.type.mtls.ClientCert;
import org.idp.server.core.openid.oauth.type.oauth.ClientSecretBasic;
import org.idp.server.core.openid.oauth.type.oauth.RequestedClientId;
import org.idp.server.core.openid.token.AuthorizationHeaderHandlerable;
import org.idp.server.core.openid.token.tokenrevocation.TokenRevocationRequestParameters;
import org.idp.server.platform.http.BasicAuth;
import org.idp.server.platform.http.HttpRequestInputs;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class TokenRevocationRequest implements AuthorizationHeaderHandlerable {

  Tenant tenant;
  HttpRequestInputs inputs;

  public TokenRevocationRequest(Tenant tenant, HttpRequestInputs inputs) {
    this.tenant = tenant;
    this.inputs = inputs;
  }

  public String getAuthorizationHeaders() {
    return inputs.authorizationHeader();
  }

  public Map<String, String[]> getParams() {
    return inputs.bodyParameters();
  }

  public String getClientCert() {
    return inputs.tlsClientCertPem();
  }

  public Tenant tenant() {
    return tenant;
  }

  public RequestedClientId clientId() {
    TokenRevocationRequestParameters parameters = toParameters();

    String authorizationHeaders = inputs.authorizationHeader();
    if (isBasicAuth(authorizationHeaders)) {
      BasicAuth basicAuth = convertClientSecretBasicAuth(authorizationHeaders);
      return new RequestedClientId(basicAuth.username());
    }

    if (parameters.hasClientId()) {
      return parameters.clientId();
    }

    return new RequestedClientId();
  }

  public ClientSecretBasic clientSecretBasic() {
    String authorizationHeaders = inputs.authorizationHeader();
    if (isBasicAuth(authorizationHeaders)) {
      return new ClientSecretBasic(convertClientSecretBasicAuth(authorizationHeaders));
    }
    return new ClientSecretBasic();
  }

  public TokenRevocationRequestParameters toParameters() {
    return new TokenRevocationRequestParameters(inputs.bodyParameters());
  }

  public ClientCert toClientCert() {
    return new ClientCert(inputs.tlsClientCertPem());
  }
}
