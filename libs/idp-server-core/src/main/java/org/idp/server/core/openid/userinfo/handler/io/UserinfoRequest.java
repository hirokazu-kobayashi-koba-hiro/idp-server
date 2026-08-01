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

package org.idp.server.core.openid.userinfo.handler.io;

import java.util.List;
import org.idp.server.core.openid.oauth.dpop.DPoPProof;
import org.idp.server.core.openid.oauth.type.mtls.ClientCert;
import org.idp.server.core.openid.oauth.type.oauth.AccessTokenEntity;
import org.idp.server.core.openid.token.AuthorizationHeaderHandlerable;
import org.idp.server.platform.http.HttpRequestInputs;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class UserinfoRequest implements AuthorizationHeaderHandlerable {
  Tenant tenant;
  HttpRequestInputs inputs;

  public UserinfoRequest(Tenant tenant, HttpRequestInputs inputs) {
    this.tenant = tenant;
    this.inputs = inputs;
  }

  public String getAuthorizationHeaders() {
    return inputs.authorizationHeader();
  }

  public String getClientCert() {
    return inputs.tlsClientCertPem();
  }

  public Tenant tenant() {
    return tenant;
  }

  public AccessTokenEntity toAccessToken() {
    return extractAccessToken(inputs.authorizationHeader());
  }

  public ClientCert toClientCert() {
    return new ClientCert(inputs.tlsClientCertPem());
  }

  public DPoPProof dpopProof() {
    List<String> dpopProofHeaders = dpopProofHeaders();
    if (dpopProofHeaders.isEmpty()) {
      return new DPoPProof();
    }
    return new DPoPProof(dpopProofHeaders.get(0));
  }

  public List<String> dpopProofHeaders() {
    return inputs.headerValues("DPoP");
  }

  public String httpMethod() {
    return inputs.httpMethod() != null ? inputs.httpMethod() : "GET";
  }

  public String httpUri() {
    return inputs.httpUri() != null ? inputs.httpUri() : "";
  }

  public boolean hasToken() {
    AccessTokenEntity accessToken = toAccessToken();
    return accessToken != null && accessToken.exists();
  }
}
