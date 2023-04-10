package org.idp.server.core.oauth;

import org.idp.server.basic.jose.JoseContext;
import org.idp.server.core.configuration.ClientConfiguration;
import org.idp.server.core.configuration.ServerConfiguration;
import org.idp.server.core.oauth.request.AuthorizationRequest;
import org.idp.server.core.oauth.request.AuthorizationRequestIdentifier;
import org.idp.server.core.type.OAuthRequestKey;
import org.idp.server.core.type.OAuthRequestParameters;
import org.idp.server.core.type.oauth.*;
import org.idp.server.core.type.oidc.ResponseMode;

/** OAuthRequestContext */
public class OAuthRequestContext {

  OAuthRequestPattern pattern;
  OAuthRequestParameters parameters;
  JoseContext joseContext;
  AuthorizationRequest authorizationRequest;
  ServerConfiguration serverConfiguration;
  ClientConfiguration clientConfiguration;

  public OAuthRequestContext() {}

  public OAuthRequestContext(
      OAuthRequestPattern pattern,
      OAuthRequestParameters parameters,
      JoseContext joseContext,
      AuthorizationRequest authorizationRequest,
      ServerConfiguration serverConfiguration,
      ClientConfiguration clientConfiguration) {
    this.pattern = pattern;
    this.parameters = parameters;
    this.joseContext = joseContext;
    this.authorizationRequest = authorizationRequest;
    this.serverConfiguration = serverConfiguration;
    this.clientConfiguration = clientConfiguration;
  }

  public AuthorizationRequestIdentifier identifier() {
    return authorizationRequest.identifier();
  }

  public AuthorizationProfile profile() {
    return authorizationRequest.profile();
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
    return profile().isOAuth2();
  }

  public boolean isOidcProfile() {
    return profile().isOidc();
  }

  public boolean isFapiBaselineProfile() {
    return profile().isFapiBaseline();
  }

  public boolean isFapiAdvanceProfile() {
    return profile().isFapiAdvance();
  }

  public ResponseType responseType() {
    return authorizationRequest.responseType();
  }

  public ResponseMode responseMode() {
    return authorizationRequest.responseMode();
  }

  public boolean isSupportedResponseTypeWithServer() {
    ResponseType responseType = responseType();
    return serverConfiguration.isSupportedResponseType(responseType);
  }

  public boolean isSupportedResponseTypeWithClient() {
    ResponseType responseType = responseType();
    return clientConfiguration.isSupportedResponseType(responseType);
  }

  public boolean isRegisteredRedirectUri() {
    RedirectUri redirectUri = redirectUri();
    return clientConfiguration.isRegisteredRedirectUri(redirectUri.value());
  }

  public Scopes scopes() {
    return authorizationRequest.scope();
  }

  public RedirectUri redirectUri() {
    return authorizationRequest.redirectUri();
  }

  public TokenIssuer tokenIssuer() {
    return serverConfiguration.issuer();
  }

  public State state() {
    return authorizationRequest.state();
  }

  public String getParams(OAuthRequestKey key) {
    return parameters.getString(key);
  }

  public boolean hasRedirectUri() {
    return authorizationRequest.hasRedirectUri();
  }
}
