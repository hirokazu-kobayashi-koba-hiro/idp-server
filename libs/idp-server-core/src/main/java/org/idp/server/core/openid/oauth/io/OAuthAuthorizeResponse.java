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

import java.util.HashMap;
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

  /**
   * One-time value telling {@code /complete} that the caller is the browser which authenticated.
   *
   * <p>Only ever read from this response, which only that browser receives.
   */
  String authProof;

  public OAuthAuthorizeResponse withAuthProof(String authProof) {
    this.authProof = authProof;
    return this;
  }

  public Map<String, Object> contents() {
    if (status.isError() && Objects.nonNull(errorResponse)) {
      return Map.of("error", error(), "error_description", errorDescription());
    }
    if (status.isError()) {
      return Map.of("error", error, "error_description", errorDescription);
    }
    Map<String, Object> contents = new HashMap<>();
    if (authProof != null && !authProof.isEmpty()) {
      // The redirect carries the authorization code, so where a hand-off is in use it is withheld
      // here and travels inside the hand-off instead. Anything able to read this body could
      // otherwise take the code without ever reaching /complete.
      contents.put("auth_proof", authProof);
    } else {
      contents.put("redirect_uri", redirectUriValue());
    }
    return contents;
  }

  public boolean isOk() {
    return status.isOK();
  }
}
