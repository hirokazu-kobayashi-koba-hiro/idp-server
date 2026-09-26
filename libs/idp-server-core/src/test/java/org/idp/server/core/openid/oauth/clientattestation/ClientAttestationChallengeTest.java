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
import org.idp.server.core.openid.oauth.clientattestation.challenge.ClientAttestationChallenge;
import org.idp.server.core.openid.oauth.clientattestation.challenge.ClientAttestationChallenges;
import org.idp.server.platform.crypto.ServerNonceCodec;
import org.idp.server.platform.date.SystemDateTime;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * draft-ietf-oauth-attestation-based-client-auth-11 Section 6: Challenges issued by the server and
 * recognized without being stored. Reusable for their whole lifetime (Section 12.1), so there is no
 * consume step.
 */
class ClientAttestationChallengeTest {

  static final String SECRET = "server-secret-for-tests";

  private final ClientAttestationChallenges challenges =
      new ClientAttestationChallenges(new ServerNonceCodec(SECRET));

  private final Tenant tenant = tenant("67e7eae6-62b0-4500-9eff-87459f63fc66");
  private final Tenant otherTenant = tenant("b01c0787-b7be-4699-9a5a-042f70d41697");

  private static Tenant tenant(String id) {
    return new Tenant(
        new TenantIdentifier(id),
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        true);
  }

  @Test
  @DisplayName("発行した Challenge は、同じテナントで何度でも認識でき、期限は発行時のもの")
  void issuedChallengeIsRecognizedForItsLifetime() {
    ClientAttestationChallenge issued = challenges.issue(tenant, 300);

    assertTrue(issued.isValid());
    assertEquals(300, Duration.between(issued.createdAt(), issued.expiresAt()).toSeconds());

    ClientAttestationChallenge found = challenges.find(tenant, issued.value());
    assertTrue(found.isValid());
    assertEquals(
        SystemDateTime.toEpochSecond(issued.expiresAt()),
        SystemDateTime.toEpochSecond(found.expiresAt()));
    assertTrue(challenges.find(tenant, issued.value()).isValid(), "reusable, not consumed");
  }

  @Test
  @DisplayName("期限切れの Challenge は有効でない")
  void expiredChallengeIsNotValid() {
    ClientAttestationChallenge issued = challenges.issue(tenant, -1);

    ClientAttestationChallenge found = challenges.find(tenant, issued.value());
    assertTrue(found.exists());
    assertFalse(found.isValid());
  }

  @Test
  @DisplayName("別テナント・別の鍵・このサーバーが出していない値は認識しない")
  void foreignValuesAreNotRecognized() {
    ClientAttestationChallenge issued = challenges.issue(tenant, 300);

    assertFalse(challenges.find(otherTenant, issued.value()).exists());
    assertFalse(
        new ClientAttestationChallenges(new ServerNonceCodec("another-secret"))
            .find(tenant, issued.value())
            .exists());
    assertFalse(challenges.find(tenant, "never-issued").exists());
  }

  @Test
  @DisplayName("Challenge は毎回違う")
  void issuedChallengesDoNotRepeat() {
    assertNotEquals(challenges.issue(tenant, 300).value(), challenges.issue(tenant, 300).value());
  }
}
