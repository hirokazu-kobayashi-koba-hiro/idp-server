package org.idp.server.core.oauth.request;

import org.idp.server.core.type.*;

/** AuthorizationRequestBuilder */
public class AuthorizationRequestBuilder {
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
  Claims claims;
  RequestObject requestObject;
  RequestUri requestUri;

  public AuthorizationRequestBuilder() {}

  public AuthorizationRequestBuilder add(AcrValues acrValues) {
    this.acrValues = acrValues;
    return this;
  }

  public AuthorizationRequestBuilder add(Claims claims) {
    this.claims = claims;
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

  public AuthorizationRequest build() {
    return new AuthorizationRequest(
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
        claims,
        requestObject,
        requestUri);
  }
}
