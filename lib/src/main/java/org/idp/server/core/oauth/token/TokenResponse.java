package org.idp.server.core.oauth.token;

import org.idp.server.core.type.AccessToken;
import org.idp.server.core.type.ExpiresIn;
import org.idp.server.core.type.RefreshToken;
import org.idp.server.core.type.TokenType;

public class TokenResponse {
  AccessToken accessToken;
  TokenType tokenType;
  ExpiresIn expiresIn;
  RefreshToken refreshToken;

  public TokenResponse(
      AccessToken accessToken,
      TokenType tokenType,
      ExpiresIn expiresIn,
      RefreshToken refreshToken) {
    this.accessToken = accessToken;
    this.tokenType = tokenType;
    this.expiresIn = expiresIn;
    this.refreshToken = refreshToken;
  }

  public AccessToken accessToken() {
    return accessToken;
  }

  public TokenType tokenType() {
    return tokenType;
  }

  public ExpiresIn expiresIn() {
    return expiresIn;
  }

  public RefreshToken refreshToken() {
    return refreshToken;
  }
}
