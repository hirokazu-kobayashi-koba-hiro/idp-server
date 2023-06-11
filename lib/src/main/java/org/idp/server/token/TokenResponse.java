package org.idp.server.token;

import org.idp.server.oauth.rar.AuthorizationDetails;
import org.idp.server.type.oauth.*;
import org.idp.server.type.oidc.IdToken;

public class TokenResponse {
  AccessTokenValue accessTokenValue;
  TokenType tokenType;
  ExpiresIn expiresIn;
  RefreshTokenValue refreshTokenValue;
  Scopes scopes;
  IdToken idToken;
  AuthorizationDetails authorizationDetails;
  String contents;

  TokenResponse(
      AccessTokenValue accessTokenValue,
      TokenType tokenType,
      ExpiresIn expiresIn,
      RefreshTokenValue refreshTokenValue,
      Scopes scopes,
      IdToken idToken,
      AuthorizationDetails authorizationDetails,
      String contents) {
    this.accessTokenValue = accessTokenValue;
    this.tokenType = tokenType;
    this.expiresIn = expiresIn;
    this.refreshTokenValue = refreshTokenValue;
    this.scopes = scopes;
    this.idToken = idToken;
    this.authorizationDetails = authorizationDetails;
    this.contents = contents;
  }

  public AccessTokenValue accessToken() {
    return accessTokenValue;
  }

  public TokenType tokenType() {
    return tokenType;
  }

  public ExpiresIn expiresIn() {
    return expiresIn;
  }

  public RefreshTokenValue refreshToken() {
    return refreshTokenValue;
  }

  public IdToken idToken() {
    return idToken;
  }

  public Scopes scopes() {
    return scopes;
  }

  public AuthorizationDetails authorizationDetails() {
    return authorizationDetails;
  }

  public String contents() {
    return contents;
  }

  public boolean hasRefreshToken() {
    return refreshTokenValue.exists();
  }
}
