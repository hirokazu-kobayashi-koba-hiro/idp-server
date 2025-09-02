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
import org.idp.server.core.openid.federation.FederationType;
import org.idp.server.core.openid.federation.sso.SsoProvider;
import org.idp.server.core.openid.federation.sso.SsoState;
import org.idp.server.core.openid.federation.sso.SsoStateCoder;
import org.idp.server.core.openid.federation.sso.oidc.OidcSsoSession;
import org.idp.server.core.openid.oauth.request.AuthorizationRequestIdentifier;
import org.idp.server.platform.http.HttpQueryParams;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class OAuthExtensionSsoSessionCreator {

  OidcSsoConfiguration configuration;
  Tenant tenant;
  AuthorizationRequestIdentifier authorizationRequestIdentifier;
  FederationType federationType;
  SsoProvider ssoProvider;

  public OAuthExtensionSsoSessionCreator(
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
    SsoState ssoState =
        new SsoState(
            sessionId, authorizationRequestIdentifier.value(), tenantId, ssoProvider.name());
    String state = SsoStateCoder.encode(ssoState);

    HttpQueryParams httpQueryParams = new HttpQueryParams();
    httpQueryParams.add("client_id", configuration.clientId());
    httpQueryParams.add("redirect_uri", configuration.redirectUri());
    httpQueryParams.add("response_type", "code");
    httpQueryParams.add("state", state);
    httpQueryParams.add("scope", configuration.scopeAsString());

    String authorizationRequestUri =
        String.format("%s?%s", authorizationEndpoint, httpQueryParams.params());

    return new OidcSsoSession(
        sessionId,
        authorizationRequestIdentifier.value(),
        tenant.identifierValue(),
        tenant.tokenIssuer(),
        state,
        "",
        configuration.type(),
        configuration.clientId(),
        configuration.redirectUri(),
        authorizationRequestUri);
  }
}
