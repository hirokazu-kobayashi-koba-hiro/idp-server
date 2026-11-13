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

import java.util.Objects;
import org.idp.server.account_linking.LinkedExternalAccount;
import org.idp.server.account_linking.ParkedCredentials;
import org.idp.server.account_linking.exception.AccountLinkingAnotherAccountLinkedException;

/**
 * Linking again to a provider that allows one account replaces the link only with the same external
 * account.
 *
 * <p>Switching to another account is a step the user takes on purpose: unlink, then link. Doing it
 * on a second press of the link button would replace the account without the user noticing.
 *
 * <p>Only identifiers on both sides are compared. A provider that names no account (a plain OAuth
 * 2.0 delegation) gives nothing to compare, and linking again is taken as renewing the one link.
 */
public class SameExternalAccountVerifier {

  public void verify(LinkedExternalAccount current, ParkedCredentials credentials) {
    if (current.federatedUserId() == null || !credentials.hasFederatedUserId()) {
      return;
    }
    if (!Objects.equals(current.federatedUserId(), credentials.federatedUserId())) {
      throw new AccountLinkingAnotherAccountLinkedException(
          "Another account of this provider is already linked. Unlink it before linking another.");
    }
  }
}
