package org.idp.server.oauth.response;

import org.idp.server.basic.http.QueryParams;
import org.idp.server.type.extension.ResponseModeValue;
import org.idp.server.type.oauth.*;
import org.idp.server.type.oauth.Error;

public class AuthorizationResponseBuilder {
  RedirectUri redirectUri;
  ResponseModeValue responseModeValue;
  AuthorizationCode authorizationCode;
  State state;
  TokenIssuer tokenIssuer;
  Error error;
  ErrorDescription errorDescription;
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

  public AuthorizationResponse build() {
    return new AuthorizationResponse(
        redirectUri, responseModeValue, authorizationCode, state, tokenIssuer, queryParams);
  }
}
