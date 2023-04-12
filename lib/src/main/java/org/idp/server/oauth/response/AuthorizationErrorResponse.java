package org.idp.server.oauth.response;

import org.idp.server.basic.http.QueryParams;
import org.idp.server.type.extension.ResponseModeValue;
import org.idp.server.type.oauth.*;
import org.idp.server.type.oauth.Error;

public class AuthorizationErrorResponse {
  RedirectUri redirectUri;
  ResponseModeValue responseModeValue;
  State state;
  TokenIssuer tokenIssuer;
  Error error;
  ErrorDescription errorDescription;
  QueryParams queryParams;

  public AuthorizationErrorResponse() {}

  AuthorizationErrorResponse(
      RedirectUri redirectUri,
      ResponseModeValue responseModeValue,
      State state,
      TokenIssuer tokenIssuer,
      Error error,
      ErrorDescription errorDescription,
      QueryParams queryParams) {
    this.redirectUri = redirectUri;
    this.responseModeValue = responseModeValue;
    this.state = state;
    this.tokenIssuer = tokenIssuer;
    this.error = error;
    this.errorDescription = errorDescription;
    this.queryParams = queryParams;
  }

  public RedirectUri redirectUri() {
    return redirectUri;
  }

  public ResponseModeValue responseModeValue() {
    return responseModeValue;
  }

  public State state() {
    return state;
  }

  public TokenIssuer tokenIssuer() {
    return tokenIssuer;
  }

  public Error error() {
    return error;
  }

  public ErrorDescription errorDescription() {
    return errorDescription;
  }

  QueryParams queryParams() {
    return queryParams;
  }

  public String redirectUriValue() {
    return String.format(
        "%s%s%s", redirectUri.value(), responseModeValue.value(), queryParams.params());
  }
}
