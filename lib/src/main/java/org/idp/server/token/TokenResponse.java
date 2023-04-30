package org.idp.server.token;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.type.oauth.AccessTokenValue;
import org.idp.server.type.oauth.ExpiresIn;
import org.idp.server.type.oauth.RefreshTokenValue;
import org.idp.server.type.oauth.TokenType;
import org.idp.server.type.oidc.IdToken;

public class TokenResponse {
  AccessTokenValue accessTokenValue;
  TokenType tokenType;
  ExpiresIn expiresIn;
  RefreshTokenValue refreshTokenValue = new RefreshTokenValue();
  IdToken idToken = new IdToken();
  Map<String, Object> response = new HashMap<>();

  public TokenResponse() {}

  TokenResponse(
      AccessTokenValue accessTokenValue,
      TokenType tokenType,
      ExpiresIn expiresIn,
      RefreshTokenValue refreshTokenValue,
      IdToken idToken,
      Map<String, Object> response) {
    this.accessTokenValue = accessTokenValue;
    this.tokenType = tokenType;
    this.expiresIn = expiresIn;
    this.refreshTokenValue = refreshTokenValue;
    this.idToken = idToken;
    this.response = response;
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

  public Map<String, Object> response() {
    return response;
  }

  public boolean hasRefreshToken() {
    return refreshTokenValue.exists();
  }
}
