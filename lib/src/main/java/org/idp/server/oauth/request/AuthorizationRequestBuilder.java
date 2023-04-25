package org.idp.server.oauth.request;

import org.idp.server.oauth.AuthorizationProfile;
import org.idp.server.oauth.identity.ClaimsPayload;
import org.idp.server.type.oauth.*;
import org.idp.server.type.oidc.*;

/** AuthorizationRequestBuilder */
public class AuthorizationRequestBuilder {

  AuthorizationRequestIdentifier identifier;
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
  Prompt prompt;
  MaxAge maxAge;
  UiLocales uiLocales;
  IdTokenHint idTokenHint;
  LoginHint loginHint;
  AcrValues acrValues;
  ClaimsValue claimsValue;
  RequestObject requestObject;
  RequestUri requestUri;
  ClaimsPayload claimsPayload;

  public AuthorizationRequestBuilder() {}

  public AuthorizationRequestBuilder add(AuthorizationRequestIdentifier identifier) {
    this.identifier = identifier;
    return this;
  }

  public AuthorizationRequestBuilder add(TokenIssuer tokenIssuer) {
    this.tokenIssuer = tokenIssuer;
    return this;
  }

  public AuthorizationRequestBuilder add(AuthorizationProfile profile) {
    this.profile = profile;
    return this;
  }

  public AuthorizationRequestBuilder add(AcrValues acrValues) {
    this.acrValues = acrValues;
    return this;
  }

  public AuthorizationRequestBuilder add(ClaimsValue claimsValue) {
    this.claimsValue = claimsValue;
    return this;
  }

  public AuthorizationRequestBuilder add(ClientId clientId) {
    this.clientId = clientId;
    return this;
  }

  public AuthorizationRequestBuilder add(Display display) {
    this.display = display;
    return this;
  }

  public AuthorizationRequestBuilder add(IdTokenHint idTokenHint) {
    this.idTokenHint = idTokenHint;
    return this;
  }

  public AuthorizationRequestBuilder add(LoginHint loginHint) {
    this.loginHint = loginHint;
    return this;
  }

  public AuthorizationRequestBuilder add(MaxAge maxAge) {
    this.maxAge = maxAge;
    return this;
  }

  public AuthorizationRequestBuilder add(Nonce nonce) {
    this.nonce = nonce;
    return this;
  }

  public AuthorizationRequestBuilder add(Prompt prompt) {
    this.prompt = prompt;
    return this;
  }

  public AuthorizationRequestBuilder add(RedirectUri redirectUri) {
    this.redirectUri = redirectUri;
    return this;
  }

  public AuthorizationRequestBuilder add(RequestObject requestObject) {
    this.requestObject = requestObject;
    return this;
  }

  public AuthorizationRequestBuilder add(RequestUri requestUri) {
    this.requestUri = requestUri;
    return this;
  }

  public AuthorizationRequestBuilder add(ResponseMode responseMode) {
    this.responseMode = responseMode;
    return this;
  }

  public AuthorizationRequestBuilder add(ResponseType responseType) {
    this.responseType = responseType;
    return this;
  }

  public AuthorizationRequestBuilder add(Scopes scopes) {
    this.scopes = scopes;
    return this;
  }

  public AuthorizationRequestBuilder add(State state) {
    this.state = state;
    return this;
  }

  public AuthorizationRequestBuilder add(UiLocales uiLocales) {
    this.uiLocales = uiLocales;
    return this;
  }

  public AuthorizationRequestBuilder add(ClaimsPayload claimsPayload) {
    this.claimsPayload = claimsPayload;
    return this;
  }

  public AuthorizationRequest build() {
    return new AuthorizationRequest(
        identifier,
        tokenIssuer,
        profile,
        scopes,
        responseType,
        clientId,
        redirectUri,
        state,
        responseMode,
        nonce,
        display,
        prompt,
        maxAge,
        uiLocales,
        idTokenHint,
        loginHint,
        acrValues,
        claimsValue,
        requestObject,
        requestUri,
        claimsPayload);
  }
}
