package org.idp.server.core.oauth.request;

import org.idp.server.type.*;

/** AuthorizationRequest */
public class AuthorizationRequest {
  Scope scope;
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
  Request request;
  RequestUri requestUri;

  AuthorizationRequest(
      Scope scope,
      ResponseType responseType,
      ClientId clientId,
      RedirectUri redirectUri,
      State state,
      ResponseMode responseMode,
      Nonce nonce,
      Display display,
      Prompt prompt,
      MaxAge maxAge,
      UiLocales uiLocales,
      IdTokenHint idTokenHint,
      LoginHint loginHint,
      AcrValues acrValues,
      Claims claims,
      Request request,
      RequestUri requestUri) {
    this.scope = scope;
    this.responseType = responseType;
    this.clientId = clientId;
    this.redirectUri = redirectUri;
    this.state = state;
    this.responseMode = responseMode;
    this.nonce = nonce;
    this.display = display;
    this.prompt = prompt;
    this.maxAge = maxAge;
    this.uiLocales = uiLocales;
    this.idTokenHint = idTokenHint;
    this.loginHint = loginHint;
    this.acrValues = acrValues;
    this.claims = claims;
    this.request = request;
    this.requestUri = requestUri;
  }

  public Scope scope() {
    return scope;
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

  public Prompt prompt() {
    return prompt;
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

  public Claims claims() {
    return claims;
  }

  public Request request() {
    return request;
  }

  public RequestUri requestUri() {
    return requestUri;
  }
}
