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

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import org.idp.server.platform.date.SystemDateTime;

/**
 * Issues Challenges for Attestation-Based Client Authentication.
 *
 * <p>32 random bytes encoded as base64url without padding. The value carries no structure: it is
 * looked up server-side, so nothing about the request needs to be encoded into it. The tenant is
 * not part of the value either -- the repository scopes both the write and the lookup.
 */
public class ClientAttestationChallengeIssuer {

  static final int CHALLENGE_BYTES = 32;

  SecureRandom secureRandom = new SecureRandom();

  public ClientAttestationChallenge issue(int expiresInSeconds) {
    byte[] random = new byte[CHALLENGE_BYTES];
    secureRandom.nextBytes(random);
    String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(random);

    LocalDateTime now = SystemDateTime.now();

    return new ClientAttestationChallenge(challenge, now.plusSeconds(expiresInSeconds), now);
  }
}
