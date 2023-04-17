package org.idp.server.token;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.type.oauth.AccessToken;
import org.idp.server.type.oauth.ExpiresIn;
import org.idp.server.type.oauth.RefreshToken;
import org.idp.server.type.oauth.TokenType;
import org.idp.server.type.oidc.IdToken;

public class TokenResponse {
  AccessToken accessToken;
  TokenType tokenType;
  ExpiresIn expiresIn;
  RefreshToken refreshToken = new RefreshToken();
  IdToken idToken = new IdToken();
  Map<String, Object> response = new HashMap<>();

  public TokenResponse() {}

  TokenResponse(
      AccessToken accessToken,
      TokenType tokenType,
      ExpiresIn expiresIn,
      RefreshToken refreshToken,
      IdToken idToken,
      Map<String, Object> response) {
    this.accessToken = accessToken;
    this.tokenType = tokenType;
    this.expiresIn = expiresIn;
    this.refreshToken = refreshToken;
    this.idToken = idToken;
    this.response = response;
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

  public IdToken idToken() {
    return idToken;
  }

  public Map<String, Object> response() {
    return response;
  }

  public boolean hasRefreshToken() {
    return refreshToken.exists();
  }
}
