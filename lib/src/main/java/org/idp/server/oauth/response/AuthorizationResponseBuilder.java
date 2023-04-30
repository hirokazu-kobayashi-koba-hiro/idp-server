package org.idp.server.oauth.response;

import org.idp.server.basic.http.QueryParams;
import org.idp.server.oauth.token.AccessToken;
import org.idp.server.type.extension.ResponseModeValue;
import org.idp.server.type.oauth.*;
import org.idp.server.type.oidc.IdToken;

public class AuthorizationResponseBuilder {
  RedirectUri redirectUri;
  ResponseModeValue responseModeValue;
  AuthorizationCode authorizationCode = new AuthorizationCode();
  State state = new State();
  AccessToken accessToken = new AccessToken();
  TokenType tokenType = TokenType.undefined;
  ExpiresIn expiresIn = new ExpiresIn();
  IdToken idToken = new IdToken();
  TokenIssuer tokenIssuer;
  QueryParams queryParams;

  public AuthorizationResponseBuilder(
      RedirectUri redirectUri, ResponseModeValue responseModeValue, TokenIssuer tokenIssuer) {
    this.redirectUri = redirectUri;
    this.responseModeValue = responseModeValue;
    this.tokenIssuer = tokenIssuer;
    this.queryParams = new QueryParams();
    queryParams.add("iss", tokenIssuer.value());
  }

  public AuthorizationResponseBuilder add(AuthorizationCode authorizationCode) {
    this.authorizationCode = authorizationCode;
    this.queryParams.add("code", authorizationCode.value());
    return this;
  }

  public AuthorizationResponseBuilder add(State state) {
    this.state = state;
    this.queryParams.add("state", state.value());
    return this;
  }

  public AuthorizationResponseBuilder add(AccessToken accessToken) {
    this.accessToken = accessToken;
    this.queryParams.add("access_token", accessToken.accessTokenValue().value());
    return this;
  }

  public AuthorizationResponseBuilder add(ExpiresIn expiresIn) {
    this.expiresIn = expiresIn;
    this.queryParams.add("expires_in", expiresIn.toStringValue());
    return this;
  }

  public AuthorizationResponseBuilder add(TokenType tokenType) {
    if (tokenType.isDefined()) {
      this.tokenType = tokenType;
      this.queryParams.add("token_type", tokenType.name());
    }
    return this;
  }

  public AuthorizationResponseBuilder add(IdToken idToken) {
    this.idToken = idToken;
    this.queryParams.add("id_token", idToken.value());
    return this;
  }

  public AuthorizationResponse build() {
    return new AuthorizationResponse(
        redirectUri,
        responseModeValue,
        authorizationCode,
        state,
        accessToken,
        tokenType,
        expiresIn,
        idToken,
        tokenIssuer,
        queryParams);
  }
}
