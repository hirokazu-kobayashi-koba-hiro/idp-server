package org.idp.server.core.oidc.federation.sso.oidc;

import org.idp.server.core.oidc.federation.FederationCallbackParameters;

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
