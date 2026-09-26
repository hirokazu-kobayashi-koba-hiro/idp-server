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
package org.idp.server.core.extension.oid4vci.io;

import java.util.List;
import java.util.Map;
import org.idp.server.core.extension.oid4vci.request.CredentialRequestParameters;
import org.idp.server.core.openid.oauth.dpop.DPoPProof;
import org.idp.server.core.openid.oauth.type.mtls.ClientCert;
import org.idp.server.core.openid.oauth.type.oauth.AccessTokenEntity;
import org.idp.server.core.openid.token.AuthorizationHeaderHandlerable;
import org.idp.server.platform.http.HttpRequestInputs;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

/**
 * A request to the Credential Endpoint: the access token and its sender constraint come from the
 * headers, the Credential Request from the JSON body.
 *
 * <p>The access token is read from the {@code Authorization} header only. A token in the query
 * string is not looked at, so such a request is unauthenticated (RFC 6750 Section 2.3 is not
 * supported).
 */
public class CredentialRequest implements AuthorizationHeaderHandlerable {

  Tenant tenant;
  HttpRequestInputs inputs;
  Map<String, Object> body;

  public CredentialRequest(Tenant tenant, HttpRequestInputs inputs, Map<String, Object> body) {
    this.tenant = tenant;
    this.inputs = inputs;
    this.body = body;
  }

  public Tenant tenant() {
    return tenant;
  }

  public AccessTokenEntity toAccessToken() {
    return extractAccessToken(inputs.authorizationHeader());
  }

  public boolean hasAccessToken() {
    AccessTokenEntity accessToken = toAccessToken();
    return accessToken != null && accessToken.exists();
  }

  public ClientCert toClientCert() {
    return new ClientCert(inputs.tlsClientCertPem());
  }

  public List<String> dpopProofHeaders() {
    return inputs.headerValues("DPoP");
  }

  public DPoPProof dpopProof() {
    List<String> headers = dpopProofHeaders();
    if (headers.isEmpty()) {
      return new DPoPProof();
    }
    return new DPoPProof(headers.get(0));
  }

  public String httpMethod() {
    return inputs.httpMethod() != null ? inputs.httpMethod() : "POST";
  }

  public String httpUri() {
    return inputs.httpUri() != null ? inputs.httpUri() : "";
  }

  public CredentialRequestParameters parameters() {
    return new CredentialRequestParameters(body);
  }
}
