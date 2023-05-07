package org.idp.server.token;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.basic.json.JsonParser;
import org.idp.server.type.oauth.AccessTokenValue;
import org.idp.server.type.oauth.ExpiresIn;
import org.idp.server.type.oauth.RefreshTokenValue;
import org.idp.server.type.oauth.TokenType;
import org.idp.server.type.oidc.IdToken;

public class TokenResponseBuilder {
  AccessTokenValue accessTokenValue;
  TokenType tokenType;
  ExpiresIn expiresIn;
  RefreshTokenValue refreshTokenValue = new RefreshTokenValue();
  IdToken idToken = new IdToken();
  Map<String, Object> values = new HashMap<>();
  JsonParser jsonParser = JsonParser.createWithSnakeCaseStrategy();

  public TokenResponseBuilder() {}

  public TokenResponseBuilder add(AccessTokenValue accessTokenValue) {
    this.accessTokenValue = accessTokenValue;
    values.put("access_token", accessTokenValue.value());
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

  public TokenResponseBuilder add(RefreshTokenValue refreshTokenValue) {
    this.refreshTokenValue = refreshTokenValue;
    values.put("refresh_token", refreshTokenValue.value());
    return this;
  }

  public TokenResponseBuilder add(IdToken idToken) {
    this.idToken = idToken;
    values.put("id_token", idToken.value());
    return this;
  }

  public TokenResponse build() {
    String contents = jsonParser.write(values);
    return new TokenResponse(
        accessTokenValue, tokenType, expiresIn, refreshTokenValue, idToken, contents);
  }
}
