package org.idp.server.core.oauth;

import org.idp.server.basic.jose.JoseContext;
import org.idp.server.core.configuration.ClientConfiguration;
import org.idp.server.core.configuration.ServerConfiguration;
import org.idp.server.core.oauth.request.AuthorizationRequest;
import org.idp.server.core.type.OAuthRequestParameters;

/** OAuthRequestContext */
public class OAuthRequestContext {
  AuthorizationProfile profile;
  OAuthRequestPattern pattern;
  OAuthRequestParameters parameters;
  JoseContext joseContext;
  AuthorizationRequest authorizationRequest;
  ServerConfiguration serverConfiguration;
  ClientConfiguration clientConfiguration;

  public OAuthRequestContext() {}

  public OAuthRequestContext(
      AuthorizationProfile profile,
      OAuthRequestPattern pattern,
      OAuthRequestParameters parameters,
      JoseContext joseContext,
      AuthorizationRequest authorizationRequest,
      ServerConfiguration serverConfiguration,
      ClientConfiguration clientConfiguration) {
    this.profile = profile;
    this.pattern = pattern;
    this.parameters = parameters;
    this.joseContext = joseContext;
    this.authorizationRequest = authorizationRequest;
    this.serverConfiguration = serverConfiguration;
    this.clientConfiguration = clientConfiguration;
  }

  public AuthorizationProfile profile() {
    return profile;
  }

  public OAuthRequestPattern pattern() {
    return pattern;
  }

  public OAuthRequestParameters parameters() {
    return parameters;
  }

  public JoseContext joseContext() {
    return joseContext;
  }

  public AuthorizationRequest authorizationRequest() {
    return authorizationRequest;
  }

  public ServerConfiguration serverConfiguration() {
    return serverConfiguration;
  }

  public ClientConfiguration clientConfiguration() {
    return clientConfiguration;
  }

  public boolean isRequestParameterPattern() {
    return pattern.isRequestParameter();
  }

  public boolean isOAuth2Profile() {
    return profile.isOAuth2();
  }

  public boolean isOidcProfile() {
    return profile.isOidc();
  }

  public boolean isFapiBaselineProfile() {
    return profile.isFapiBaseline();
  }

  public boolean isFapiAdvanceProfile() {
    return profile.isFapiAdvance();
  }
}
