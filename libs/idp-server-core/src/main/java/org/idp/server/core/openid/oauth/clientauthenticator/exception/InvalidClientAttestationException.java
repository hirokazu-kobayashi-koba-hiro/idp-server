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

package org.idp.server.core.openid.oauth.clientauthenticator.exception;

import org.idp.server.core.openid.oauth.type.oauth.RequestedClientId;

/**
 * Represents the {@code invalid_client_attestation} client authentication error.
 *
 * <p>Defined in <a
 * href="https://www.ietf.org/archive/id/draft-ietf-oauth-attestation-based-client-auth-10.html#name-errors">OAuth
 * 2.0 Attestation-Based Client Authentication, Section 7.4</a>: it MAY be used in addition to the
 * more general {@code invalid_client} when the attestation or its proof of possession could not be
 * successfully verified. Reporting it tells the Client Instance that the failure is about the two
 * attestation JWTs rather than about the rest of the request.
 *
 * <p>Modeled as a subclass of {@link ClientUnAuthorizedException} so it keeps the same 401
 * Unauthorized mapping while every error handler resolves the code through {@link #errorCode()}.
 */
public class InvalidClientAttestationException extends ClientUnAuthorizedException {

  public InvalidClientAttestationException(
      String method, RequestedClientId clientId, String reason) {
    super(method, clientId, reason);
  }

  public InvalidClientAttestationException(
      String method, RequestedClientId clientId, String reason, Throwable throwable) {
    super(method, clientId, reason, throwable);
  }

  @Override
  public String errorCode() {
    return "invalid_client_attestation";
  }
}
