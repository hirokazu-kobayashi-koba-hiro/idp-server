/*
 * Copyright 2025 Hirokazu Kobayashi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.idp.server.core.openid.oauth.io;

import java.util.Map;
import java.util.Objects;
import org.idp.server.core.openid.oauth.response.AuthorizationErrorResponse;
import org.idp.server.core.openid.oauth.response.AuthorizationResponse;

/** OAuthAuthorizeResponse */
public class OAuthAuthorizeResponse {
  OAuthAuthorizeStatus status;
  AuthorizationResponse authorizationResponse;
  AuthorizationErrorResponse errorResponse;
  String error;
  String errorDescription;

  public OAuthAuthorizeResponse() {}

  public OAuthAuthorizeResponse(
      OAuthAuthorizeStatus status, AuthorizationResponse authorizationResponse) {
    this.status = status;
    this.authorizationResponse = authorizationResponse;
  }

  public OAuthAuthorizeResponse(
      OAuthAuthorizeStatus status, AuthorizationErrorResponse errorResponse) {
    this.status = status;
    this.errorResponse = errorResponse;
  }

  public OAuthAuthorizeResponse(
      OAuthAuthorizeStatus status, String error, String errorDescription) {
    this.status = status;
    this.error = error;
    this.errorDescription = errorDescription;
  }

  public OAuthAuthorizeStatus status() {
    return status;
  }

  public AuthorizationResponse authorizationResponse() {
    return authorizationResponse;
  }

  public String redirectUriValue() {
    if (status.isOK()) {
      return authorizationResponse.redirectUriValue();
    }
    if (status.isRedirectableBadRequest()) {
      return errorResponse.redirectUriValue();
    }
    throw new IllegalStateException(
        "redirectUriValue is not available for status: " + status.name());
  }

  public String error() {
    return errorResponse.error().value();
  }

  public String errorDescription() {
    return errorResponse.errorDescription().value();
  }

  public Map<String, Object> contents() {
    if (status.isError() && Objects.nonNull(errorResponse)) {
      return Map.of("error", error(), "error_description", errorDescription());
    }
    if (status.isError()) {
      return Map.of("error", error, "error_description", errorDescription);
    }
    return Map.of("redirect_uri", redirectUriValue());
  }

  public boolean isOk() {
    return status.isOK();
  }
}
