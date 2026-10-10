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
package org.idp.server.core.extension.oid4vci.io;

import java.util.List;
import java.util.Map;

/**
 * Response of the Credential Endpoint (OpenID4VCI 1.0 Section 8.3). {@code Cache-Control: no-store}
 * is added by the adapter, and so is {@code WWW-Authenticate} for a 401.
 */
public record CredentialResponse(int statusCode, Map<String, Object> contents) {

  public static CredentialResponse issued(List<String> credentials) {
    List<Map<String, Object>> entries =
        credentials.stream()
            .map(credential -> Map.<String, Object>of("credential", credential))
            .toList();
    return new CredentialResponse(200, Map.of("credentials", entries));
  }

  public static CredentialResponse requestError(String error, String errorDescription) {
    return new CredentialResponse(
        400, Map.of("error", error, "error_description", errorDescription));
  }

  /** RFC 6750 Section 3.1 {@code invalid_token}. */
  public static CredentialResponse invalidToken(String errorDescription) {
    return new CredentialResponse(
        401, Map.of("error", "invalid_token", "error_description", errorDescription));
  }

  /** RFC 6750 Section 3.1 {@code insufficient_scope}. */
  public static CredentialResponse insufficientScope(String errorDescription) {
    return new CredentialResponse(
        403, Map.of("error", "insufficient_scope", "error_description", errorDescription));
  }

  public static CredentialResponse notFound() {
    return new CredentialResponse(
        404, Map.of("error", "not_found", "error_description", "this tenant issues no credential"));
  }

  public static CredentialResponse serverError() {
    return new CredentialResponse(500, Map.of("error", "server_error"));
  }
}
