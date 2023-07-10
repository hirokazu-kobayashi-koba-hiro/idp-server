package org.idp.server.token;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.basic.json.JsonConverter;
import org.idp.server.oauth.rar.AuthorizationDetails;
import org.idp.server.type.oauth.*;
import org.idp.server.type.oidc.IdToken;

public class TokenResponseBuilder {
  AccessTokenValue accessTokenValue;
  TokenType tokenType;
  ExpiresIn expiresIn;
  RefreshTokenValue refreshTokenValue = new RefreshTokenValue();
  Scopes scopes = new Scopes();
  IdToken idToken = new IdToken();
  AuthorizationDetails authorizationDetails = new AuthorizationDetails();
  Map<String, Object> values = new HashMap<>();
  JsonConverter jsonConverter = JsonConverter.createWithSnakeCaseStrategy();

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
    if (refreshTokenValue.exists()) {
      values.put("refresh_token", refreshTokenValue.value());
    }
    return this;
  }

  public TokenResponseBuilder add(Scopes scopes) {
    this.scopes = scopes;
    values.put("scope", scopes.toStringValues());
    return this;
  }

  public TokenResponseBuilder add(IdToken idToken) {
    this.idToken = idToken;
    if (idToken.exists()) {
      values.put("id_token", idToken.value());
    }
    return this;
  }

  public TokenResponseBuilder add(AuthorizationDetails authorizationDetails) {
    this.authorizationDetails = authorizationDetails;
    if (authorizationDetails.exists()) {
      values.put("authorization_details", authorizationDetails.toMapValues());
    }
    return this;
  }

  public TokenResponse build() {
    String contents = jsonConverter.write(values);
    return new TokenResponse(
        accessTokenValue,
        tokenType,
        expiresIn,
        refreshTokenValue,
        scopes,
        idToken,
        authorizationDetails,
        contents);
  }
}
