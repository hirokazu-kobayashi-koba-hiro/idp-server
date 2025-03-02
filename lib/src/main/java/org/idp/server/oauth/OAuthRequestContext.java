package org.idp.server.oauth;

import org.idp.server.basic.jose.JoseContext;
import org.idp.server.configuration.ClientConfiguration;
import org.idp.server.configuration.ServerConfiguration;
import org.idp.server.oauth.rar.AuthorizationDetails;
import org.idp.server.oauth.request.AuthorizationRequest;
import org.idp.server.oauth.request.AuthorizationRequestIdentifier;
import org.idp.server.oauth.request.OAuthRequestParameters;
import org.idp.server.oauth.response.ResponseModeDecidable;
import org.idp.server.type.OAuthRequestKey;
import org.idp.server.type.extension.RegisteredRedirectUris;
import org.idp.server.type.extension.ResponseModeValue;
import org.idp.server.type.oauth.*;
import org.idp.server.type.oidc.ResponseMode;

/** OAuthRequestContext */
public class OAuthRequestContext implements ResponseModeDecidable {

  OAuthRequestPattern pattern;
  OAuthRequestParameters parameters;
  JoseContext joseContext;
  AuthorizationRequest authorizationRequest;
  ServerConfiguration serverConfiguration;
  ClientConfiguration clientConfiguration;
  OAuthSessionKey oAuthSessionKey;

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
    this.oAuthSessionKey = authorizationRequest.sessionKey();
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

  public AuthorizationRequestIdentifier authorizationRequestIdentifier() {
    return authorizationRequest.identifier();
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

  public boolean isUnsignedRequestObject() {
    return !joseContext.hasJsonWebSignature();
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
    if (authorizationRequest.hasRedirectUri()) {
      return authorizationRequest.redirectUri();
    }
    return clientConfiguration.getFirstRedirectUri();
  }

  public TokenIssuer tokenIssuer() {
    return serverConfiguration.tokenIssuer();
  }

  public State state() {
    return authorizationRequest.state();
  }

  public String getParams(OAuthRequestKey key) {
    return parameters.getValueOrEmpty(key);
  }

  public boolean hasRedirectUriInRequest() {
    return authorizationRequest.hasRedirectUri();
  }

  public boolean isPckeRequest() {
    return authorizationRequest.hasCodeChallenge();
  }

  public RegisteredRedirectUris registeredRedirectUris() {
    return clientConfiguration.registeredRedirectUris();
  }

  public boolean isMultiRegisteredRedirectUri() {
    return clientConfiguration.isMultiRegisteredRedirectUri();
  }

  public ResponseModeValue responseModeValue() {
    return decideResponseModeValue(responseType(), responseMode());
  }

  public boolean isPromptNone() {
    return authorizationRequest.isPromptNone();
  }

  public boolean isPromptCreate() {
    return authorizationRequest.isPromptCreate();
  }

  public boolean isOidcImplicitFlowOrHybridFlow() {
    return responseType().isOidcImplicitFlow() || responseType().isOidcHybridFlow();
  }

  public boolean isOidcImplicitFlow() {
    return responseType().isOidcImplicitFlow();
  }

  public boolean isWebApplication() {
    return clientConfiguration.isWebApplication();
  }

  public boolean hasAuthorizationDetails() {
    return authorizationRequest.hasAuthorizationDetails();
  }

  public AuthorizationDetails authorizationDetails() {
    return authorizationRequest.authorizationDetails();
  }

  public ClientId clientId() {
    return clientConfiguration.clientId();
  }

  public OAuthSessionKey sessionKey() {
    return oAuthSessionKey;
  }

  public String sessionKeyValue() {
    return oAuthSessionKey.key();
  }

  public boolean isOidcRequest() {
    return scopes().hasOpenidScope();
  }

  public ClientAuthenticationType clientAuthenticationType() {
    return clientConfiguration.clientAuthenticationType();
  }

  public boolean hasOpenidScope() {
    return scopes().hasOpenidScope();
  }

  public boolean isJwtMode() {
    return isJwtMode(authorizationRequest.profile(), responseType(), responseMode());
  }
}
