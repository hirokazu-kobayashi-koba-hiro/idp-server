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
package org.idp.server.account_linking.handler;

import java.util.Map;
import org.idp.server.account_linking.AccountAlias;
import org.idp.server.account_linking.LinkedExternalAccount;
import org.idp.server.account_linking.LinkedExternalAccountRevoker;
import org.idp.server.account_linking.io.AccountLinkingResult;
import org.idp.server.account_linking.io.AccountLinkingStatus;
import org.idp.server.account_linking.repository.LinkedExternalAccountCommandRepository;
import org.idp.server.account_linking.repository.LinkedExternalAccountQueryRepository;
import org.idp.server.core.openid.identity.User;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.security.event.DefaultSecurityEventType;

/**
 * Removes one of the caller's linked external accounts.
 *
 * <p>The account is looked up under the caller, so another user's alias is simply not found. The
 * stored tokens are revoked at the external IdP before the row goes, on a best-effort basis.
 *
 * <p>The row is read under a row lock. Token retrieval refreshes under the same lock, and the
 * provider may rotate both tokens when it does; reading without the lock would revoke the tokens
 * that refresh is about to replace, and leave the new ones live at the provider with nothing here
 * to revoke them.
 *
 * <p>A missing alias records no event. Probing for one needs the user's own token, so a run of
 * misses says nothing about anyone else's links.
 */
public class AccountLinkingUnlinkHandler {

  LinkedExternalAccountQueryRepository accountQueryRepository;
  LinkedExternalAccountCommandRepository accountCommandRepository;
  LinkedExternalAccountRevoker revoker;

  public AccountLinkingUnlinkHandler(
      LinkedExternalAccountQueryRepository accountQueryRepository,
      LinkedExternalAccountCommandRepository accountCommandRepository,
      LinkedExternalAccountRevoker revoker) {
    this.accountQueryRepository = accountQueryRepository;
    this.accountCommandRepository = accountCommandRepository;
    this.revoker = revoker;
  }

  public AccountLinkingResult handle(Tenant tenant, User user, AccountAlias alias) {
    LinkedExternalAccount account =
        accountQueryRepository.lock(tenant, user.userIdentifier(), alias);
    if (!account.exists()) {
      return AccountLinkingResult.error(
          AccountLinkingStatus.NOT_FOUND, "not_found", "Linked external account not found.", null);
    }

    revoker.revoke(tenant, account);
    accountCommandRepository.delete(tenant, account);

    return AccountLinkingResult.success(
        AccountLinkingStatus.NO_CONTENT,
        Map.of(),
        DefaultSecurityEventType.external_account_unlinked,
        user);
  }
}
