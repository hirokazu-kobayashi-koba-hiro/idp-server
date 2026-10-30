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

import java.util.List;
import org.idp.server.account_linking.exception.AccountLinkingSeveralAccountsLinkedException;

/**
 * A user's links to one provider.
 *
 * <p>A provider that allows one account holds at most one link, but more are possible for links
 * made while it still allowed several.
 */
public class ProviderLinks {

  List<LinkedExternalAccount> links;

  public ProviderLinks(List<LinkedExternalAccount> links) {
    this.links = links;
  }

  /** The links among {@code accounts} that are {@code provider}'s. */
  public static ProviderLinks of(
      List<LinkedExternalAccount> accounts, ExternalIdpProvider provider) {
    return new ProviderLinks(
        accounts.stream().filter(account -> account.provider().equals(provider)).toList());
  }

  /**
   * The one link that linking with {@code credentials} renews; none when the user holds none.
   *
   * <p>The link of the same external account is renewed. Failing that, the only link is. Among
   * several, which to renew cannot be told, so the user is asked to unlink first.
   *
   * @throws AccountLinkingSeveralAccountsLinkedException when several are linked and none is the
   *     same external account
   */
  public LinkedExternalAccount theOneToRenew(ParkedCredentials credentials) {
    if (links.isEmpty()) {
      return new LinkedExternalAccount();
    }
    if (credentials.hasFederatedUserId()) {
      for (LinkedExternalAccount link : links) {
        if (credentials.federatedUserId().equals(link.federatedUserId())) {
          return link;
        }
      }
    }
    if (links.size() == 1) {
      return links.getFirst();
    }
    throw new AccountLinkingSeveralAccountsLinkedException(
        "Several accounts of this provider are linked. Unlink them before linking again.");
  }
}
