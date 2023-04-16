package org.idp.server.token;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.type.oauth.AccessToken;
import org.idp.server.type.oauth.ExpiresIn;
import org.idp.server.type.oauth.RefreshToken;
import org.idp.server.type.oauth.TokenType;
import org.idp.server.type.oidc.IdToken;

public class TokenResponseBuilder {
  AccessToken accessToken;
  TokenType tokenType;
  ExpiresIn expiresIn;
  RefreshToken refreshToken;
  IdToken idToken;
  Map<String, Object> values = new HashMap<>();

  public TokenResponseBuilder() {}

  public TokenResponseBuilder add(AccessToken accessToken) {
    this.accessToken = accessToken;
    values.put("access_token", accessToken.value());
    return this;
  }

  public TokenResponseBuilder add(TokenType tokenType) {
    this.tokenType = tokenType;
    values.put("token_type", tokenType.name());
    return this;
  }

  public TokenResponseBuilder add(ExpiresIn expiresIn) {
    this.expiresIn = expiresIn;
    values.put("expires_in", expiresIn.value());
    return this;
  }

  public TokenResponseBuilder add(RefreshToken refreshToken) {
    this.refreshToken = refreshToken;
    values.put("refresh_token", refreshToken.value());
    return this;
  }

  public TokenResponseBuilder add(IdToken idToken) {
    this.idToken = idToken;
    values.put("id_token", idToken.value());
    return this;
  }

  public TokenResponse build() {
    return new TokenResponse(accessToken, tokenType, expiresIn, refreshToken, idToken, values);
  }
}
