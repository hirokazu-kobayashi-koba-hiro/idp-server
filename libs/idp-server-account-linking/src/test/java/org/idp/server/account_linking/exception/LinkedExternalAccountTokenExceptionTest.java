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
package org.idp.server.account_linking.exception;

import static org.junit.jupiter.api.Assertions.*;

import org.idp.server.account_linking.io.AccountLinkingStatus;
import org.junit.jupiter.api.Test;

class LinkedExternalAccountTokenExceptionTest {

  @Test
  void asksToLinkAgainWhenTheProviderRejectsTheRefreshToken() {
    LinkedExternalAccountTokenException exception =
        LinkedExternalAccountTokenException.refreshFailed(400);

    assertEquals("relink_required", exception.error());
    assertEquals(AccountLinkingStatus.BAD_REQUEST, exception.status());
  }

  @Test
  void asksToTryLaterWhenTheProviderFails() {
    LinkedExternalAccountTokenException exception =
        LinkedExternalAccountTokenException.refreshFailed(503);

    assertEquals("temporarily_unavailable", exception.error());
    assertEquals(AccountLinkingStatus.BAD_GATEWAY, exception.status());
  }

  @Test
  void asksToTryLaterWhenTheProviderIsRateLimiting() {
    // A busy provider has not rejected the grant: linking again would not help.
    LinkedExternalAccountTokenException exception =
        LinkedExternalAccountTokenException.refreshFailed(429);

    assertEquals("temporarily_unavailable", exception.error());
    assertEquals(AccountLinkingStatus.BAD_GATEWAY, exception.status());
  }

  @Test
  void tellsTheProviderIsUnavailableRatherThanToLinkAgain() {
    // Linking again would fail too while the provider is removed or disabled.
    LinkedExternalAccountTokenException exception =
        LinkedExternalAccountTokenException.providerUnavailable("gone");

    assertEquals("provider_unavailable", exception.error());
    assertEquals(AccountLinkingStatus.BAD_REQUEST, exception.status());
    assertFalse(exception.isRelinkRequired());
  }
}
