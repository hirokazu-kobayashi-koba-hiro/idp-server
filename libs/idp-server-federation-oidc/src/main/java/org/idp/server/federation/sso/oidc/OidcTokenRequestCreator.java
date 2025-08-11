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

import org.idp.server.core.openid.federation.FederationCallbackParameters;
import org.idp.server.core.openid.federation.sso.oidc.OidcSsoSession;

public class OidcTokenRequestCreator {

  FederationCallbackParameters parameters;
  OidcSsoSession session;
  OidcSsoConfiguration configuration;

  public OidcTokenRequestCreator(
      FederationCallbackParameters parameters,
      OidcSsoSession session,
      OidcSsoConfiguration configuration) {
    this.parameters = parameters;
    this.session = session;
    this.configuration = configuration;
  }

  public OidcTokenRequest create() {
    String endpoint = configuration.tokenEndpoint();
    String code = parameters.code();
    String clientId = configuration.clientId();
    String clientSecret = configuration.clientSecret();
    String redirectUri = session.redirectUri();
    String grantType = "authorization_code";
    String clientAuthenticationType = configuration.clientAuthenticationType();
    return new OidcTokenRequest(
        endpoint, code, clientId, clientSecret, redirectUri, grantType, clientAuthenticationType);
  }
}
