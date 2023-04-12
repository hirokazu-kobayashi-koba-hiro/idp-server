package org.idp.server.oauth.response;

import org.idp.server.basic.http.QueryParams;
import org.idp.server.type.extension.ResponseModeValue;
import org.idp.server.type.oauth.AuthorizationCode;
import org.idp.server.type.oauth.RedirectUri;
import org.idp.server.type.oauth.State;
import org.idp.server.type.oauth.TokenIssuer;

public class AuthorizationResponse {
  RedirectUri redirectUri;
  ResponseModeValue responseModeValue;
  AuthorizationCode authorizationCode;
  State state;
  TokenIssuer tokenIssuer;
  QueryParams queryParams;

  AuthorizationResponse(
      RedirectUri redirectUri,
      ResponseModeValue responseModeValue,
      AuthorizationCode authorizationCode,
      State state,
      TokenIssuer tokenIssuer,
      QueryParams queryParams) {
    this.redirectUri = redirectUri;
    this.responseModeValue = responseModeValue;
    this.authorizationCode = authorizationCode;
    this.state = state;
    this.tokenIssuer = tokenIssuer;
    this.queryParams = queryParams;
  }

  public RedirectUri redirectUri() {
    return redirectUri;
  }

  public ResponseModeValue responseModeValue() {
    return responseModeValue;
  }

  public AuthorizationCode authorizationCode() {
    return authorizationCode;
  }

  public State state() {
    return state;
  }

  public TokenIssuer tokenIssuer() {
    return tokenIssuer;
  }

  QueryParams queryParams() {
    return queryParams;
  }

  public String redirectUriValue() {
    return String.format(
        "%s%s%s", redirectUri.value(), responseModeValue.value(), queryParams.params());
  }

  public boolean hasAuthorizationCode() {
    return authorizationCode.exists();
  }
}
