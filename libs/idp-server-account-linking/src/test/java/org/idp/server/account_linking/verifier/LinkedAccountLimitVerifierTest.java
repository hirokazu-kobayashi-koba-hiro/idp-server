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

import java.util.List;
import org.idp.server.account_linking.AccountAlias;
import org.idp.server.account_linking.ExternalIdpProvider;
import org.idp.server.account_linking.LinkedExternalAccount;
import org.idp.server.account_linking.ProviderLinks;
import org.idp.server.account_linking.exception.AccountLinkingAccountLimitReachedException;
import org.junit.jupiter.api.Test;

class LinkedAccountLimitVerifierTest {

  static final ExternalIdpProvider PROVIDER = new ExternalIdpProvider("google");

  LinkedAccountLimitVerifier verifier = new LinkedAccountLimitVerifier();

  static ProviderLinks holding(int count) {
    List<LinkedExternalAccount> links =
        java.util.stream.IntStream.range(0, count)
            .mapToObj(
                i ->
                    new LinkedExternalAccount.Builder()
                        .provider(PROVIDER)
                        .accountAlias(new AccountAlias("google-" + i))
                        .build())
            .toList();
    return ProviderLinks.of(links, PROVIDER);
  }

  @Test
  void addsALinkBelowTheLimit() {
    assertDoesNotThrow(() -> verifier.verify(holding(0), 1));
    assertDoesNotThrow(() -> verifier.verify(holding(2), 3));
  }

  @Test
  void refusesALinkAtTheLimit() {
    assertThrows(
        AccountLinkingAccountLimitReachedException.class, () -> verifier.verify(holding(3), 3));
  }

  @Test
  void refusesALinkAboveALoweredLimit() {
    assertThrows(
        AccountLinkingAccountLimitReachedException.class, () -> verifier.verify(holding(5), 3));
  }
}
