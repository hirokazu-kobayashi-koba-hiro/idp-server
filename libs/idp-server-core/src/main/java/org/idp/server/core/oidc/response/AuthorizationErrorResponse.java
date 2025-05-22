/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.oidc.response;

import org.idp.server.basic.http.QueryParams;
import org.idp.server.basic.type.extension.JarmPayload;
import org.idp.server.basic.type.extension.ResponseModeValue;
import org.idp.server.basic.type.oauth.*;
import org.idp.server.basic.type.oauth.Error;

public class AuthorizationErrorResponse {
  RedirectUri redirectUri;
  ResponseModeValue responseModeValue;
  State state;
  TokenIssuer tokenIssuer;
  Error error;
  ErrorDescription errorDescription;
  JarmPayload jarmPayload;
  QueryParams queryParams;

  public AuthorizationErrorResponse() {}

  AuthorizationErrorResponse(
      RedirectUri redirectUri,
      ResponseModeValue responseModeValue,
      State state,
      TokenIssuer tokenIssuer,
      Error error,
      ErrorDescription errorDescription,
      JarmPayload jarmPayload,
      QueryParams queryParams) {
    this.redirectUri = redirectUri;
    this.responseModeValue = responseModeValue;
    this.state = state;
    this.tokenIssuer = tokenIssuer;
    this.error = error;
    this.errorDescription = errorDescription;
    this.jarmPayload = jarmPayload;
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

  public JarmPayload jarmPayload() {
    return jarmPayload;
  }

  QueryParams queryParams() {
    return queryParams;
  }

  public String redirectUriValue() {
    return String.format(
        "%s%s%s", redirectUri.value(), responseModeValue.value(), queryParams.params());
  }
}
