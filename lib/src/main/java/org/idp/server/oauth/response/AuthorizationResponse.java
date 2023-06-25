package org.idp.server.oauth.response;

import org.idp.server.basic.http.QueryParams;
import org.idp.server.oauth.token.AccessToken;
import org.idp.server.type.extension.JarmPayload;
import org.idp.server.type.extension.ResponseModeValue;
import org.idp.server.type.oauth.*;
import org.idp.server.type.oidc.IdToken;
import org.idp.server.type.oidc.ResponseMode;

public class AuthorizationResponse {
  RedirectUri redirectUri;
  ResponseMode responseMode;
  ResponseModeValue responseModeValue;
  AuthorizationCode authorizationCode;
  State state;
  AccessToken accessToken;
  TokenType tokenType;
  ExpiresIn expiresIn;
  Scopes scopes;
  IdToken idToken;
  TokenIssuer tokenIssuer;
  JarmPayload jarmPayload;
  QueryParams queryParams;

  AuthorizationResponse(
      RedirectUri redirectUri,
      ResponseMode responseMode,
      ResponseModeValue responseModeValue,
      AuthorizationCode authorizationCode,
      State state,
      AccessToken accessToken,
      TokenType tokenType,
      ExpiresIn expiresIn,
      Scopes scopes,
      IdToken idToken,
      TokenIssuer tokenIssuer,
      JarmPayload jarmPayload,
      QueryParams queryParams) {
    this.redirectUri = redirectUri;
    this.responseMode = responseMode;
    this.responseModeValue = responseModeValue;
    this.authorizationCode = authorizationCode;
    this.state = state;
    this.accessToken = accessToken;
    this.tokenType = tokenType;
    this.expiresIn = expiresIn;
    this.scopes = scopes;
    this.idToken = idToken;
    this.tokenIssuer = tokenIssuer;
    this.jarmPayload = jarmPayload;
    this.queryParams = queryParams;
  }

  public RedirectUri redirectUri() {
    return redirectUri;
  }

  public ResponseMode responseMode() {
    return responseMode;
  }

  public ResponseModeValue responseModeValue() {
    return responseModeValue;
  }

  public AuthorizationCode authorizationCode() {
    return authorizationCode;
  }

  public boolean hasAuthorizationCode() {
    return authorizationCode.exists();
  }

  public State state() {
    return state;
  }

  public boolean hasState() {
    return state.exists();
  }

  public TokenIssuer tokenIssuer() {
    return tokenIssuer;
  }

  public AccessToken accessToken() {
    return accessToken;
  }

  public boolean hasAccessToken() {
    return accessToken.exists();
  }

  public TokenType tokenType() {
    return tokenType;
  }

  public ExpiresIn expiresIn() {
    return expiresIn;
  }

  public Scopes scopes() {
    return scopes;
  }

  public IdToken idToken() {
    return idToken;
  }

  public boolean hasIdToken() {
    return idToken.exists();
  }

  public JarmPayload jarmPayload() {
    return jarmPayload;
  }

  public boolean hasJarm() {
    return jarmPayload.exists();
  }

  QueryParams queryParams() {
    return queryParams;
  }

  public String redirectUriValue() {
    return String.format(
        "%s%s%s", redirectUri.value(), responseModeValue.value(), queryParams.params());
  }
}
