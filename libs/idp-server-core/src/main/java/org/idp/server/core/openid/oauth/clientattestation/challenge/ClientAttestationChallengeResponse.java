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

package org.idp.server.core.openid.oauth.clientattestation.challenge;

import java.util.Map;

/**
 * Response of the challenge endpoint.
 *
 * <p>Section 6.1 names the member {@code attestation_challenge} and requires the response to be
 * uncacheable; the {@code Cache-Control: no-store} header is added by the adapter.
 */
public record ClientAttestationChallengeResponse(int statusCode, Map<String, Object> contents) {

  public static ClientAttestationChallengeResponse ok(ClientAttestationChallenge challenge) {
    return new ClientAttestationChallengeResponse(
        200, Map.of("attestation_challenge", challenge.challenge()));
  }

  /**
   * Returned when the tenant does not offer server-provided challenges. Section 6.1 makes the
   * endpoint optional, and a tenant that has not configured it must not look as if it had.
   */
  public static ClientAttestationChallengeResponse notFound() {
    return new ClientAttestationChallengeResponse(404, Map.of("error", "not_found"));
  }
}
