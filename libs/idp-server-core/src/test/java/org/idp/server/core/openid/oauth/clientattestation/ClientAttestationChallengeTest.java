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

package org.idp.server.core.openid.oauth.clientattestation;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.Base64;
import org.idp.server.core.openid.oauth.clientattestation.challenge.ClientAttestationChallenge;
import org.idp.server.core.openid.oauth.clientattestation.challenge.ClientAttestationChallengeIssuer;
import org.idp.server.platform.date.SystemDateTime;
import org.junit.jupiter.api.Test;

/**
 * draft-ietf-oauth-attestation-based-client-auth-10 Section 6: Challenges issued by the server.
 *
 * <p>Section 9.7 and Section 11.1 let a challenge bound to a Client Instance session be validated
 * against the single value expected for that session, so a challenge here is reusable for its whole
 * lifetime and there is no consume step.
 */
class ClientAttestationChallengeTest {

  private final ClientAttestationChallengeIssuer issuer = new ClientAttestationChallengeIssuer();

  @Test
  void issuedChallengeIsBase64UrlEncodedRandomOf32Bytes() {
    ClientAttestationChallenge challenge = issuer.issue(300);

    byte[] decoded = Base64.getUrlDecoder().decode(challenge.challenge());
    assertEquals(32, decoded.length);
    assertFalse(challenge.challenge().contains("="), "base64url without padding");
    assertFalse(challenge.challenge().contains("+"));
    assertFalse(challenge.challenge().contains("/"));
  }

  @Test
  void issuedChallengesDoNotRepeat() {
    ClientAttestationChallenge first = issuer.issue(300);
    ClientAttestationChallenge second = issuer.issue(300);

    assertNotEquals(first.challenge(), second.challenge());
  }

  @Test
  void issuedChallengeIsValidForTheRequestedLifetime() {
    ClientAttestationChallenge challenge = issuer.issue(300);

    assertTrue(challenge.isValid());
    assertFalse(challenge.isExpired());
    Duration lifetime = Duration.between(challenge.createdAt(), challenge.expiresAt());
    assertEquals(300, lifetime.toSeconds());
  }

  @Test
  void expiredChallengeIsNotValid() {
    ClientAttestationChallenge challenge =
        new ClientAttestationChallenge(
            "value", SystemDateTime.now().minusSeconds(1), SystemDateTime.now());

    assertTrue(challenge.exists());
    assertTrue(challenge.isExpired());
    assertFalse(challenge.isValid());
  }

  @Test
  void unknownChallengeIsNotValid() {
    // What the repository returns when the value was never issued by this server.
    ClientAttestationChallenge challenge = new ClientAttestationChallenge();

    assertFalse(challenge.exists());
    assertFalse(challenge.isValid());
  }
}
