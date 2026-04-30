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
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class UserinfoRequest implements AuthorizationHeaderHandlerable {
  Tenant tenant;
  String authorizationHeaders;
  String clientCert;
  List<String> dpopProofHeaders;
  String httpMethod;
  String httpUri;

  public UserinfoRequest(Tenant tenant, String authorizationHeaders) {
    this.tenant = tenant;
    this.authorizationHeaders = authorizationHeaders;
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

  public AccessTokenEntity toAccessToken() {
    return extractAccessToken(authorizationHeaders);
  }

  public ClientCert toClientCert() {
    return new ClientCert(clientCert);
  }

  public UserinfoRequest setClientCert(String clientCert) {
    this.clientCert = clientCert;
    return this;
  }

  public DPoPProof dpopProof() {
    if (dpopProofHeaders == null || dpopProofHeaders.isEmpty()) {
      return new DPoPProof();
    }
    return new DPoPProof(dpopProofHeaders.get(0));
  }

  public List<String> dpopProofHeaders() {
    return dpopProofHeaders;
  }

  public UserinfoRequest setDPoPProofHeaders(List<String> dpopProofHeaders) {
    this.dpopProofHeaders = dpopProofHeaders;
    return this;
  }

  public String httpMethod() {
    return httpMethod != null ? httpMethod : "GET";
  }

  public UserinfoRequest setHttpMethod(String httpMethod) {
    this.httpMethod = httpMethod;
    return this;
  }

  public String httpUri() {
    return httpUri != null ? httpUri : "";
  }

  public UserinfoRequest setHttpUri(String httpUri) {
    this.httpUri = httpUri;
    return this;
  }

  public boolean hasToken() {
    AccessTokenEntity accessToken = toAccessToken();
    return accessToken != null && accessToken.exists();
  }
}
