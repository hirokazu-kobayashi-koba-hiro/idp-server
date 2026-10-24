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
import java.util.Map;
import org.idp.server.account_linking.repository.AccountLinkingSessionCommandRepository;
import org.idp.server.account_linking.repository.AccountLinkingSessionQueryRepository;
import org.idp.server.account_linking.repository.LinkedExternalAccountCommandRepository;
import org.idp.server.account_linking.repository.LinkedExternalAccountQueryRepository;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantDeletionStep;

/**
 * Revokes every external token a tenant holds, before the tenant is deleted.
 *
 * <p>Deleting the tenant removes its links and linking sessions through the foreign key cascade,
 * which revokes nothing: the tokens would stay valid at the external IdP with no row left to revoke
 * them from. This does for the whole tenant what deleting a user does for that user.
 *
 * <p>Each link is read under its row lock and removed once its tokens are revoked, a page at a
 * time, so the next page is always the rows still left. Revocation is best effort, as on unlink.
 * Every token takes a call to the external IdP, so the time grows with the number of links.
 */
public class TenantLinkedAccountCleanup implements TenantDeletionStep {

  static final int PAGE_SIZE = 500;

  LinkedExternalAccountQueryRepository accountQueryRepository;
  LinkedExternalAccountCommandRepository accountCommandRepository;
  AccountLinkingSessionQueryRepository sessionQueryRepository;
  AccountLinkingSessionCommandRepository sessionCommandRepository;
  LinkedExternalAccountRevoker revoker;
  LoggerWrapper log = LoggerWrapper.getLogger(TenantLinkedAccountCleanup.class);

  public TenantLinkedAccountCleanup(
      LinkedExternalAccountQueryRepository accountQueryRepository,
      LinkedExternalAccountCommandRepository accountCommandRepository,
      AccountLinkingSessionQueryRepository sessionQueryRepository,
      AccountLinkingSessionCommandRepository sessionCommandRepository,
      LinkedExternalAccountRevoker revoker) {
    this.accountQueryRepository = accountQueryRepository;
    this.accountCommandRepository = accountCommandRepository;
    this.sessionQueryRepository = sessionQueryRepository;
    this.sessionCommandRepository = sessionCommandRepository;
    this.revoker = revoker;
  }

  @Override
  public String name() {
    return "linked-external-account-revocation";
  }

  @Override
  public void beforeDeletion(Tenant tenant) {
    cleanup(tenant);
  }

  /** Revokes and removes the tenant's links and parked linking sessions. */
  public TenantLinkedAccountCleanupResult cleanup(Tenant tenant) {
    LinkedExternalAccountQueries firstPage =
        new LinkedExternalAccountQueries(Map.of("limit", String.valueOf(PAGE_SIZE)));
    int accounts = 0;
    int revokedAccounts = 0;
    List<LinkedExternalAccount> page;
    while (!(page = accountQueryRepository.findList(tenant, firstPage)).isEmpty()) {
      int removedInPage = 0;
      for (LinkedExternalAccount listed : page) {
        LinkedExternalAccount account = accountQueryRepository.lock(tenant, listed.identifier());
        if (!account.exists()) {
          continue;
        }
        if (revoker.revoke(tenant, account)) {
          revokedAccounts++;
        }
        accountCommandRepository.delete(tenant, account);
        removedInPage++;
      }
      accounts += removedInPage;
      // The page is read again from the start; one that removed nothing would come back unchanged.
      if (removedInPage == 0) {
        break;
      }
    }

    int sessions = 0;
    int revokedSessions = 0;
    List<AccountLinkingSession> parked;
    while (!(parked = sessionQueryRepository.findParkedList(tenant, PAGE_SIZE)).isEmpty()) {
      for (AccountLinkingSession session : parked) {
        if (revoker.revoke(tenant, session)) {
          revokedSessions++;
        }
        sessionCommandRepository.delete(tenant, session.state());
        sessions++;
      }
    }

    if (accounts > 0 || sessions > 0) {
      log.info(
          "Linked external accounts removed before tenant deletion: tenant={}, count={},"
              + " revoked={}, parked_sessions={}, parked_sessions_revoked={}",
          tenant.identifierValue(),
          accounts,
          revokedAccounts,
          sessions,
          revokedSessions);
    }
    return new TenantLinkedAccountCleanupResult(
        accounts, revokedAccounts, sessions, revokedSessions);
  }
}
