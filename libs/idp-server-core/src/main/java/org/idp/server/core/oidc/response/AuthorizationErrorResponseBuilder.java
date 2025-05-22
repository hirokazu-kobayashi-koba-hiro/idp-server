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
import org.idp.server.basic.type.oidc.ResponseMode;

public class AuthorizationErrorResponseBuilder {
  RedirectUri redirectUri;
  ResponseMode responseMode;
  ResponseModeValue responseModeValue;
  State state;
  TokenIssuer tokenIssuer;
  Error error;
  ErrorDescription errorDescription;
  JarmPayload jarmPayload = new JarmPayload();
  QueryParams queryParams;

  public AuthorizationErrorResponseBuilder(
      RedirectUri redirectUri,
      ResponseMode responseMode,
      ResponseModeValue responseModeValue,
      TokenIssuer tokenIssuer) {
    this.redirectUri = redirectUri;
    this.responseMode = responseMode;
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

  public AuthorizationErrorResponseBuilder add(JarmPayload jarmPayload) {
    this.jarmPayload = jarmPayload;
    return this;
  }

  public AuthorizationErrorResponse build() {
    // TODO consider
    if (jarmPayload.exists()) {
      this.queryParams = new QueryParams();
      this.queryParams.add("response", jarmPayload.value());
    }
    return new AuthorizationErrorResponse(
        redirectUri,
        responseModeValue,
        state,
        tokenIssuer,
        error,
        errorDescription,
        jarmPayload,
        queryParams);
  }
}
