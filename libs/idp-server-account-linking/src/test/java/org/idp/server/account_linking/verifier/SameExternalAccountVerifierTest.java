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

package org.idp.server.account_linking.verifier;

import static org.junit.jupiter.api.Assertions.*;

import org.idp.server.account_linking.LinkedExternalAccount;
import org.idp.server.account_linking.ParkedCredentials;
import org.idp.server.account_linking.exception.AccountLinkingAnotherAccountLinkedException;
import org.junit.jupiter.api.Test;

class SameExternalAccountVerifierTest {

  SameExternalAccountVerifier verifier = new SameExternalAccountVerifier();

  static LinkedExternalAccount current(String federatedUserId) {
    return new LinkedExternalAccount.Builder().federatedUserId(federatedUserId).build();
  }

  static ParkedCredentials credentials(String federatedUserId) {
    return new ParkedCredentials.Builder().federatedUserId(federatedUserId).build();
  }

  @Test
  void acceptsTheSameAccount() {
    assertDoesNotThrow(() -> verifier.verify(current("sub-a"), credentials("sub-a")));
  }

  @Test
  void refusesAnotherAccount() {
    assertThrows(
        AccountLinkingAnotherAccountLinkedException.class,
        () -> verifier.verify(current("sub-a"), credentials("sub-b")));
  }

  @Test
  void acceptsWhenEitherSideNamesNoAccount() {
    assertDoesNotThrow(() -> verifier.verify(current(null), credentials("sub-a")));
    assertDoesNotThrow(() -> verifier.verify(current("sub-a"), credentials(null)));
    assertDoesNotThrow(() -> verifier.verify(current(null), credentials(null)));
  }
}
