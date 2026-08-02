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

package org.idp.server.core.openid.clientinstance.registration;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.UUID;
import org.idp.server.core.openid.oauth.type.oauth.RequestedClientId;
import org.idp.server.platform.date.SystemDateTime;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

/**
 * Issues registration challenges.
 *
 * <p>The value is 32 random bytes encoded as base64url without padding. Android Key Attestation
 * embeds the decoded bytes in the certificate, and oversized challenges fail to generate on some
 * devices, so the value stays a plain nonce and all authorization data is kept server-side.
 */
public class ClientInstanceRegistrationChallengeIssuer {

  static final int CHALLENGE_BYTES = 32;

  SecureRandom secureRandom = new SecureRandom();

  public ClientInstanceRegistrationChallenge issue(
      Tenant tenant, RequestedClientId requestedClientId, String deviceId, int expiresInSeconds) {

    byte[] random = new byte[CHALLENGE_BYTES];
    secureRandom.nextBytes(random);
    String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(random);

    LocalDateTime now = SystemDateTime.now();

    return new ClientInstanceRegistrationChallenge(
        challenge,
        tenant.identifierValue(),
        requestedClientId.value(),
        deviceId,
        UUID.randomUUID().toString(),
        now.plusSeconds(expiresInSeconds),
        null,
        now);
  }
}
