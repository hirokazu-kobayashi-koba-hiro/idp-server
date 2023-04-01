package org.idp.server.core.oauth.response;

import org.idp.server.basic.http.QueryParams;
import org.idp.server.core.type.*;
import org.idp.server.core.type.Error;

public class AuthorizationErrorResponseBuilder {
  RedirectUri redirectUri;
  ResponseModeValue responseModeValue;
  State state;
  TokenIssuer tokenIssuer;
  Error error;
  ErrorDescription errorDescription;
  QueryParams queryParams;

  public AuthorizationErrorResponseBuilder(
      RedirectUri redirectUri, ResponseModeValue responseModeValue, TokenIssuer tokenIssuer) {
    this.redirectUri = redirectUri;
    this.responseModeValue = responseModeValue;
    this.tokenIssuer = tokenIssuer;
    this.queryParams = new QueryParams();
    queryParams.add("iss", tokenIssuer.value());
  }

  public AuthorizationErrorResponseBuilder add(State state) {
    this.state = state;
    this.queryParams.add("state", state.value());
    return this;
  }

  public AuthorizationErrorResponseBuilder add(Error error) {
    this.error = error;
    this.queryParams.add("error", error.value());
    return this;
  }

  public AuthorizationErrorResponseBuilder add(ErrorDescription errorDescription) {
    this.errorDescription = errorDescription;
    this.queryParams.add("error_description", errorDescription.value());
    return this;
  }

  public AuthorizationErrorResponse build() {
    return new AuthorizationErrorResponse(
        redirectUri, responseModeValue, state, tokenIssuer, error, errorDescription, queryParams);
  }
}
