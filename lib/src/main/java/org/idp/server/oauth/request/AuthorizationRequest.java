package org.idp.server.oauth.request;

import org.idp.server.oauth.AuthorizationProfile;
import org.idp.server.oauth.identity.ClaimsPayload;
import org.idp.server.oauth.rar.AuthorizationDetails;
import org.idp.server.type.oauth.*;
import org.idp.server.type.oidc.*;
import org.idp.server.type.pkce.CodeChallenge;
import org.idp.server.type.pkce.CodeChallengeMethod;

/** AuthorizationRequest */
public class AuthorizationRequest {

  AuthorizationRequestIdentifier identifier = new AuthorizationRequestIdentifier();
  TokenIssuer tokenIssuer;
  AuthorizationProfile profile;
  Scopes scopes;
  ResponseType responseType;
  ClientId clientId;
  RedirectUri redirectUri;
  State state;
  ResponseMode responseMode;
  Nonce nonce;
  Display display;
  Prompts prompts;
  MaxAge maxAge;
  UiLocales uiLocales;
  IdTokenHint idTokenHint;
  LoginHint loginHint;
  AcrValues acrValues;
  ClaimsValue claimsValue;
  RequestObject requestObject;
  RequestUri requestUri;
  ClaimsPayload claimsPayload;
  CodeChallenge codeChallenge;
  CodeChallengeMethod codeChallengeMethod;
  AuthorizationDetails authorizationDetails;

  public AuthorizationRequest() {}

  AuthorizationRequest(
      AuthorizationRequestIdentifier identifier,
      TokenIssuer tokenIssuer,
      AuthorizationProfile profile,
      Scopes scopes,
      ResponseType responseType,
      ClientId clientId,
      RedirectUri redirectUri,
      State state,
      ResponseMode responseMode,
      Nonce nonce,
      Display display,
      Prompts prompts,
      MaxAge maxAge,
      UiLocales uiLocales,
      IdTokenHint idTokenHint,
      LoginHint loginHint,
      AcrValues acrValues,
      ClaimsValue claimsValue,
      RequestObject requestObject,
      RequestUri requestUri,
      ClaimsPayload claimsPayload,
      CodeChallenge codeChallenge,
      CodeChallengeMethod codeChallengeMethod,
      AuthorizationDetails authorizationDetails) {
    this.identifier = identifier;
    this.tokenIssuer = tokenIssuer;
    this.profile = profile;
    this.scopes = scopes;
    this.responseType = responseType;
    this.clientId = clientId;
    this.redirectUri = redirectUri;
    this.state = state;
    this.responseMode = responseMode;
    this.nonce = nonce;
    this.display = display;
    this.prompts = prompts;
    this.maxAge = maxAge;
    this.uiLocales = uiLocales;
    this.idTokenHint = idTokenHint;
    this.loginHint = loginHint;
    this.acrValues = acrValues;
    this.claimsValue = claimsValue;
    this.requestObject = requestObject;
    this.requestUri = requestUri;
    this.claimsPayload = claimsPayload;
    this.codeChallenge = codeChallenge;
    this.codeChallengeMethod = codeChallengeMethod;
    this.authorizationDetails = authorizationDetails;
  }

  public AuthorizationRequestIdentifier identifier() {
    return identifier;
  }

  public TokenIssuer tokenIssuer() {
    return tokenIssuer;
  }

  public AuthorizationProfile profile() {
    return profile;
  }

  public Scopes scope() {
    return scopes;
  }

  public ResponseType responseType() {
    return responseType;
  }

  public ClientId clientId() {
    return clientId;
  }

  public RedirectUri redirectUri() {
    return redirectUri;
  }

  public State state() {
    return state;
  }

  public ResponseMode responseMode() {
    return responseMode;
  }

  public Nonce nonce() {
    return nonce;
  }

  public Display display() {
    return display;
  }

  public Prompts prompts() {
    return prompts;
  }

  public MaxAge maxAge() {
    return maxAge;
  }

  public UiLocales uiLocales() {
    return uiLocales;
  }

  public IdTokenHint idTokenHint() {
    return idTokenHint;
  }

  public LoginHint loginHint() {
    return loginHint;
  }

  public AcrValues acrValues() {
    return acrValues;
  }

  public ClaimsValue claims() {
    return claimsValue;
  }

  public RequestObject request() {
    return requestObject;
  }

  public RequestUri requestUri() {
    return requestUri;
  }

  public ClaimsPayload claimsPayload() {
    return claimsPayload;
  }

  public CodeChallenge codeChallenge() {
    return codeChallenge;
  }

  public CodeChallengeMethod codeChallengeMethod() {
    return codeChallengeMethod;
  }

  public AuthorizationDetails authorizationDetails() {
    return authorizationDetails;
  }

  public boolean hasRedirectUri() {
    return redirectUri.exists();
  }

  public boolean isInvalidDisplay() {
    return display.isUnknown();
  }

  public boolean isInvalidPrompt() {
    return prompts.hasUnknown();
  }

  public boolean isInvalidMaxAge() {
    return !maxAge.isValid();
  }

  public boolean hasState() {
    return state.exists();
  }

  public boolean hasNonce() {
    return nonce.exists();
  }

  public boolean isOidcProfile() {
    return scopes.contains("openid");
  }

  public boolean hasCodeChallenge() {
    return codeChallenge.exists();
  }

  public boolean hasAuthorizationDetails() {
    return authorizationDetails.exists();
  }

  public boolean exists() {
    return identifier.exists();
  }

  public boolean isPkceRequest() {
    return hasCodeChallenge();
  }

  public boolean isPkceWithS256() {
    return codeChallengeMethod.isS256();
  }

  public boolean isPromptNone() {
    return prompts().hasNone();
  }

  public boolean isPromptLogin() {
    return prompts().hasLogin();
  }

  public boolean hasMaxAge() {
    return maxAge.exists();
  }
}
