package org.idp.server.core.federation;

public class FederationTokenRequestCreator {

  FederationCallbackParameters parameters;
  FederationSession session;
  FederatableIdProviderConfiguration configuration;

  public FederationTokenRequestCreator(
      FederationCallbackParameters parameters,
      FederationSession session,
      FederatableIdProviderConfiguration configuration) {
    this.parameters = parameters;
    this.session = session;
    this.configuration = configuration;
  }

  public FederationTokenRequest create() {
    String endpoint = configuration.tokenEndpoint();
    String code = parameters.code();
    String clientId = configuration.clientId();
    String clientSecret = configuration.clientSecret();
    String redirectUri = session.redirectUri();
    String grantType = "authorization_code";
    String clientAuthenticationType = configuration.clientAuthenticationType();
    return new FederationTokenRequest(
        endpoint, code, clientId, clientSecret, redirectUri, grantType, clientAuthenticationType);
  }
}
