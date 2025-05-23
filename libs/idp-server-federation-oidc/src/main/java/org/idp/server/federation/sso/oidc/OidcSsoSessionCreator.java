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

package org.idp.server.federation.sso.oidc;

import java.util.UUID;
import org.idp.server.basic.http.QueryParams;
import org.idp.server.core.oidc.federation.FederationType;
import org.idp.server.core.oidc.federation.sso.SsoProvider;
import org.idp.server.core.oidc.federation.sso.SsoState;
import org.idp.server.core.oidc.federation.sso.SsoStateCoder;
import org.idp.server.core.oidc.federation.sso.oidc.OidcSsoSession;
import org.idp.server.core.oidc.request.AuthorizationRequestIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class OidcSsoSessionCreator {

  OidcSsoConfiguration configuration;
  Tenant tenant;
  AuthorizationRequestIdentifier authorizationRequestIdentifier;
  FederationType federationType;
  SsoProvider ssoProvider;

  public OidcSsoSessionCreator(
      OidcSsoConfiguration oidcSsoConfiguration,
      Tenant tenant,
      AuthorizationRequestIdentifier authorizationRequestIdentifier,
      FederationType federationType,
      SsoProvider ssoProvider) {
    this.configuration = oidcSsoConfiguration;
    this.tenant = tenant;
    this.authorizationRequestIdentifier = authorizationRequestIdentifier;
    this.federationType = federationType;
    this.ssoProvider = ssoProvider;
  }

  public OidcSsoSession create() {
    String authorizationEndpoint = configuration.authorizationEndpoint();
    String sessionId = UUID.randomUUID().toString();
    String tenantId = tenant.identifierValue();
    SsoState ssoState = new SsoState(sessionId, tenantId, ssoProvider.name());
    String state = SsoStateCoder.encode(ssoState);
    String nonce = UUID.randomUUID().toString();

    QueryParams queryParams = new QueryParams();
    queryParams.add("client_id", configuration.clientId());
    queryParams.add("redirect_uri", configuration.redirectUri());
    queryParams.add("response_type", "code");
    queryParams.add("state", state);
    queryParams.add("nonce", nonce);
    queryParams.add("scope", configuration.scopeAsString());

    String authorizationRequestUri =
        String.format("%s?%s", authorizationEndpoint, queryParams.params());

    return new OidcSsoSession(
        sessionId,
        authorizationRequestIdentifier.value(),
        tenant.identifierValue(),
        tenant.tokenIssuer(),
        state,
        nonce,
        configuration.type(),
        configuration.clientId(),
        configuration.redirectUri(),
        authorizationRequestUri);
  }
}
