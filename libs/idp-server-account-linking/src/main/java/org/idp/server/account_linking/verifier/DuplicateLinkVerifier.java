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

import org.idp.server.account_linking.AccountLinkingSession;
import org.idp.server.account_linking.DuplicateLinkPolicy;
import org.idp.server.account_linking.exception.AccountLinkingDuplicateException;
import org.idp.server.account_linking.repository.LinkedExternalAccountQueryRepository;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

/**
 * Applies the tenant's policy for an external account already linked by someone else.
 *
 * <p>The database does not forbid this. The stored external account identifier records whose tokens
 * these are, not an identity — nothing authenticates through it — so a blanket constraint would
 * block a shared corporate account and would let whoever links first keep the owner out.
 */
public class DuplicateLinkVerifier {

  LinkedExternalAccountQueryRepository accountQueryRepository;

  public DuplicateLinkVerifier(LinkedExternalAccountQueryRepository accountQueryRepository) {
    this.accountQueryRepository = accountQueryRepository;
  }

  public void verify(
      Tenant tenant,
      AccountLinkingSession session,
      String federatedUserId,
      DuplicateLinkPolicy policy) {

    if (!policy.isReject()) {
      return;
    }

    boolean linkedByAnother =
        accountQueryRepository.existsForOtherUser(
            tenant, session.userIdentifier(), session.provider(), federatedUserId);

    if (linkedByAnother) {
      // Deliberately vague: naming the current owner would turn this into an enumeration oracle.
      throw new AccountLinkingDuplicateException("This external account cannot be linked.");
    }
  }
}
