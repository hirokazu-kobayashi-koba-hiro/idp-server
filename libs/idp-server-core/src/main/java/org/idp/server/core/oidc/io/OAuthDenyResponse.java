/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.oidc.io;

import java.util.Map;
import org.idp.server.core.oidc.response.AuthorizationErrorResponse;

public class OAuthDenyResponse {
  OAuthDenyStatus status;
  AuthorizationErrorResponse errorResponse;
  String error;
  String errorDescription;

  public OAuthDenyResponse() {}

  public OAuthDenyResponse(OAuthDenyStatus status, AuthorizationErrorResponse errorResponse) {
    this.status = status;
    this.errorResponse = errorResponse;
  }

  public OAuthDenyResponse(OAuthDenyStatus status, String error, String errorDescription) {
    this.status = status;
    this.error = error;
    this.errorDescription = errorDescription;
  }

  public OAuthDenyStatus status() {
    return status;
  }

  public AuthorizationErrorResponse errorResponse() {
    return errorResponse;
  }

  public String redirectUriValue() {
    return errorResponse.redirectUriValue();
  }

  public String error() {
    return error;
  }

  public String errorDescription() {
    return errorDescription;
  }

  public Map<String, Object> contents() {
    if (status.isError()) {
      return Map.of("error", error, "errorDescription", errorDescription);
    }
    return Map.of("redirect_uri", redirectUriValue());
  }
}
