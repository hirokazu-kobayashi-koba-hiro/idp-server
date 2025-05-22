/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.federation.sso.oidc;

import org.idp.server.core.oidc.federation.FederationCallbackParameters;
import org.idp.server.core.oidc.federation.sso.oidc.OidcSsoSession;

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
