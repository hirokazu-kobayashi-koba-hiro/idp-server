package org.idp.server.oauth.response;

import org.idp.server.basic.http.QueryParams;
import org.idp.server.token.AccessTokenPayload;
import org.idp.server.type.extension.ResponseModeValue;
import org.idp.server.type.oauth.*;

public class AuthorizationResponse {
  RedirectUri redirectUri;
  ResponseModeValue responseModeValue;
  AuthorizationCode authorizationCode;
  State state;
  AccessToken accessToken;
  AccessTokenPayload accessTokenPayload;
  TokenType tokenType;
  ExpiresIn expiresIn;
  TokenIssuer tokenIssuer;
  QueryParams queryParams;

  AuthorizationResponse(
      RedirectUri redirectUri,
      ResponseModeValue responseModeValue,
      AuthorizationCode authorizationCode,
      State state,
      AccessToken accessToken,
      AccessTokenPayload accessTokenPayload,
      TokenType tokenType,
      ExpiresIn expiresIn,
      TokenIssuer tokenIssuer,
      QueryParams queryParams) {
    this.redirectUri = redirectUri;
    this.responseModeValue = responseModeValue;
    this.authorizationCode = authorizationCode;
    this.state = state;
    this.accessToken = accessToken;
    this.accessTokenPayload = accessTokenPayload;
    this.tokenType = tokenType;
    this.expiresIn = expiresIn;
    this.tokenIssuer = tokenIssuer;
    this.queryParams = queryParams;
  }

  public RedirectUri redirectUri() {
    return redirectUri;
  }

  public ResponseModeValue responseModeValue() {
    return responseModeValue;
  }

  public AuthorizationCode authorizationCode() {
    return authorizationCode;
  }

  public State state() {
    return state;
  }

  public TokenIssuer tokenIssuer() {
    return tokenIssuer;
  }

  public AccessToken accessToken() {
    return accessToken;
  }

  public AccessTokenPayload accessTokenPayload() {
    return accessTokenPayload;
  }

  public TokenType tokenType() {
    return tokenType;
  }

  public ExpiresIn expiresIn() {
    return expiresIn;
  }

  QueryParams queryParams() {
    return queryParams;
  }

  public String redirectUriValue() {
    return String.format(
        "%s%s%s", redirectUri.value(), responseModeValue.value(), queryParams.params());
  }

  public boolean hasAuthorizationCode() {
    return authorizationCode.exists();
  }

  public boolean hasAccessToken() {
    return accessToken.exists();
  }
}
