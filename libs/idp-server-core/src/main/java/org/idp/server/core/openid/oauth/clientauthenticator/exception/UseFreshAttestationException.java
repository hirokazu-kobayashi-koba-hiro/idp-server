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
 * Represents the {@code use_fresh_attestation} client authentication error.
 *
 * <p>Defined in <a
 * href="https://www.ietf.org/archive/id/draft-ietf-oauth-attestation-based-client-auth-10.html#name-errors">OAuth
 * 2.0 Attestation-Based Client Authentication, Section 7.4</a>: it MUST be used when the Client
 * Attestation JWT is deemed to be not fresh enough to be acceptable by the server.
 *
 * <p>Kept distinct from {@link InvalidClientAttestationException} because the Client Instance can
 * recover from it by obtaining a new Client Attestation JWT from its Client Attester, without
 * changing anything else about the request. A Client Attestation JWT whose {@code exp} has passed
 * is the case this server reports; per Section 9.2 a single Client Attestation JWT is deliberately
 * reusable until then.
 */
public class UseFreshAttestationException extends ClientUnAuthorizedException {

  public UseFreshAttestationException(String method, RequestedClientId clientId, String reason) {
    super(method, clientId, reason);
  }

  @Override
  public String errorCode() {
    return "use_fresh_attestation";
  }
}
