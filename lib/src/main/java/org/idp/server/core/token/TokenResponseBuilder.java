package org.idp.server.core.token;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.core.type.AccessToken;
import org.idp.server.core.type.ExpiresIn;
import org.idp.server.core.type.RefreshToken;
import org.idp.server.core.type.TokenType;

public class TokenResponseBuilder {
  AccessToken accessToken;
  TokenType tokenType;
  ExpiresIn expiresIn;
  RefreshToken refreshToken;
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

  public TokenResponse build() {
    return new TokenResponse(accessToken, tokenType, expiresIn, refreshToken, values);
  }
}
