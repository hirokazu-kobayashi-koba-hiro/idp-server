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

import java.util.List;
import java.util.UUID;
import org.idp.server.account_linking.exception.AccountLinkingSeveralAccountsLinkedException;
import org.junit.jupiter.api.Test;

class ProviderLinksTest {

  static final ExternalIdpProvider PROVIDER = new ExternalIdpProvider("google");

  static LinkedExternalAccount link(String alias, String federatedUserId) {
    return link(PROVIDER, alias, federatedUserId);
  }

  static LinkedExternalAccount link(
      ExternalIdpProvider provider, String alias, String federatedUserId) {
    return new LinkedExternalAccount.Builder()
        .identifier(new LinkedExternalAccountIdentifier(UUID.randomUUID().toString()))
        .provider(provider)
        .accountAlias(new AccountAlias(alias))
        .federatedUserId(federatedUserId)
        .build();
  }

  static ParkedCredentials credentials(String federatedUserId) {
    return new ParkedCredentials.Builder().federatedUserId(federatedUserId).build();
  }

  static String renewed(List<LinkedExternalAccount> accounts, String federatedUserId) {
    LinkedExternalAccount renewed =
        ProviderLinks.of(accounts, PROVIDER).theOneToRenew(credentials(federatedUserId));
    return renewed.exists() ? renewed.accountAlias().value() : null;
  }

  @Test
  void renewsNothingWithoutALinkToTheProvider() {
    assertNull(renewed(List.of(), "sub-a"));
    assertNull(
        renewed(List.of(link(new ExternalIdpProvider("github"), "github-1", "sub-a")), "sub-a"));
  }

  @Test
  void renewsTheOnlyLink() {
    assertEquals("google-1", renewed(List.of(link("google-1", "sub-a")), "sub-a"));
    assertEquals("google-1", renewed(List.of(link("google-1", "sub-a")), "sub-b"));
    assertEquals("google-1", renewed(List.of(link("google-1", null)), "sub-a"));
    assertEquals("google-1", renewed(List.of(link("google-1", "sub-a")), null));
  }

  @Test
  void renewsTheLinkOfTheSameAccountAmongSeveral() {
    List<LinkedExternalAccount> several =
        List.of(link("google-1", "sub-a"), link("google-2", "sub-b"));

    assertEquals("google-2", renewed(several, "sub-b"));
  }

  @Test
  void cannotTellWhichOfSeveralToRenewWithoutTheSameAccount() {
    List<LinkedExternalAccount> several =
        List.of(link("google-1", "sub-a"), link("google-2", null));

    assertThrows(
        AccountLinkingSeveralAccountsLinkedException.class, () -> renewed(several, "sub-c"));
    assertThrows(AccountLinkingSeveralAccountsLinkedException.class, () -> renewed(several, null));
  }
}
