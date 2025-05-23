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

package org.idp.server.core.oidc.federation.sso.oidc;

import java.io.Serializable;
import java.util.Objects;
import org.idp.server.basic.json.JsonReadable;
import org.idp.server.core.oidc.federation.sso.SsoSessionIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;

public class OidcSsoSession implements Serializable, JsonReadable {

  String ssoSessionId;
  String authorizationRequestId;
  String tenantId;
  String tokenIssuer;
  String state;
  String nonce;
  String idpId;
  String clientId;
  String redirectUri;
  String authorizationRequestUri;

  public OidcSsoSession() {}

  public OidcSsoSession(
      String ssoSessionId,
      String authorizationRequestId,
      String tenantId,
      String tokenIssuer,
      String state,
      String nonce,
      String idpId,
      String clientId,
      String redirectUri,
      String authorizationRequestUri) {
    this.ssoSessionId = ssoSessionId;
    this.authorizationRequestId = authorizationRequestId;
    this.tenantId = tenantId;
    this.tokenIssuer = tokenIssuer;
    this.state = state;
    this.nonce = nonce;
    this.idpId = idpId;
    this.clientId = clientId;
    this.redirectUri = redirectUri;
    this.authorizationRequestUri = authorizationRequestUri;
  }

  public String authorizationRequestId() {
    return authorizationRequestId;
  }

  public String tenantId() {
    return tenantId;
  }

  public String tokenIssuer() {
    return tokenIssuer;
  }

  public String state() {
    return state;
  }

  public String nonce() {
    return nonce;
  }

  public String idpId() {
    return idpId;
  }

  public String clientId() {
    return clientId;
  }

  public String redirectUri() {
    return redirectUri;
  }

  public String authorizationRequestUri() {
    return authorizationRequestUri;
  }

  public boolean exists() {
    return Objects.nonNull(state) && !state.isEmpty();
  }

  public TenantIdentifier tenantIdentifier() {
    return new TenantIdentifier(tenantId);
  }

  public SsoSessionIdentifier ssoSessionIdentifier() {
    return new SsoSessionIdentifier(ssoSessionId);
  }
}
