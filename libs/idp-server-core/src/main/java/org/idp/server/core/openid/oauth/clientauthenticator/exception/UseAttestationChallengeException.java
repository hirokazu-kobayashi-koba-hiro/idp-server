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

import java.util.Map;
import org.idp.server.core.openid.oauth.type.oauth.RequestedClientId;

/**
 * Represents the {@code use_attestation_challenge} client authentication error.
 *
 * <p>Defined in <a
 * href="https://www.ietf.org/archive/id/draft-ietf-oauth-attestation-based-client-auth-10.html#name-errors">OAuth
 * 2.0 Attestation-Based Client Authentication, Section 7.4</a>: it MUST be used when the Client
 * Attestation PoP JWT is not using an expected server-provided challenge, and when used it MUST be
 * accompanied by the {@code OAuth-Client-Attestation-Challenge} HTTP header field parameter.
 *
 * <p>The exception therefore carries a freshly issued challenge, which every error handler copies
 * into that response header. Section 6.2 defines the same header as the way a server hands the
 * client the challenge to use next, so the failed request doubles as the hand-off.
 */
public class UseAttestationChallengeException extends ClientUnAuthorizedException {

  /** Section 6.2 response header field carrying a fresh Challenge. */
  public static final String CHALLENGE_HEADER_NAME = "OAuth-Client-Attestation-Challenge";

  private String challenge;

  public UseAttestationChallengeException(
      String method, RequestedClientId clientId, String reason, String challenge) {
    super(method, clientId, reason);
    this.challenge = challenge;
  }

  @Override
  public String errorCode() {
    return "use_attestation_challenge";
  }

  @Override
  public Map<String, String> responseHeaders() {
    return Map.of(CHALLENGE_HEADER_NAME, challenge);
  }

  public String challenge() {
    return challenge;
  }
}
