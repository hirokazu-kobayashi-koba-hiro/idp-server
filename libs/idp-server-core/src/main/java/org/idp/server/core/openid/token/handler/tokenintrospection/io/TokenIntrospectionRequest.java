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

import java.util.List;
import java.util.Map;
import org.idp.server.core.openid.oauth.clientattestation.ClientAttestationJwt;
import org.idp.server.core.openid.oauth.clientattestation.ClientAttestationPopJwt;
import org.idp.server.core.openid.oauth.type.mtls.ClientCert;
import org.idp.server.core.openid.oauth.type.oauth.ClientSecretBasic;
import org.idp.server.core.openid.oauth.type.oauth.RequestedClientId;
import org.idp.server.core.openid.oauth.type.oauth.Scopes;
import org.idp.server.core.openid.token.AuthorizationHeaderHandlerable;
import org.idp.server.core.openid.token.tokenintrospection.TokenIntrospectionRequestParameters;
import org.idp.server.platform.http.BasicAuth;
import org.idp.server.platform.http.HttpRequestInputs;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class TokenIntrospectionRequest implements AuthorizationHeaderHandlerable {
  Tenant tenant;
  HttpRequestInputs inputs;

  public TokenIntrospectionRequest(Tenant tenant, HttpRequestInputs inputs) {
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

  public List<String> clientAttestationHeaders() {
    return inputs.headerValues(ClientAttestationJwt.HEADER_NAME);
  }

  public List<String> clientAttestationPopHeaders() {
    return inputs.headerValues(ClientAttestationPopJwt.HEADER_NAME);
  }

  public ClientAttestationJwt toClientAttestationJwt() {
    List<String> headers = clientAttestationHeaders();
    if (headers.isEmpty()) {
      return new ClientAttestationJwt();
    }
    return new ClientAttestationJwt(headers.get(0));
  }

  public ClientAttestationPopJwt toClientAttestationPopJwt() {
    List<String> headers = clientAttestationPopHeaders();
    if (headers.isEmpty()) {
      return new ClientAttestationPopJwt();
    }
    return new ClientAttestationPopJwt(headers.get(0));
  }

  public TokenIntrospectionRequestParameters toParameters() {
    return new TokenIntrospectionRequestParameters(inputs.bodyParameters());
  }

  public Tenant tenant() {
    return tenant;
  }

  public RequestedClientId clientId() {
    TokenIntrospectionRequestParameters parameters = toParameters();

    String authorizationHeaders = inputs.authorizationHeader();
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
    String authorizationHeaders = inputs.authorizationHeader();
    if (isBasicAuth(authorizationHeaders)) {
      return new ClientSecretBasic(convertBasicAuth(authorizationHeaders));
    }
    return new ClientSecretBasic();
  }

  public String token() {
    if (hasToken()) {
      return inputs.bodyParameters().get("token")[0];
    }
    return "";
  }

  public boolean hasToken() {
    return inputs.bodyParameters().containsKey("token");
  }

  public Scopes scopes() {
    if (hasScope()) {
      String scopes = inputs.bodyParameters().get("scope")[0];

      return new Scopes(scopes);
    }
    return new Scopes();
  }

  public boolean hasScope() {
    return inputs.bodyParameters().containsKey("scope");
  }

  public boolean hasClientCert() {
    return inputs.bodyParameters().containsKey("client_cert");
  }

  public ClientCert toClientCert() {
    return new ClientCert(inputs.tlsClientCertPem());
  }
}
