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

package org.idp.server.account_linking;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;
import org.idp.server.platform.crypto.SealedData;
import org.junit.jupiter.api.Test;

class LinkedExternalAccountTest {

  static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 29, 12, 0);

  static LinkedExternalAccount read() {
    return new LinkedExternalAccount.Builder().accessTokenExpiresAt(NOW.plusMinutes(5)).build();
  }

  static SealedData sealed(String cipherText) {
    return new SealedData(SealedData.VERSION_1, "salt", "iv", cipherText);
  }

  @Test
  void seesARefreshAsANewAccessToken() {
    LinkedExternalAccount read = read();
    LinkedExternalAccount refreshed =
        read.withRefreshedTokens(
            sealed("access"), sealed("refresh"), "key-2", NOW.plusHours(1), null, NOW);

    assertTrue(refreshed.hasNewAccessTokenSince(read));
  }

  @Test
  void doesNotSeeAMoveToAnotherKeyAsANewAccessToken() {
    LinkedExternalAccount read = read();
    LinkedExternalAccount reencrypted =
        read.withReencryptedTokens(sealed("access"), sealed("refresh"), "key-2", NOW);

    assertFalse(reencrypted.hasNewAccessTokenSince(read));
    assertFalse(read().hasNewAccessTokenSince(read));
  }

  static ParkedCredentials named(String federatedUserId) {
    return new ParkedCredentials.Builder().federatedUserId(federatedUserId).build();
  }

  static LinkedExternalAccount naming(String federatedUserId) {
    return new LinkedExternalAccount.Builder().federatedUserId(federatedUserId).build();
  }

  @Test
  void learnsItsAccountFromTheFirstReLinkThatNamesIt() {
    assertEquals("sub-a", naming(null).relinkedWith(named("sub-a"), NOW).federatedUserId());
  }

  @Test
  void keepsTheAccountItNames() {
    assertEquals("sub-a", naming("sub-a").relinkedWith(named(null), NOW).federatedUserId());
    assertEquals("sub-a", naming("sub-a").relinkedWith(named("sub-a"), NOW).federatedUserId());
  }

  @Test
  void isFirstNamedOnlyWhenItNamesNoAccountAndTheCredentialsDo() {
    assertTrue(naming(null).isFirstNamedBy(named("sub-a")));
    assertFalse(naming("sub-a").isFirstNamedBy(named("sub-a")));
    assertFalse(naming(null).isFirstNamedBy(named(null)));
    assertFalse(naming("sub-a").isFirstNamedBy(named(null)));
  }
}
